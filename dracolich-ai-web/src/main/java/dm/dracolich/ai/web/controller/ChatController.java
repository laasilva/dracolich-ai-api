package dm.dracolich.ai.web.controller;

import dm.dracolich.ai.core.service.AgentService;
import dm.dracolich.ai.dto.ChatMessageDto;
import dm.dracolich.ai.dto.records.ChatRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("agent/chat")
@Tag(name = "Chat")
@RequiredArgsConstructor
public class ChatController {

    private final AgentService agentService;

    @Operation(summary = "Send a message", description = "Sends a message to the AI agent and streams the response via SSE")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "AI response streamed successfully",
                    content = @Content(schema = @Schema(implementation = String.class)))
    })
    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chat(@Valid @RequestBody ChatRequest request) {
        return agentService.chat(request.sessionId(), request.message());
    }

    @Operation(summary = "Get chat history", description = "Returns all messages for a session")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Chat history fetched successfully",
                    content = @Content(schema = @Schema(implementation = ChatMessageDto.class)))
    })
    @GetMapping(path = "/history/{sessionId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Flux<ChatMessageDto> getChatHistory(@PathVariable String sessionId) {
        return agentService.getChatHistory(sessionId);
    }
}
