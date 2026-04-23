package dm.dracolich.ai.dto.records;

import com.fasterxml.jackson.annotation.JsonProperty;
import dm.dracolich.ai.dto.enums.SessionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SessionCreateRequest(
        @JsonProperty("user_id") String userId,
        @NotNull(message = "session_type is required") @JsonProperty("session_type") SessionType sessionType,
        @NotBlank(message = "format is required") String format,
        @JsonProperty("commander_name") String commanderName,
        @JsonProperty("deck_list") List<DeckCardRequest> deckList
) {}
