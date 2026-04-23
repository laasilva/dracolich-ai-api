package dm.dracolich.ai.datasource.entity;

import dm.dracolich.ai.dto.enums.MessageRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatMessageEntity {
    private MessageRole role;
    private String content;
    @Field("input_tokens")
    private Long inputTokens;
    @Field("output_tokens")
    private Long outputTokens;
    @Field("created_at")
    private Instant createdAt;
}
