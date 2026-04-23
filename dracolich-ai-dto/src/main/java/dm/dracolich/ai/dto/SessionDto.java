package dm.dracolich.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dm.dracolich.ai.dto.enums.SessionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SessionDto {
    private String id;
    @JsonProperty("user_id")
    private String userId;
    @JsonProperty("session_type")
    private SessionType sessionType;
    private String format;
    @JsonProperty("commander_name")
    private String commanderName;
    @JsonProperty("commander_id")
    private String commanderId;
    @JsonProperty("color_identity")
    private Set<String> colorIdentity;
    @JsonProperty("deck_list")
    private List<DeckCardDto> deckList;
    @JsonProperty("deck_stats")
    private DeckStatsDto deckStats;
    @JsonProperty("card_suggestions")
    private List<CardSuggestionDto> cardSuggestions;
    @JsonProperty("total_input_tokens")
    private Long totalInputTokens;
    @JsonProperty("total_output_tokens")
    private Long totalOutputTokens;
    @JsonProperty("created_at")
    private Instant createdAt;
    @JsonProperty("updated_at")
    private Instant updatedAt;
}
