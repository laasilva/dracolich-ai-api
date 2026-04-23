package dm.dracolich.ai.core.tool;

import dm.dracolich.ai.client.mtg.MtgLibraryClient;
import dm.dracolich.ai.datasource.entity.DeckCardEntity;
import dm.dracolich.ai.datasource.entity.SessionEntity;
import dm.dracolich.ai.datasource.repository.SessionRepository;
import dm.dracolich.mtgLibrary.dto.CardDto;
import dm.dracolich.mtgLibrary.dto.CardFaceDto;
import dm.dracolich.mtgLibrary.dto.GameplayPropertyDto;
import dm.dracolich.mtgLibrary.dto.enums.Color;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
@lombok.extern.slf4j.Slf4j
public class DeckAnalysisTool {

    private final SessionRepository sessionRepository;
    private final MtgLibraryClient mtgClient;

    @Tool(description = "Analyze the current deck in a session. Returns mana curve, color balance, " +
            "category breakdown, card count, land count, and warnings about potential problems " +
            "(too few lands, missing removal, bad mana curve, etc.). " +
            "Use this after adding cards, when the user asks about their deck's stats, " +
            "or when you want to identify weaknesses to suggest improvements.")
    public String analyzeDeck(
            @ToolParam(description = "The session ID to analyze") String sessionId
    ) {
        try {
            return doAnalyzeDeck(sessionId);
        } catch (Exception e) {
            log.error("Deck analysis failed for session {}: {}", sessionId, e.getMessage());
            return "Deck analysis unavailable due to a service error. Try again later.";
        }
    }

    private String doAnalyzeDeck(String sessionId) {
        SessionEntity session = sessionRepository.findById(sessionId).toFuture().join();
        if (session == null) {
            return "Session not found: " + sessionId;
        }

        List<DeckCardEntity> deck = session.getDeckList();
        if (deck == null || deck.isEmpty()) {
            return "Deck is empty. No cards to analyze.";
        }

        Map<Integer, Integer> manaCurve = new TreeMap<>();
        Map<String, Integer> colorBalance = new LinkedHashMap<>();
        Map<String, Integer> categoryCounts = new LinkedHashMap<>();
        int totalCards = 0;
        int landCount = 0;
        List<String> warnings = new ArrayList<>();

        for (DeckCardEntity deckCard : deck) {
            int qty = deckCard.getQuantity() != null ? deckCard.getQuantity() : 1;
            totalCards += qty;

            String category = deckCard.getCategory() != null ? deckCard.getCategory() : "Uncategorized";
            categoryCounts.merge(category, qty, Integer::sum);

            CardDto card = null;
            try {
                card = mtgClient.fetchCardById(deckCard.getCardId()).toFuture().join();
            } catch (Exception e) {
                log.warn("Failed to fetch card {}: {}", deckCard.getCardId(), e.getMessage());
            }
            if (card != null && card.getDefaultFace() != null) {
                CardFaceDto face = card.getDefaultFace();

                if (face.getFullType() != null && face.getFullType().toLowerCase().contains("land")) {
                    landCount += qty;
                }

                GameplayPropertyDto gp = face.getGameplayProperty();
                if (gp != null && gp.getManaValue() != null) {
                    int mv = gp.getManaValue().intValue();
                    manaCurve.merge(mv, qty, Integer::sum);
                }

                if (gp != null && gp.getColors() != null) {
                    for (Color color : gp.getColors()) {
                        colorBalance.merge(color.name(), qty, Integer::sum);
                    }
                }
            }
        }

        if (session.getFormat() != null && session.getFormat().equalsIgnoreCase("COMMANDER")) {
            if (totalCards < 100) warnings.add("Deck has " + totalCards + "/100 cards.");
            if (landCount < 33) warnings.add("Low land count (" + landCount + "). Commander decks typically want 35-38 lands.");
            if (landCount > 42) warnings.add("High land count (" + landCount + "). Consider cutting some for more spells.");
        } else {
            if (totalCards < 60) warnings.add("Deck has " + totalCards + "/60 cards.");
            if (landCount < 20) warnings.add("Low land count (" + landCount + "). Most 60-card decks want 22-26 lands.");
        }

        if (!categoryCounts.containsKey("Removal") || categoryCounts.getOrDefault("Removal", 0) < 3) {
            warnings.add("Deck has little or no removal. Consider adding interaction.");
        }
        if (!categoryCounts.containsKey("Card Draw") || categoryCounts.getOrDefault("Card Draw", 0) < 3) {
            warnings.add("Deck has little or no card draw. Consider adding draw engines.");
        }

        var sb = new StringBuilder();
        sb.append("Deck Analysis (").append(totalCards).append(" cards):\n\n");

        sb.append("Mana Curve:\n");
        for (var entry : manaCurve.entrySet()) {
            sb.append("  ").append(entry.getKey()).append(" MV: ").append(entry.getValue()).append(" cards\n");
        }

        sb.append("\nColor Balance:\n");
        for (var entry : colorBalance.entrySet()) {
            sb.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append(" cards\n");
        }

        sb.append("\nCategories:\n");
        for (var entry : categoryCounts.entrySet()) {
            sb.append("  ").append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
        }

        sb.append("\nLands: ").append(landCount).append("/").append(totalCards).append("\n");

        if (!warnings.isEmpty()) {
            sb.append("\nWarnings:\n");
            for (String w : warnings) {
                sb.append("  ⚠ ").append(w).append("\n");
            }
        }

        return sb.toString();
    }
}
