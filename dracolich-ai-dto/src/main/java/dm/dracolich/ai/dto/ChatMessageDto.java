package dm.dracolich.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dm.dracolich.ai.dto.enums.MessageRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatMessageDto {
    private MessageRole role;
    private String content;
    @JsonProperty("input_tokens")
    private Long inputTokens;
    @JsonProperty("output_tokens")
    private Long outputTokens;
    @JsonProperty("created_at")
    private Instant createdAt;
}
