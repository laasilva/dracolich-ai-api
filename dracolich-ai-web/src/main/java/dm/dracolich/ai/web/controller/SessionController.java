package dm.dracolich.ai.web.controller;

import dm.dracolich.ai.core.service.AgentService;
import dm.dracolich.ai.dto.SessionDto;
import dm.dracolich.ai.dto.records.SessionCreateRequest;
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
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("agent/session")
@Tag(name = "Session")
@RequiredArgsConstructor
public class SessionController {

    private final AgentService agentService;

    @Operation(summary = "Create a new session", description = "Creates a BUILD or ANALYSIS deck building session")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Session created",
                    content = @Content(schema = @Schema(implementation = SessionDto.class)))
    })
    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<SessionDto> createSession(@Valid @RequestBody SessionCreateRequest request) {
        return agentService.createSession(request);
    }

    @Operation(summary = "List sessions by user", description = "Returns paginated sessions for a given user ID. Returns empty if no user_id provided.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Sessions listed",
                    content = @Content(schema = @Schema(implementation = SessionDto.class)))
    })
    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public Flux<SessionDto> listSessions(
            @RequestParam(name = "user_id", required = false) String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return agentService.listSessions(userId, page, size);
    }

    @Operation(summary = "Get session state", description = "Returns session with deck list and metadata")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Session fetched",
                    content = @Content(schema = @Schema(implementation = SessionDto.class)))
    })
    @GetMapping(path = "/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<SessionDto> getSession(@PathVariable String id) {
        return agentService.getSession(id);
    }

    @Operation(summary = "Delete a session", description = "Permanently deletes a session and its chat history")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Session deleted")
    })
    @DeleteMapping(path = "/{id}")
    public Mono<Void> deleteSession(@PathVariable String id) {
        return agentService.deleteSession(id);
    }
}
