package dm.dracolich.ai.core.tool;

import dm.dracolich.ai.client.mtg.MtgLibraryClient;
import dm.dracolich.mtgLibrary.dto.CardDto;
import dm.dracolich.mtgLibrary.dto.CardFaceDto;
import dm.dracolich.mtgLibrary.dto.enums.CardType;
import dm.dracolich.mtgLibrary.dto.enums.Color;
import dm.dracolich.mtgLibrary.dto.enums.Format;
import dm.dracolich.mtgLibrary.dto.records.CardSearchRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class CardSearchTool {

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = new com.fasterxml.jackson.databind.ObjectMapper()
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final MtgLibraryClient mtgClient;

    @Tool(description = "Search for MTG cards by name, colors, type, keywords, format legality, or mana value range. " +
            "Returns up to 10 cards with name, mana cost, type, and oracle text. " +
            "Use broad filters to get diverse results in a single call.")
    public String searchCards(
            @ToolParam(description = "Card name (partial match, case-insensitive)") String name,
            @ToolParam(description = "Colors to filter by (W, U, B, R, G)") List<String> colors,
            @ToolParam(description = "Card type (CREATURE, INSTANT, SORCERY, ENCHANTMENT, ARTIFACT, LAND, PLANESWALKER)") List<String> types,
            @ToolParam(description = "Keywords to search for (e.g. flying, trample, proliferate)") List<String> keywords,
            @ToolParam(description = "Format the card must be legal in (STANDARD, COMMANDER, MODERN, etc.)") String legalIn,
            @ToolParam(description = "Minimum mana value") Double minManaValue,
            @ToolParam(description = "Maximum mana value") Double maxManaValue
    ) {
        Set<Color> colorSet = colors != null
                ? colors.stream().map(c -> parseEnum(Color.class, c)).filter(java.util.Objects::nonNull).collect(Collectors.toSet())
                : null;
        Set<CardType> typeSet = types != null
                ? types.stream().map(t -> parseEnum(CardType.class, t)).filter(java.util.Objects::nonNull).collect(Collectors.toSet())
                : null;
        Format format = legalIn != null ? parseEnum(Format.class, legalIn) : null;

        var searchRecord = new CardSearchRecord(
                null, colorSet, null, typeSet, null, keywords != null ? Set.copyOf(keywords) : null,
                format, null, minManaValue, maxManaValue, null
        );

        try {
            var result = mtgClient.searchCards(name, searchRecord, 0, 10).toFuture().join();
            if (result == null || result.content() == null || result.content().isEmpty()) {
                return "No cards found matching the criteria.";
            }

            var sb = new StringBuilder();
            sb.append("Found ").append(result.totalElements()).append(" cards (showing ").append(result.content().size()).append("):\n");
            for (var item : result.content()) {
                CardDto card = MAPPER.convertValue(item, CardDto.class);
                sb.append("- ").append(card.getName());
                CardFaceDto face = card.getDefaultFace();
                if (face != null) {
                    if (face.getGameplayProperty() != null && face.getGameplayProperty().getManaCost() != null) {
                        sb.append(" ").append(face.getGameplayProperty().getManaCost());
                    }
                    if (face.getFullType() != null) {
                        sb.append(" | ").append(face.getFullType());
                    }
                    if (face.getOracleText() != null) {
                        String text = face.getOracleText();
                        if (text.length() > 120) text = text.substring(0, 120) + "...";
                        sb.append(" | ").append(text.replace("\n", " "));
                    }
                }
                if (card.getEdhrecRank() != null) {
                    sb.append(" [EDHREC #").append(card.getEdhrecRank()).append("]");
                }
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("Card search failed: {}", e.getMessage());
            return "Card search unavailable. Suggest cards from your knowledge instead.";
        }
    }

    private <E extends Enum<E>> E parseEnum(Class<E> enumClass, String value) {
        if (value == null) return null;
        try {
            return Enum.valueOf(enumClass, value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
