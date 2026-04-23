package dm.dracolich.ai.dto.error;

import dm.dracolich.forge.error.ErrorCode;
import lombok.Getter;

@Getter
public enum ErrorCodes implements ErrorCode {
    DMD023("DMD023", "%s not found: %s"),
    DMD024("DMD024", "Validation error: %s"),
    DMD025("DMD025", "Card search failed: %s"),
    DMD026("DMD026", "Deck analysis failed: %s"),
    DMD027("DMD027", "Failed to persist card suggestions: %s"),
    DMD028("DMD028", "Chat streaming error: %s"),
    DMD029("DMD029", "MTG Library API unavailable");

    private final String code;
    private final String message;

    ErrorCodes(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String format(String... args) {
        return String.format(message, (Object[]) args);
    }
}
