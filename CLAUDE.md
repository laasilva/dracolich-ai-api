# dracolich-ai-api

AI deck building assistant for MTG. Uses Spring AI 1.0.0-M6 + Anthropic Claude (Sonnet).

## Build & Run

Requires forge:common installed locally and mtg-library-api running on port 8080.

```bash
# Build
mvn clean install -s ~/.m2/settings-personal.xml

# Run (port 8083, dev profile auto-active)
mvn spring-boot:run -pl dracolich-ai-web -s ~/.m2/settings-personal.xml
```

API keys go in `.env` at project root (gitignored):
```
ANTHROPIC_API_KEY=sk-ant-...
```

## Module Structure

| Module | Purpose |
|--------|---------|
| `dracolich-ai-web` | Spring Boot app, controllers, Swagger UI |
| `dracolich-ai-core` | AgentService, Spring AI tools, Anthropic config |
| `dracolich-ai-client` | WebClient for mtg-library-api (unwraps DmdResponse) |
| `dracolich-ai-datasource` | MongoDB entities and SessionRepository |
| `dracolich-ai-dto` | DTOs, records, enums shared across modules |

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST /agent/session` | Create BUILD or ANALYSIS session (optional `user_id`) |
| `GET /agent/session?user_id=&page=&size=` | List sessions by user (paginated, sorted by created_at DESC) |
| `GET /agent/session/{id}` | Get session (deck, suggestions, token totals) |
| `DELETE /agent/session/{id}` | Delete session (204 No Content) |
| `POST /agent/chat` | Send message, SSE-streamed response |
| `GET /agent/chat/history/{sessionId}` | Chat history with token usage per message |

Base path: `/dracolich/ai/api/v0/`

## AI Tools

| Tool | Purpose | Token Cost |
|------|---------|------------|
| `CardSearchTool` | Broad card search via mtg-library-api. Returns compact format (name, cost, type, truncated oracle text). Max 10 results | 1 API call to library |
| `DeckAnalysisTool` | Analyzes deck stats (mana curve, color balance, warnings). Fetches each card by ID | N API calls (1 per card) |
| `SuggestCardsTool` | Persists structured suggestions to session. Accepts JSON string param (Spring AI generic type workaround) | 0 API calls (just MongoDB) |

`SynergyFinderTool` was deleted — it made N+1 API calls per invocation.

## Error Handling

Uses forge's `ControllerAdvice` (registered via `@ComponentScan` on `dm.dracolich.forge`). Error codes in `dm.dracolich.ai.dto.error.ErrorCodes`:

| Code | Meaning |
|------|---------|
| DMD023 | Not found (session, card) |
| DMD024 | Validation error (missing commander_name for BUILD, etc.) |
| DMD025 | Card search failed |
| DMD026 | Deck analysis failed |
| DMD027 | Failed to persist card suggestions |
| DMD028 | Chat streaming error |
| DMD029 | MTG Library API unavailable |

All service methods use `switchIfEmpty(Mono.error(notFound(...)))` for 404s. Tools wrapped in try-catch — return descriptive error strings on failure instead of crashing.

Input validation: `@Valid` on controllers, `@NotNull`/`@NotBlank` on `SessionCreateRequest` and `ChatRequest`. BUILD requires `commander_name`, ANALYSIS requires `deck_list`.

## Session Management

- `userId` field on sessions — optional, null for anonymous users
- `GET /agent/session?user_id=&page=&size=` — paginated list, returns empty without user_id
- `DELETE /agent/session/{id}` — validates session exists, returns 204
- MongoDB indexes: `idx_user_id` for list queries, `idx_ttl_anonymous_sessions` (partial TTL, 24h expiry for sessions without userId)
- Ownership check on delete deferred to Phase 3 (requires auth)

## Token Budget

System prompt enforces max 1 `searchCards` call per response. Typical flow:
1. AI calls `searchCards` once with broad filters → ~10 results
2. AI calls `suggestCards` with top picks → persisted to session
3. AI writes 1-2 sentence summary

Expected: ~5k input tokens, ~600 output tokens per interaction.

Token usage is logged at INFO level and persisted on `ChatMessageEntity` (per-message) and `SessionEntity` (cumulative).

## Key Implementation Details

- **Streaming**: Uses `.stream().chatResponse()` (not `.content()`) to capture token usage from ChatResponse metadata
- **Tool execution**: All tools use `.toFuture().join()` instead of `.block()` — Reactor rejects `.block()` on Netty event loop threads during streaming
- **DmdResponse unwrapping**: `MtgLibraryClient` deserializes to `DmdResponse<T>` via `ParameterizedTypeReference` then extracts `.getPayload()`. Never deserialize directly to DTO.
- **Jackson 2/3 split**: Spring Boot 4 uses Jackson 3 (`tools.jackson`), Spring AI uses Jackson 2 (`com.fasterxml.jackson`). WebClient codecs use `JacksonJsonDecoder` (Jackson 3). Tool internals use static Jackson 2 ObjectMapper.
- **`SuggestCardsTool` param**: Accepts `suggestionsJson` as String, not `List<CardSuggestion>` — Spring AI can't deserialize generic List of records in tool params.
- **`PageRecord` content**: Returns raw type (type erasure), items are `LinkedHashMap` at runtime. `CardSearchTool` uses `ObjectMapper.convertValue()` to map to `CardDto`.

## Swagger UI

Available at: `http://localhost:8083/dracolich/ai/api/v0/swagger-ui.html`

Required fix: explicit `swagger-annotations:2.2.41` (non-jakarta) dependency to match swagger-core-jakarta version. Spring AI pulls in 2.2.25 transitively which lacks `$dynamicRef()`.

## Phase 3 Readiness — Deferred Items

| Item | Priority | Notes |
|------|----------|-------|
| Authentication (Spring Security + JWT) | CRITICAL | No auth on any endpoint. Phase 3 blocker. |
| Ownership check on DELETE | CRITICAL | Anyone can delete any session by ID |
| Rate limiting on `/agent/chat` | HIGH | One user can burn Anthropic quota. Plan: Spring Cloud Gateway |
| Test coverage | MEDIUM | 0% — test deps declared but no tests written |
| `application-prod.yml` | MEDIUM | Deferred to helm/argo deployment to AWS |
| Metrics/monitoring (Micrometer) | LOW | Not implemented |
