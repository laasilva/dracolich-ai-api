package dm.dracolich.ai.datasource.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DeckCardEntity {
    @Field("card_id")
    private String cardId;
    private String name;
    private String category;
    private Integer quantity;
}
