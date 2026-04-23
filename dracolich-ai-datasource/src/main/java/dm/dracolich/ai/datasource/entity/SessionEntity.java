package dm.dracolich.ai.datasource.entity;

import dm.dracolich.ai.dto.enums.SessionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Document(collection = "sessions")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SessionEntity {
    @Id
    private String id;
    @Field("user_id")
    private String userId;
    @Field("session_type")
    private SessionType sessionType;
    private String format;
    @Field("commander_name")
    private String commanderName;
    @Field("commander_id")
    private String commanderId;
    @Field("color_identity")
    private Set<String> colorIdentity;
    @Field("deck_list")
    private List<DeckCardEntity> deckList;
    @Field("chat_history")
    private List<ChatMessageEntity> chatHistory;
    @Field("card_suggestions")
    private List<dm.dracolich.ai.dto.CardSuggestionDto> cardSuggestions;
    @Field("total_input_tokens")
    private Long totalInputTokens;
    @Field("total_output_tokens")
    private Long totalOutputTokens;
    @Field("created_at")
    private Instant createdAt;
    @Field("updated_at")
    private Instant updatedAt;
}
