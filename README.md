# dracolich-ai-api

AI-powered deck building assistant for Magic: The Gathering. Uses Spring AI with Anthropic Claude to provide card suggestions, deck analysis, and synergy recommendations via SSE-streamed chat with tool-based architecture.

## Prerequisites

- Java 25
- Maven 3.9+
- `~/.m2/settings-personal.xml` with GitHub Packages credentials (for `dm.dracolich.*` artifacts)
- MongoDB, an Anthropic API key, and a reachable mtg-library-api — only if you intend to *run* it

## Build

```bash
mvn clean install -s ~/.m2/settings-personal.xml
```

## Running

The service is deployed to the `dracolich-dev` cluster and reached through
`https://dev.dracolich.app/dracolich/ai/api/v0/`. It is **internal infrastructure**: the frontend
never calls it directly, only `dracolich-mtg-deck-builder-api` does.

Running it locally is expected when developing a feature or chasing a bug here. Create an
uncommitted `dracolich-ai-web/src/main/resources/application-local.yml` (gitignored — never commit
local config) and run with `SPRING_PROFILES_ACTIVE=local`; `application-dev.yml.example` is a
starting point. Everything below without a default must be supplied there, and the port must be
overridden if another Dracolich service is already on 8080.

Swagger UI: `http://<host>/dracolich/ai/api/v0/swagger-ui.html`.

## Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `PORT` | `8080` | Server port. Actuator listens separately on `7980`. |
| `MONGODB_URI` | _(required)_ | MongoDB connection URI |
| `MONGODB_DATABASE` | _(required)_ | Database name |
| `ANTHROPIC_API_KEY` | _(required)_ | Anthropic API key |
| `DRACOLICH_MTG_LIBRARY_API_BASE_URL` | `http://localhost:8080/dracolich/mtg-library/api/v0` | MTG Library API base URL |
| `CORS_ALLOWED_ORIGINS` | _(empty)_ | Allowed CORS origins |
| `SPRING_PROFILES_ACTIVE` | `dev` | Profile name only — no profile-specific config file exists |

## API Endpoints

Base path: `/dracolich/ai/api/v0/`

### Sessions

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/agent/session` | Create a BUILD or ANALYSIS session |
| `GET` | `/agent/session?user_id=&page=0&size=20` | List sessions by user (paginated) |
| `GET` | `/agent/session/{id}` | Get session state (deck, suggestions, token totals) |
| `DELETE` | `/agent/session/{id}` | Delete a session (204 No Content) |

**Create session (BUILD):**

```json
{
  "user_id": "optional-user-id",
  "session_type": "BUILD",
  "format": "commander",
  "commander_name": "Atraxa, Praetors' Voice"
}
```

**Create session (ANALYSIS):**

```json
{
  "user_id": "optional-user-id",
  "session_type": "ANALYSIS",
  "format": "commander",
  "deck_list": [
    { "card_name": "Atraxa, Praetors' Voice", "is_commander": true },
    { "card_name": "Sol Ring", "category": "Ramp", "quantity": 1 }
  ]
}
```

### Chat

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/agent/chat` | Send a message (SSE-streamed response) |
| `GET` | `/agent/chat/history/{sessionId}` | Get chat history with token usage |

**Send message:**

```json
{
  "session_id": "session-id-here",
  "message": "Suggest 5 cards with strong synergy for this commander"
}
```

Response is streamed via Server-Sent Events (SSE). Each `data:` chunk is a text token from Claude.

**Chat history response includes token usage:**

```json
[
  {
    "role": "USER",
    "content": "Suggest 5 cards...",
    "created_at": "2026-04-22T19:44:39.307Z"
  },
  {
    "role": "ASSISTANT",
    "content": "These cards support a spell-slinging strategy...",
    "input_tokens": 4990,
    "output_tokens": 592,
    "created_at": "2026-04-22T19:44:39.307Z"
  }
]
```

### Session Response

The session object includes structured card suggestions (populated by the AI via tool calls):

```json
{
  "id": "session-id",
  "user_id": "user-id",
  "session_type": "BUILD",
  "format": "commander",
  "commander_name": "Atraxa, Praetors' Voice",
  "color_identity": ["B", "G", "U", "W"],
  "deck_list": [...],
  "card_suggestions": [
    {
      "card_name": "Deepglow Skate",
      "category": "Creature",
      "reason": "Doubles all counters on any permanent when it enters",
      "synergy_score": 0.95
    }
  ],
  "total_input_tokens": 4990,
  "total_output_tokens": 592,
  "created_at": "...",
  "updated_at": "..."
}
```

## AI Tools

The AI agent has access to three tools:

| Tool | Purpose |
|------|---------|
| `CardSearchTool` | Searches the MTG Library API with filters. Returns compact results (name, cost, type, oracle text). Max 10 results per call. |
| `SuggestCardsTool` | Formally recommends cards with structured data. Persisted to the session's `card_suggestions` field for programmatic consumption. |
| `ReportIssuesTool` | Reports structured deck problems as `IssueDto` (with `IssueSeverity`) for programmatic consumption by the caller. |

`DeckAnalysisTool` was removed — deck stats are pre-computed by `dracolich-mtg-deck-builder-api` and
injected into the prompt, avoiding the N API calls it made per invocation. `SynergyFinderTool` was
removed earlier for the same reason.

The system prompt enforces max 1 search call per response to control token budget. Typical interaction: ~5k input tokens, ~600 output tokens.

## Token Tracking

Token usage is tracked at two levels:

- **Per message**: `input_tokens` and `output_tokens` on each assistant message in chat history
- **Per session**: `total_input_tokens` and `total_output_tokens` cumulative on the session object
- **Logs**: INFO-level log per chat operation with per-request and cumulative token counts

## Session Management

- **`user_id`** is optional — sessions without a user ID are anonymous
- Anonymous sessions are **auto-deleted after 24 hours** via MongoDB TTL index
- Authenticated sessions persist until explicitly deleted
- List endpoint requires `user_id` (returns empty without it to prevent full table scans)

## Error Codes

All errors are returned in the standard `DmdResponse` envelope via forge's `ControllerAdvice`.

| Code | HTTP Status | Meaning |
|------|-------------|---------|
| DMD023 | 404 | Session or card not found |
| DMD024 | 400 | Validation error (missing required fields) |
| DMD025 | 500 | Card search tool failed |
| DMD026 | 500 | Deck analysis tool failed |
| DMD027 | 500 | Failed to persist card suggestions |
| DMD028 | 500 | Chat streaming error |
| DMD029 | 503 | MTG Library API unavailable |

## Module Structure

| Module | Purpose |
|--------|---------|
| `dracolich-ai-web` | Spring Boot app, controllers, Swagger UI |
| `dracolich-ai-core` | AgentService, AI tools, Anthropic config |
| `dracolich-ai-client` | WebClient for mtg-library-api (unwraps DmdResponse envelope) |
| `dracolich-ai-datasource` | MongoDB entities, SessionRepository, index config |
| `dracolich-ai-dto` | DTOs, request records, enums, error codes |

## Project Structure

```
dracolich-ai-api/
├── dracolich-ai-dto/              # Shared DTOs
│   └── src/main/java/
│       └── dm/dracolich/ai/dto/
│           ├── enums/             # SessionType, MessageRole
│           ├── error/             # ErrorCodes (DMD023-DMD029)
│           ├── records/           # ChatRequest, SessionCreateRequest
│           └── *.java             # SessionDto, ChatMessageDto, CardSuggestionDto
│
├── dracolich-ai-client/           # MTG Library API client
│   └── src/main/java/
│       └── dm/dracolich/ai/client/
│           ├── config/            # WebClient config (Jackson 3, DmdResponse unwrap)
│           └── mtg/               # MtgLibraryClient
│
├── dracolich-ai-datasource/       # MongoDB persistence
│   └── src/main/java/
│       └── dm/dracolich/ai/datasource/
│           ├── config/            # MongoIndexConfig (TTL + userId indexes)
│           ├── entity/            # SessionEntity, ChatMessageEntity, DeckCardEntity
│           └── repository/        # SessionRepository
│
├── dracolich-ai-core/             # Business logic + AI
│   └── src/main/java/
│       └── dm/dracolich/ai/core/
│           ├── config/            # AnthropicConfig (model, tokens, temperature)
│           ├── service/           # AgentService interface + implementation
│           └── tool/              # CardSearchTool, SuggestCardsTool, ReportIssuesTool
│
└── dracolich-ai-web/              # Application entry point
    └── src/main/java/
        └── dm/dracolich/ai/web/
            ├── config/            # CorsConfig
            └── controller/        # SessionController, ChatController
```

## Tech Stack

- **Java 25** with preview features
- **Spring Boot 4.0** with WebFlux (reactive)
- **Spring AI 1.0.0-M6** with Anthropic Claude (Sonnet)
- **MongoDB** with Reactive Streams
- **MapStruct 1.6.3** for entity-DTO mapping
- **SpringDoc OpenAPI 3.0.1** for Swagger UI
- **Lombok** for boilerplate reduction
- **forge:common** for DmdResponse envelope, error handling, ControllerAdvice

## Running Tests

There are none yet — test dependencies are declared but no tests are written.

---

Part of the [Dracolich](https://github.com/laasilva?tab=repositories&q=dracolich) platform. For the
cross-repo picture — service topology, release pipeline, shared conventions — see the workspace guide
at `~/Dev/Dracolich/CLAUDE.md`.
