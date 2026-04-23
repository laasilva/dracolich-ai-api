package dm.dracolich.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeckStatsDto {
    @JsonProperty("card_count")
    private Integer cardCount;
    @JsonProperty("land_count")
    private Integer landCount;
    @JsonProperty("mana_curve")
    private Map<Integer, Integer> manaCurve;
    @JsonProperty("color_balance")
    private Map<String, Integer> colorBalance;
    @JsonProperty("category_counts")
    private Map<String, Integer> categoryCounts;
    private List<String> warnings;
}
