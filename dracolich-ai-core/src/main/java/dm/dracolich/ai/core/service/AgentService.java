package dm.dracolich.ai.core.service;

import dm.dracolich.ai.dto.ChatMessageDto;
import dm.dracolich.ai.dto.SessionDto;
import dm.dracolich.ai.dto.records.SessionCreateRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AgentService {
    Mono<SessionDto> createSession(SessionCreateRequest request);
    Mono<SessionDto> getSession(String sessionId);
    Flux<SessionDto> listSessions(String userId, int page, int size);
    Mono<Void> deleteSession(String sessionId);
    Flux<String> chat(String sessionId, String userMessage);
    Flux<ChatMessageDto> getChatHistory(String sessionId);
}
