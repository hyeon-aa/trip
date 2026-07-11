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
The core flow lives in `PlanChatController` (`/plan/chat`, SSE endpoint):
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
5. Builds a large Korean system prompt (place list grouped by region + wishlist,
   strict output-format and itinerary rules) and calls `AiService.chatWithGemini`
   (with retry on 503) in a background thread.
6. Parses the model's JSON response (`parseAiJson` strips markdown fences and
   extracts the outermost `{...}`). For `type: schedule` responses, resolves each
   place id back to real name/category/lat/lng from the id maps built in steps 1/4.
7. Per day, places with coordinates are reordered for shortest travel distance
   (`optimalOrder`: brute-force permutations for ≤8 stops, else nearest-neighbor;
   the last day is anchored toward the airport), then a second Gemini call
   (`assignTimesForDay`) assigns realistic visit time windows.
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
- **Kakao**: map rendering on the client, place search key on the server.

### Data model
- `JejuPlace` (`jeju_place` table): name/category/main_category/region/address/lat/lng
  + a pgvector `embedding` column, queried by cosine/L2 distance for semantic search.
- `Wishlist`: user-saved places, merged into the same id-space as `JejuPlace` results
  when building AI prompts.
- Redis (`RedisService`) is available for caching/session-style storage.

### Client structure
- `feature/<domain>/api.ts` — fetch wrappers per domain (`place`, `plan`, `wishlist`),
  reading the server base URL from `NEXT_PUBLIC_API_URL`. `feature/plan/api.ts` also
  owns the SSE-stream parsing (`sendPlanChat`) and the shared `Schedule`/`Day`/`Place`
  types used by the schedule UI.
- `components/` — `KakaoMap`, `PlanChat`, `SchedulePanel`, `SearchBar`,
  `WishlistPanel` are the main feature components composed in `app/page.tsx`.
- `types/` — shared TS types per domain, mirroring the server's DTOs.
