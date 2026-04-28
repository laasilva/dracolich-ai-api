package dm.dracolich.ai.core.service;

import dm.dracolich.ai.client.mtg.MtgLibraryClient;
import dm.dracolich.ai.datasource.entity.ChatMessageEntity;
import dm.dracolich.ai.datasource.entity.DeckCardEntity;
import dm.dracolich.ai.datasource.entity.SessionEntity;
import dm.dracolich.ai.datasource.repository.SessionRepository;
import dm.dracolich.ai.dto.ChatMessageDto;
import dm.dracolich.ai.dto.DeckCardDto;
import dm.dracolich.ai.dto.SessionDto;
import dm.dracolich.ai.dto.enums.MessageRole;
import dm.dracolich.ai.dto.enums.SessionType;
import dm.dracolich.ai.dto.error.ErrorCodes;
import dm.dracolich.ai.dto.records.SessionCreateRequest;
import dm.dracolich.forge.error.ApiError;
import dm.dracolich.forge.exception.ResponseException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentServiceImpl implements AgentService {

    private final SessionRepository sessionRepository;
    private final MtgLibraryClient mtgClient;
    private final ChatClient agentChatClient;

    @Override
    public Mono<SessionDto> createSession(SessionCreateRequest request) {
        if (request.sessionType() == SessionType.BUILD) {
            if (request.commanderName() == null || request.commanderName().isBlank()) {
                return Mono.error(new ResponseException(
                        ErrorCodes.DMD024.format("commander_name is required for BUILD sessions"),
                        List.of(new ApiError(ErrorCodes.DMD024)),
                        HttpStatus.BAD_REQUEST));
            }
            return createBuildSession(request);
        } else {
            if (request.deckList() == null || request.deckList().isEmpty()) {
                return Mono.error(new ResponseException(
                        ErrorCodes.DMD024.format("deck_list is required for ANALYSIS sessions"),
                        List.of(new ApiError(ErrorCodes.DMD024)),
                        HttpStatus.BAD_REQUEST));
            }
            return createAnalysisSession(request);
        }
    }

    @Override
    public Mono<SessionDto> getSession(String sessionId) {
        return sessionRepository.findById(sessionId)
                .switchIfEmpty(Mono.error(notFound("Session", sessionId)))
                .map(this::toSessionDto);
    }

    @Override
    public Flux<SessionDto> listSessions(String userId, int page, int size) {
        if (userId == null || userId.isBlank()) {
            return Flux.empty();
        }
        return sessionRepository.findByUserId(userId,
                        org.springframework.data.domain.PageRequest.of(page, size,
                                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "created_at")))
                .map(this::toSessionDto);
    }

    @Override
    public Mono<Void> deleteSession(String sessionId) {
        return sessionRepository.findById(sessionId)
                .switchIfEmpty(Mono.error(notFound("Session", sessionId)))
                .flatMap(session -> sessionRepository.deleteById(sessionId));
    }

    @Override
    public Flux<String> chat(String sessionId, String userMessage) {
        return sessionRepository.findById(sessionId)
                .switchIfEmpty(Mono.error(notFound("Session", sessionId)))
                .flatMapMany(session -> {
                    List<Message> messages = buildMessages(session, userMessage);

                    Flux<ChatResponse> responseStream = agentChatClient.prompt()
                            .messages(messages)
                            .stream()
                            .chatResponse();

                    StringBuilder fullResponse = new StringBuilder();
                    AtomicReference<Long> inputTokens = new AtomicReference<>();
                    AtomicReference<Long> outputTokens = new AtomicReference<>();

                    return responseStream
                            .mapNotNull(chatResponse -> {
                                if (chatResponse.getMetadata() != null
                                        && chatResponse.getMetadata().getUsage() != null) {
                                    Usage usage = chatResponse.getMetadata().getUsage();
                                    if (usage.getPromptTokens() != null && usage.getPromptTokens() > 0) {
                                        inputTokens.set(usage.getPromptTokens().longValue());
                                    }
                                    if (usage.getCompletionTokens() != null && usage.getCompletionTokens() > 0) {
                                        outputTokens.set(usage.getCompletionTokens().longValue());
                                    }
                                }

                                if (chatResponse.getResult() != null
                                        && chatResponse.getResult().getOutput() != null
                                        && chatResponse.getResult().getOutput().getText() != null) {
                                    String text = chatResponse.getResult().getOutput().getText();
                                    fullResponse.append(text);
                                    return text;
                                }
                                return null;
                            })
                            .doOnComplete(() ->
                                persistMessages(sessionId, userMessage, fullResponse.toString(),
                                        inputTokens.get(), outputTokens.get())
                                    .subscribeOn(Schedulers.boundedElastic())
                                    .subscribe()
                            )
                            .onErrorResume(e -> {
                                log.error("Chat streaming error for session {}: {}", sessionId, e.getMessage());
                                if (!fullResponse.isEmpty()) {
                                    persistMessages(sessionId, userMessage, fullResponse.toString(),
                                            inputTokens.get(), outputTokens.get())
                                        .subscribeOn(Schedulers.boundedElastic())
                                        .subscribe();
                                }
                                return Flux.error(new ResponseException(
                                        ErrorCodes.DMD028.format(e.getMessage()),
                                        List.of(new ApiError(ErrorCodes.DMD028)),
                                        HttpStatus.INTERNAL_SERVER_ERROR));
                            });
                });
    }

    private Mono<SessionDto> createBuildSession(SessionCreateRequest request) {
        return mtgClient.fetchCardByName(request.commanderName())
                .switchIfEmpty(Mono.error(notFound("Commander card", request.commanderName())))
                .flatMap(commander -> {
                    Set<String> colorIdentity = Set.of();
                    if (commander.getDefaultFace() != null &&
                            commander.getDefaultFace().getGameplayProperty() != null &&
                            commander.getDefaultFace().getGameplayProperty().getColorIdentity() != null) {
                        colorIdentity = commander.getDefaultFace().getGameplayProperty().getColorIdentity()
                                .stream().map(Enum::name).collect(Collectors.toSet());
                    }

                    DeckCardEntity commanderCard = DeckCardEntity.builder()
                            .cardId(commander.getId())
                            .name(commander.getName())
                            .category("Commander")
                            .quantity(1)
                            .build();

                    SessionEntity session = SessionEntity.builder()
                            .userId(request.userId())
                            .sessionType(SessionType.BUILD)
                            .format(request.format())
                            .commanderName(commander.getName())
                            .commanderId(commander.getId())
                            .colorIdentity(colorIdentity)
                            .deckList(new ArrayList<>(List.of(commanderCard)))
                            .chatHistory(new ArrayList<>())
                            .totalInputTokens(0L)
                            .totalOutputTokens(0L)
                            .createdAt(Instant.now())
                            .updatedAt(Instant.now())
                            .build();

                    return sessionRepository.save(session);
                })
                .map(this::toSessionDto)
                .onErrorResume(ResponseException.class, Mono::error)
                .onErrorResume(e -> {
                    log.error("Failed to create BUILD session for commander '{}': {}", request.commanderName(), e.getMessage());
                    return Mono.error(new ResponseException(
                            ErrorCodes.DMD029.getMessage(),
                            List.of(new ApiError(ErrorCodes.DMD029)),
                            HttpStatus.SERVICE_UNAVAILABLE));
                });
    }

    private Mono<SessionDto> createAnalysisSession(SessionCreateRequest request) {
        List<Mono<DeckCardEntity>> cardMonos = request.deckList().stream()
                .map(req -> mtgClient.fetchCardByName(req.cardName())
                        .map(card -> DeckCardEntity.builder()
                                .cardId(card.getId())
                                .name(card.getName())
                                .category(Boolean.TRUE.equals(req.isCommander()) ? "Commander" : req.category())
                                .quantity(req.quantity() != null ? req.quantity() : 1)
                                .build())
                        .onErrorResume(e -> {
                            log.warn("Card not found during import: {}", req.cardName());
                            return Mono.empty();
                        }))
                .toList();

        return Flux.merge(cardMonos)
                .collectList()
                .flatMap(deckCards -> {
                    DeckCardEntity commander = deckCards.stream()
                            .filter(c -> "Commander".equals(c.getCategory()))
                            .findFirst()
                            .orElse(null);

                    Mono<Set<String>> colorIdentityMono;
                    if (commander != null) {
                        colorIdentityMono = mtgClient.fetchCardById(commander.getCardId())
                                .map(card -> {
                                    if (card.getDefaultFace() != null &&
                                            card.getDefaultFace().getGameplayProperty() != null &&
                                            card.getDefaultFace().getGameplayProperty().getColorIdentity() != null) {
                                        return card.getDefaultFace().getGameplayProperty().getColorIdentity()
                                                .stream().map(Enum::name).collect(Collectors.toSet());
                                    }
                                    return Set.<String>of();
                                })
                                .onErrorResume(e -> {
                                    log.warn("Failed to fetch color identity for commander: {}", e.getMessage());
                                    return Mono.just(Set.<String>of());
                                });
                    } else {
                        colorIdentityMono = Mono.just(Set.<String>of());
                    }

                    return colorIdentityMono.flatMap(colorIdentity -> {
                        SessionEntity session = SessionEntity.builder()
                                .userId(request.userId())
                                .sessionType(SessionType.ANALYSIS)
                                .format(request.format())
                                .commanderName(commander != null ? commander.getName() : null)
                                .commanderId(commander != null ? commander.getCardId() : null)
                                .colorIdentity(colorIdentity)
                                .deckList(new ArrayList<>(deckCards))
                                .chatHistory(new ArrayList<>())
                                .totalInputTokens(0L)
                                .totalOutputTokens(0L)
                                .createdAt(Instant.now())
                                .updatedAt(Instant.now())
                            .build();

                        return sessionRepository.save(session);
                    });
                })
                .map(this::toSessionDto);
    }

    private List<Message> buildMessages(SessionEntity session, String userMessage) {
        List<Message> messages = new ArrayList<>();

        messages.add(new SystemMessage(buildSystemPrompt(session)));

        if (session.getChatHistory() != null) {
            for (ChatMessageEntity msg : session.getChatHistory()) {
                if (msg.getRole() == MessageRole.USER) {
                    messages.add(new UserMessage(msg.getContent()));
                } else {
                    messages.add(new AssistantMessage(msg.getContent()));
                }
            }
        }

        messages.add(new UserMessage(userMessage));

        return messages;
    }

    private String buildSystemPrompt(SessionEntity session) {
        var sb = new StringBuilder();
        sb.append("""
                You are a Magic: The Gathering deck building assistant. Be concise.

                CRITICAL — Token budget rules:
                - Make AT MOST 1 searchCards call per response. Use broad filters (colors + type + format) to get diverse results in one call.
                - NEVER search for individual cards one at a time. One broad search, then pick the best from results.
                - After searching, call suggest_cards with your picks. Do not list card details in your text.
                - After calling suggest_cards, write 1-2 sentences summarizing your reasoning.
                - If search returns no results, suggest cards from your knowledge — do NOT retry with different queries.
                - Warn about deck weaknesses briefly.
                - Inform, never block. The user always decides.

                """);

        sb.append("Current session:\n");
        sb.append("- Session ID: ").append(session.getId()).append("\n");
        sb.append("- Session Type: ").append(session.getSessionType()).append("\n");
        sb.append("- Format: ").append(session.getFormat()).append("\n");

        if (session.getCommanderName() != null) {
            sb.append("- Commander: ").append(session.getCommanderName()).append("\n");
        }
        if (session.getColorIdentity() != null && !session.getColorIdentity().isEmpty()) {
            sb.append("- Color Identity: ").append(session.getColorIdentity()).append("\n");
        }

        if (session.getDeckList() != null && !session.getDeckList().isEmpty()) {
            sb.append("- Deck (").append(session.getDeckList().size()).append(" cards):\n");
            for (DeckCardEntity card : session.getDeckList()) {
                sb.append("  - ").append(card.getName());
                if (card.getCategory() != null) sb.append(" [").append(card.getCategory()).append("]");
                sb.append("\n");
            }
        }

        if (session.getSessionType() == SessionType.ANALYSIS) {
            sb.append("""

                    This is a deck ANALYSIS session. Pre-computed stats (mana curve, lands, color
                    pie, average CMC, warnings) will be included in the user message — use those
                    directly, do NOT estimate or recompute them. Workflow:
                    1. Call reportIssues with structured findings — use stable topic keys like
                       "mana_curve", "color_balance", "removal_count", "card_draw", "land_count",
                       "win_conditions". One reportIssues call with the full array of findings.
                       severity = ERROR (illegal/broken) | WARNING (concerning but legal) | INFO (advisory).
                    2. Call suggestCards with cards that address the issues you reported. CRITICAL:
                       set the "topic" field on each suggestion to match the topic key of the issue
                       it solves. For example, if you reported a "removal_count" issue and suggest
                       "Swords to Plowshares", that suggestion's topic should be "removal_count".
                       This lets the frontend render suggestions grouped by problem.
                    3. Write a brief 2-3 sentence summary. Do NOT repeat issue details or card lists in
                       your text — both are persisted structurally and rendered visually by the frontend.
                    """);
        }

        return sb.toString();
    }

    private Mono<Void> persistMessages(String sessionId, String userMessage, String assistantResponse,
                                        Long inputTokens, Long outputTokens) {
        return sessionRepository.findById(sessionId)
                .flatMap(session -> {
                    if (session.getChatHistory() == null) {
                        session.setChatHistory(new ArrayList<>());
                    }

                    session.getChatHistory().add(ChatMessageEntity.builder()
                            .role(MessageRole.USER)
                            .content(userMessage)
                            .createdAt(Instant.now())
                            .build());

                    session.getChatHistory().add(ChatMessageEntity.builder()
                            .role(MessageRole.ASSISTANT)
                            .content(assistantResponse)
                            .inputTokens(inputTokens)
                            .outputTokens(outputTokens)
                            .createdAt(Instant.now())
                            .build());

                    if (inputTokens != null) {
                        session.setTotalInputTokens(
                                (session.getTotalInputTokens() != null ? session.getTotalInputTokens() : 0L) + inputTokens);
                    }
                    if (outputTokens != null) {
                        session.setTotalOutputTokens(
                                (session.getTotalOutputTokens() != null ? session.getTotalOutputTokens() : 0L) + outputTokens);
                    }

                    log.info("Token usage for session {}: input={}, output={}, cumulative_input={}, cumulative_output={}",
                            sessionId, inputTokens, outputTokens,
                            session.getTotalInputTokens(), session.getTotalOutputTokens());

                    session.setUpdatedAt(Instant.now());
                    return sessionRepository.save(session);
                })
                .doOnError(e -> log.error("Failed to persist messages for session {}: {}", sessionId, e.getMessage()))
            .then();
    }

    @Override
    public Flux<ChatMessageDto> getChatHistory(String sessionId) {
        return sessionRepository.findById(sessionId)
                .switchIfEmpty(Mono.error(notFound("Session", sessionId)))
                .flatMapMany(session -> {
                    if (session.getChatHistory() == null || session.getChatHistory().isEmpty()) {
                        return Flux.empty();
                    }
                    return Flux.fromIterable(session.getChatHistory())
                            .map(this::toChatMessageDto);
                });
    }

    private SessionDto toSessionDto(SessionEntity entity) {
        return SessionDto.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .sessionType(entity.getSessionType())
                .format(entity.getFormat())
                .commanderName(entity.getCommanderName())
                .commanderId(entity.getCommanderId())
                .colorIdentity(entity.getColorIdentity())
                .deckList(entity.getDeckList() != null
                        ? entity.getDeckList().stream().map(this::toDeckCardDto).toList()
                        : null)
                .cardSuggestions(entity.getCardSuggestions())
                .analysisIssues(entity.getAnalysisIssues())
                .totalInputTokens(entity.getTotalInputTokens())
                .totalOutputTokens(entity.getTotalOutputTokens())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
            .build();
    }

    private DeckCardDto toDeckCardDto(DeckCardEntity entity) {
        return DeckCardDto.builder()
                .cardId(entity.getCardId())
                .name(entity.getName())
                .category(entity.getCategory())
                .quantity(entity.getQuantity())
                .build();
    }

    private ChatMessageDto toChatMessageDto(ChatMessageEntity entity) {
        return ChatMessageDto.builder()
                .role(entity.getRole())
                .content(entity.getContent())
                .inputTokens(entity.getInputTokens())
                .outputTokens(entity.getOutputTokens())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private ResponseException notFound(String entity, String identifier) {
        return new ResponseException(
                ErrorCodes.DMD023.format(entity, identifier),
                List.of(new ApiError(ErrorCodes.DMD023)),
                HttpStatus.NOT_FOUND);
    }
}
