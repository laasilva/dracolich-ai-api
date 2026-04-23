package dm.dracolich.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CardSuggestionDto {
    @JsonProperty("card_name")
    private String cardName;
    private String reason;
    private String category;
    @JsonProperty("synergy_score")
    private Double synergyScore;
}
