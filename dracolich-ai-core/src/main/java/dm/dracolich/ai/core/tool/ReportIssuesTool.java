package dm.dracolich.ai.core.tool;

import dm.dracolich.ai.datasource.entity.SessionEntity;
import dm.dracolich.ai.datasource.repository.SessionRepository;
import dm.dracolich.ai.dto.IssueDto;
import dm.dracolich.ai.dto.enums.IssueSeverity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReportIssuesTool {

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();

    private final SessionRepository sessionRepository;

    @Tool(description = "Formally report deck analysis findings. Call this whenever you analyze a deck's structure. " +
            "Pass a JSON array where each element has: topic (short stable key like \"mana_curve\", " +
            "\"color_balance\", \"removal_count\", \"card_draw\", \"land_count\", \"win_conditions\"), " +
            "severity (ERROR for illegal/broken, WARNING for concerning but legal, INFO for advisory tips), " +
            "and reason (1-2 sentences explaining the finding). " +
            "Use this BEFORE writing your text summary. Do NOT repeat issue details in your text response — " +
            "the frontend will render them visually.")
    public String reportIssues(
            @ToolParam(description = "The session ID") String sessionId,
            @ToolParam(description = "JSON array of issues, e.g. [{\"topic\":\"mana_curve\",\"severity\":\"WARNING\",\"reason\":\"Average CMC of 4.5 is high for an aggro deck\"}]") String issuesJson
    ) {
        List<Issue> issues;
        try {
            issues = MAPPER.readValue(issuesJson, new com.fasterxml.jackson.core.type.TypeReference<List<Issue>>() {});
        } catch (Exception e) {
            log.warn("Failed to parse issues JSON: {}", e.getMessage());
            return "Error: invalid issues format. Send a JSON array of objects with topic, severity, reason.";
        }

        try {
            SessionEntity session = sessionRepository.findById(sessionId).toFuture().join();
            if (session == null) {
                return "Session not found: " + sessionId;
            }

            List<IssueDto> dtos = issues.stream()
                    .map(i -> IssueDto.builder()
                            .topic(i.topic())
                            .severity(parseSeverity(i.severity()))
                            .reason(i.reason())
                            .build())
                    .toList();

            session.setAnalysisIssues(dtos);
            sessionRepository.save(session).toFuture().join();

            return "Reported " + issues.size() + " issue(s): " +
                    issues.stream().map(Issue::topic).collect(Collectors.joining(", "));
        } catch (Exception e) {
            log.error("Failed to persist analysis issues for session {}: {}", sessionId, e.getMessage());
            return "Failed to save issues. The topics were: " +
                    issues.stream().map(Issue::topic).collect(Collectors.joining(", "));
        }
    }

    private static IssueSeverity parseSeverity(String raw) {
        if (raw == null) return IssueSeverity.INFO;
        try {
            return IssueSeverity.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return IssueSeverity.INFO;
        }
    }

    public record Issue(
            String topic,
            String severity,
            String reason
    ) {}
}
