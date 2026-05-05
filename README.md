# dracolich-ai-api 

AI-powered deck building assistant for Magic: The Gathering. Uses Spring AI with Anthropic Claude to provide card suggestions, deck analysis, and synergy recommendations via SSE-streamed chat with tool-based architecture.

## Prerequisites

- Java 25
- MongoDB running on `localhost:27017`
- Maven 3.9+
- [dracolich-mtg-library-api](https://github.com/laasilva/dracolich-mtg-library-api) running on port 8080
- Anthropic API key

## Quick Start

```bash
# Set up API key (one-time)
echo "ANTHROPIC_API_KEY=sk-ant-..." > .env

# Build
mvn clean install -s ~/.m2/settings-personal.xml

# Run (port 8083, dev profile auto-active)
mvn spring-boot:run -pl dracolich-ai-web -s ~/.m2/settings-personal.xml
```

The API starts on `http://localhost:8083/dracolich/ai/api/v0/`.

Swagger UI is available at `http://localhost:8083/dracolich/ai/api/v0/swagger-ui.html`.

## Configuration

| Variable | Default | Description |
|----------|---------|-------------|
| `PORT` | `8083` | Server port |
| `MONGODB_URI` | `mongodb://localhost:27017/dracolich-ai-db` | MongoDB connection URI |
| `MONGODB_DATABASE` | `dracolich-ai-db` | Database name |
| `ANTHROPIC_API_KEY` | _(required)_ | Anthropic API key (via `.env` file) |
| `MTG_LIBRARY_API_BASE_URL` | `http://localhost:8080/dracolich/mtg-library/api/v0` | MTG Library API base URL |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:5173` | Allowed CORS origins |

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
| `DeckAnalysisTool` | Analyzes the current deck — mana curve, color balance, category breakdown, and warnings (low lands, missing removal, etc.). |
| `SuggestCardsTool` | Formally recommends cards with structured data. Persisted to the session's `card_suggestions` field for programmatic consumption. |

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
│           └── tool/              # CardSearchTool, DeckAnalysisTool, SuggestCardsTool
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

```bash
mvn test -s ~/.m2/settings-personal.xml
```
