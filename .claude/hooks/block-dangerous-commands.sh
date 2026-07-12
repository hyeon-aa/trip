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

command=$(strip_heredocs "$raw_command")

# git push --force / -f (allow --force-with-lease)
if printf '%s' "$command" | grep -qE '\bgit\b[^|;&]*\bpush\b' \
  && printf '%s' "$command" | grep -qE '(--force\b|-f\b)' \
  && ! printf '%s' "$command" | grep -qE -- '--force-with-lease'; then
  block "git push --force/-f can overwrite remote history. Use --force-with-lease or ask the user first."
fi

# git reset --hard
if printf '%s' "$command" | grep -qE '\bgit\b[^|;&]*\breset\b[^|;&]*--hard\b'; then
  block "git reset --hard discards uncommitted work. Confirm with the user first."
fi

# git clean -fd (any order/combination of f and d flags)
if printf '%s' "$command" | grep -qE '\bgit\b[^|;&]*\bclean\b[^|;&]*-[a-zA-Z]*f[a-zA-Z]*d|\bgit\b[^|;&]*\bclean\b[^|;&]*-[a-zA-Z]*d[a-zA-Z]*f'; then
  block "git clean -fd permanently deletes untracked files. Confirm with the user first."
fi

# git branch -D on main/master
if printf '%s' "$command" | grep -qE '\bgit\b[^|;&]*\bbranch\b[^|;&]*-D\b[^|;&]*\b(main|master)\b'; then
  block "Force-deleting main/master is almost certainly a mistake."
fi

# rm -rf (any order of flags, e.g. -rf, -fr, -r -f, --recursive --force)
if printf '%s' "$command" | grep -qE '\brm\b[^|;&]*(-[a-zA-Z]*r[a-zA-Z]*f[a-zA-Z]*|-[a-zA-Z]*f[a-zA-Z]*r[a-zA-Z]*|--recursive[^|;&]*--force|--force[^|;&]*--recursive)'; then
  block "rm -rf is destructive and hard to reverse. Confirm the exact path with the user first."
fi

# DROP TABLE / TRUNCATE via psql (raw SQL passed with -c, or heredoc/pipe into psql)
if printf '%s' "$command" | grep -qE '\bpsql\b' \
  && printf '%s' "$command" | grep -qiE '\b(DROP[[:space:]]+TABLE|TRUNCATE)\b'; then
  block "Raw DROP TABLE/TRUNCATE via psql is destructive. Confirm with the user first."
fi

exit 0
