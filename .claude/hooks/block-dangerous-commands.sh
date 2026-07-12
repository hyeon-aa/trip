#!/usr/bin/env bash
# PreToolUse hook (matcher: Bash). Blocks destructive commands before they run.
# Reads tool_input JSON from stdin; exits 2 + stderr message to block, 0 to allow.

input=$(cat)
raw_command=$(printf '%s' "$input" | jq -r '.tool_input.command // empty')

if [ -z "$raw_command" ]; then
  exit 0
fi

block() {
  echo "Blocked by block-dangerous-commands.sh: $1" >&2
  exit 2
}

# Strip heredoc bodies (e.g. `git commit -m "$(cat <<'EOF' ... EOF)"`) before
# pattern-matching — commit messages / PR bodies routinely quote things like
# "git push --force" as documentation, not as commands to run.
strip_heredocs() {
  local in_heredoc=0 delim=""
  while IFS= read -r line || [ -n "$line" ]; do
    if [ "$in_heredoc" = "1" ]; then
      [ "$line" = "$delim" ] && in_heredoc=0
      continue
    fi
    if [[ "$line" =~ \<\<-?[[:space:]]*[\'\"]?([A-Za-z_][A-Za-z0-9_]*)[\'\"]? ]]; then
      delim="${BASH_REMATCH[1]}"
      in_heredoc=1
    fi
    printf '%s\n' "$line"
  done <<< "$1"
}

# has_flag SEGMENT SHORT_LETTER LONG_NAME
# True if SEGMENT contains a short-option cluster with SHORT_LETTER (e.g. -rf
# contains r and f) or a long option exactly matching LONG_NAME (or
# LONG_NAME=value). Long options are matched as whole tokens, not substrings,
# so "--force" never counts toward an unrelated "-r" check just because the
# word "force" happens to contain the letter r.
has_flag() {
  local seg="$1" short="$2" long="$3" word rest
  local -a words
  read -ra words <<< "$seg"
  for word in "${words[@]}"; do
    case "$word" in
      --*)
        if [ -n "$long" ] && { [ "$word" = "$long" ] || [ "${word%%=*}" = "$long" ]; }; then
          return 0
        fi
        ;;
      -*)
        rest="${word#-}"
        case "$rest" in
          *"$short"*) return 0 ;;
        esac
        ;;
    esac
  done
  return 1
}

# Join backslash-newline line continuations into a space so a flag split
# across two physical lines (`rm -r \` + newline + `-f path`) is still seen
# as one segment below. (Pure bash, not sed — BSD sed on macOS doesn't
# support the GNU ":a;N;$!ba" slurp idiom used for this.)
join_continuations() {
  local buf="" line
  while IFS= read -r line || [ -n "$line" ]; do
    if [[ "$line" == *\\ ]]; then
      buf+="${line%\\} "
    else
      printf '%s\n' "${buf}${line}"
      buf=""
    fi
  done <<< "$1"
  [ -n "$buf" ] && printf '%s\n' "$buf"
  return 0
}

after_heredocs=$(strip_heredocs "$raw_command")
joined=$(join_continuations "$after_heredocs")

# Split into segments on statement separators (;, &&, ||, |, newline) so every
# check below only looks within a single sub-command — not the whole
# multi-command string. This is what keeps `git push origin main && curl -f
# ...` from being misread as a force-push just because "-f" appears somewhere
# later in an unrelated command.
segments=$(printf '%s' "$joined" | tr ';|&' '\n\n\n')

while IFS= read -r segment || [ -n "$segment" ]; do
  [ -z "$segment" ] && continue

  # git push --force / -f (allow --force-with-lease)
  if printf '%s' "$segment" | grep -qE '\bgit\b.*\bpush\b'; then
    if has_flag "$segment" f --force \
      && ! printf '%s' "$segment" | grep -qE -- '--force-with-lease'; then
      block "git push --force/-f can overwrite remote history. Use --force-with-lease or ask the user first."
    fi
  fi

  # git reset --hard
  if printf '%s' "$segment" | grep -qE '\bgit\b.*\breset\b.*--hard\b'; then
    block "git reset --hard discards uncommitted work. Confirm with the user first."
  fi

  # git clean -f -d (any order, combined or separate flags; -d has no long form)
  if printf '%s' "$segment" | grep -qE '\bgit\b.*\bclean\b'; then
    if has_flag "$segment" f --force && has_flag "$segment" d ""; then
      block "git clean -fd permanently deletes untracked files. Confirm with the user first."
    fi
  fi

  # git branch -D on main/master
  if printf '%s' "$segment" | grep -qE '\bgit\b.*\bbranch\b.*-D\b.*\b(main|master)\b'; then
    block "Force-deleting main/master is almost certainly a mistake."
  fi

  # rm -rf (combined or separate flags, short or long form)
  if printf '%s' "$segment" | grep -qE '\brm\b'; then
    if has_flag "$segment" r --recursive && has_flag "$segment" f --force; then
      block "rm -rf is destructive and hard to reverse. Confirm the exact path with the user first."
    fi
  fi

  # DROP TABLE / TRUNCATE via psql (raw SQL passed with -c), scoped to the
  # same segment as the psql invocation itself.
  if printf '%s' "$segment" | grep -qE '\bpsql\b' \
    && printf '%s' "$segment" | grep -qiE '\b(DROP[[:space:]]+TABLE|TRUNCATE)\b'; then
    block "Raw DROP TABLE/TRUNCATE via psql is destructive. Confirm with the user first."
  fi
done <<< "$segments"

exit 0
