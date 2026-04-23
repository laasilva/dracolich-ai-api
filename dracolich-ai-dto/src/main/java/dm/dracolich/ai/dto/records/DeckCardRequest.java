package dm.dracolich.ai.dto.records;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DeckCardRequest(
        @JsonProperty("card_name") String cardName,
        String category,
        Integer quantity,
        @JsonProperty("is_commander") Boolean isCommander
) {}
