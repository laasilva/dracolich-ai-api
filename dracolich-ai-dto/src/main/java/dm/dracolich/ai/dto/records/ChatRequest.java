package dm.dracolich.ai.dto.records;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
        @NotBlank(message = "session_id is required") @JsonProperty("session_id") String sessionId,
        @NotBlank(message = "message is required") String message
) {}
