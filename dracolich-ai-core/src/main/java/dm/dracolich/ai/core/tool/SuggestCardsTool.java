package dm.dracolich.ai.core.tool;

import dm.dracolich.ai.datasource.entity.SessionEntity;
import dm.dracolich.ai.datasource.repository.SessionRepository;
import dm.dracolich.ai.dto.CardSuggestionDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class SuggestCardsTool {

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();

    private final SessionRepository sessionRepository;

    @Tool(description = "Formally suggest cards for the user's deck. Call this whenever you recommend cards. " +
            "Pass a JSON array where each element has: cardName, category (Ramp, Removal, Card Draw, Win Condition, Creature, Land, etc.), " +
            "reason (1 sentence), synergyScore (0.0 to 1.0), and optionally topic (must match a topic key " +
            "from a prior reportIssues call, like \"removal_count\" or \"card_draw\", to link the suggestion to " +
            "a specific finding). Omit topic for non-analysis sessions or if the suggestion isn't tied to a finding. " +
            "Do NOT list these cards again in your text response.")
    public String suggestCards(
            @ToolParam(description = "The session ID") String sessionId,
            @ToolParam(description = "JSON array of suggestions, e.g. [{\"cardName\":\"Counterspell\",\"category\":\"Interaction\",\"reason\":\"Cheap counter for opposing threats\",\"synergyScore\":0.9,\"topic\":\"removal_count\"}]") String suggestionsJson
    ) {
        List<CardSuggestion> suggestions;
        try {
            suggestions = MAPPER.readValue(suggestionsJson, new com.fasterxml.jackson.core.type.TypeReference<List<CardSuggestion>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse suggestions JSON: {}", e.getMessage());
            return "Error: invalid suggestions format. Send a JSON array of objects with cardName, category, reason, synergyScore.";
        }

        try {
            SessionEntity session = sessionRepository.findById(sessionId).toFuture().join();
            if (session == null) {
                return "Session not found: " + sessionId;
            }

            List<CardSuggestionDto> dtos = suggestions.stream()
                    .map(s -> CardSuggestionDto.builder()
                            .cardName(s.cardName())
                            .category(s.category())
                            .reason(s.reason())
                            .synergyScore(s.synergyScore())
                            .topic(s.topic())
                            .build())
                    .toList();

            session.setCardSuggestions(dtos);
            sessionRepository.save(session).toFuture().join();

            return "Suggested " + suggestions.size() + " cards: " +
                    suggestions.stream().map(CardSuggestion::cardName).collect(Collectors.joining(", "));
        } catch (Exception e) {
            log.error("Failed to persist card suggestions for session {}: {}", sessionId, e.getMessage());
            return "Failed to save suggestions. The cards were: " +
                    suggestions.stream().map(CardSuggestion::cardName).collect(Collectors.joining(", "));
        }
    }

    public record CardSuggestion(
            String cardName,
            String category,
            String reason,
            Double synergyScore,
            String topic
    ) {}
}
