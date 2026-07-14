# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project overview

Jeju trip-planning app: an AI chat assistant recommends places and builds a day-by-day
itinerary, shown on a Kakao Map. Monorepo with two independently run apps:

- `client/` — Next.js 16 (App Router) + React 19 + TypeScript + Tailwind v4, Kakao Maps SDK
- `server/` — Spring Boot 4 (Java 17, Gradle), PostgreSQL + pgvector, Redis

See `client/CLAUDE.md` (→ `client/AGENTS.md`) for a client-specific note: this repo's
Next.js version has breaking API changes vs. training data — check
`node_modules/next/dist/docs/` before relying on remembered Next.js APIs.

Additional docs: `docs/PROJECT.md` (project description, external integrations,
local env setup), `docs/CONVENTIONS.md` (commit/branch naming, code layout),
`docs/DESIGN.md` (current UI/styling state), `docs/PROMPTS.md` (itinerary-generation
prompts used by `PlanChatController`, kept in sync with the source), `docs/API.md`
(REST endpoint reference, auth/CORS state, data model), `docs/PRD.md` (Korean —
feature-level requirements and the "why" behind them, not just architecture),
`docs/PLACE_SEARCH.md` (Korean — how the embedding search + region/sub_region
grouping pipeline works, step by step), `docs/WORKFLOW.md` (Korean — the
issue → branch → PRD → implement → PR/review → merge flow, backed by the
`/issue`, `/commit`, and `/pr` skills), `docs/REDIS.md` (Korean — Redis
concepts plus this project's current/planned Redis usage).

## Commands

### Client (`client/`)
```
npm run dev      # start dev server (localhost:3000)
npm run build    # production build
npm run start    # run production build
npm run lint     # eslint
```
Both `package-lock.json` and `pnpm-lock.yaml` are present; confirm which one is
authoritative before adding dependencies.

### Server (`server/`)
```
./gradlew bootRun                              # run the API
./gradlew test                                 # run all tests
./gradlew test --tests DemoApplicationTests    # run a single test class
./gradlew build                                # compile + test + package
```
Requires local PostgreSQL (with pgvector) on port 5434 and Redis on port 6379
(see `server/src/main/resources/application.properties`).

## Architecture

### AI chat → itinerary pipeline
The core flow lives in `PlanChatController` (`/plan/chat`, SSE endpoint). The request
body (`PlanChatRequest`) carries `message`, `history`, and an optional
`accommodationLat`/`accommodationLng` (the client collects this — see "Client structure"
below — via a place search, not free text).
1. Loads the user's wishlist and assigns short ids (`w1`, `w2`, ...).
2. Concatenates chat history + new message, and embeds it via `AiService.createEmbedding`
   (Gemini embedding API).
3. Does keyword matching over the combined conversation text to guess a target
   region (동부/서부/남부/제주시) and category (음식점/관광지/문화시설/레포츠), used as
   optional SQL filters.
4. `JejuPlaceRepository.findSimilarPlacesWithFilter` runs a native pgvector query
   (`embedding <=> query_embedding`, ORDER BY distance) over `jeju_place`, filtered by
   region/category when detected; falls back to an unfiltered search if the filtered
   result is empty. Candidate places also get short ids (`p1`, `p2`, ...).
5. Builds a large Korean system prompt and calls `AiService.chatWithGemini` (with retry
   on 503) in a background thread. The place list is grouped by region and then by
   `sub_region` (읍/면/동, see "Data model") so the model can keep each day's places in
   one small area; wishlist places are listed too. Rules cover output format, minimum
   3 places/day, no reusing a place across days, and honoring arrival/departure time
   mentions.
6. Parses the model's JSON response (`AiResponseParser.parse` strips markdown fences
   and extracts the outermost `{...}`). For `type: schedule` responses, resolves each
   place id back to real name/category/lat/lng from the id maps built in steps 1/4.
7. Per day, places with coordinates are reordered for shortest travel distance
   (`optimalOrder`: brute-force permutations for ≤8 stops, else nearest-neighbor).
   The route is anchored at both ends: every day starts/ends near the accommodation
   (if provided), except the last day, which ends near the airport instead. A second
   Gemini call (`assignTimesForDay`) then assigns realistic visit time windows; it's
   told whether this is the first/last day, and only for those gets the full
   conversation text too, so it can respect an arrival time (day 1) or departure time
   (last day) mentioned in the chat — middle days skip the conversation text to avoid
   resending it on every call.
8. Result is streamed back over SSE as a single JSON payload.

The AI must only ever choose place ids from the lists given in the prompt — it never
invents places or coordinates.

### External integrations (`AiService`, `jeju/`)
- **Gemini** (`gemini.*` in `application.properties`): both chat completion
  (`gemini-2.5-flash`) and text embeddings (`gemini-embedding-001`).
- **Groq** (`AiService.chat`, `llama-3.3-70b-versatile`): present but not used by the
  main plan-chat flow.
- **TourAPI / VisitJeju** (`jeju/TourApiClient`, `TourApiService`, `TourDetailService`):
  fetches real Jeju place data used to populate/seed `jeju_place`.
- **Kakao**: map rendering + place search (client), place search proxy and
  reverse-geocoding (`jeju/SubRegionService`, coord2regioncode) on the server, both
  using `kakao.api.key`.

### Data model
- `JejuPlace` (`jeju_place` table): name/category/main_category/region/sub_region/
  address/lat/lng + a pgvector `embedding` column, queried by cosine/L2 distance for
  semantic search. `region` is one of 4 coarse zones (동부/서부/남부/제주시, threshold-based
  on lat/lng — an approximation, not real administrative boundaries). `sub_region` is a
  finer 읍/면/동 name, backfilled per-place via `SubRegionService.fillSubRegions()`
  (`POST /jeju/sub-region`) using Kakao's coord2regioncode API; used only to group the
  place list in the plan-chat prompt, not for SQL filtering.
- `Wishlist`: user-saved places, merged into the same id-space as `JejuPlace` results
  when building AI prompts.
- Redis (`RedisService`) is available for caching/session-style storage.

### Client structure
- `app/page.tsx` — main screen (`KakaoMap`): map | schedule column | chat column.
  `app/wishlist/page.tsx` — separate page for place search + saved wishlist
  (`SearchBar` + `WishlistPanel`), linked from the map screen's header.
- `feature/<domain>/api.ts` — fetch wrappers per domain (`place`, `plan`, `wishlist`),
  reading the server base URL from `NEXT_PUBLIC_API_URL`. `feature/plan/api.ts` also
  owns the SSE-stream parsing (`sendPlanChat`) and the shared `Schedule`/`Day`/`Place`
  types used by the schedule UI.
- `components/` — `KakaoMap` (map + schedule/chat columns, wishlist markers),
  `PlanChat` (chat + onboarding wizard), `SchedulePanel` (day-tab place list),
  `SearchBar`/`WishlistPanel` (now only used on `app/wishlist/page.tsx`; their
  map-centering callbacks are optional since that page has no map).
- `lib/dayColors.ts` — `getDayColor(day)`, a CVD-checked 7-color palette keyed by day
  number, used for map markers/routes and the schedule panel's day tabs.
- `types/` — shared TS types per domain, mirroring the server's DTOs.

### Chat onboarding (`PlanChat`)
Style/companion/duration/arrival time/departure time/accommodation are collected as a
local, client-only wizard (`ONBOARDING_QUESTIONS` in `PlanChat.tsx`) — button and
multi-select chips, a native time picker, and a Kakao place search for the
accommodation step. None of these steps call the server; only the last one
(accommodation) triggers the real `/plan/chat` request, with the full local Q&A as
`history` and the accommodation's lat/lng attached. This keeps the onboarding to a
single embedding+chat call no matter how many steps the user goes through, instead of
one call per free-text answer.
