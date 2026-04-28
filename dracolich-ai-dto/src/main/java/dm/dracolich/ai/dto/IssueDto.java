package dm.dracolich.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dm.dracolich.ai.dto.enums.IssueSeverity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single finding from AI deck analysis. Populated by the reportIssues tool.
 *
 * <ul>
 *   <li>{@code topic} — short stable key the frontend can match on
 *       ("mana_curve", "color_balance", "removal_count", "card_draw", "land_count", etc.)</li>
 *   <li>{@code severity} — ERROR (illegal/broken), WARNING (concerning but legal), INFO (advisory tip)</li>
 *   <li>{@code reason} — 1-2 sentence human-readable explanation of the issue</li>
 * </ul>
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IssueDto {
    private String topic;
    private IssueSeverity severity;
    private String reason;
}
