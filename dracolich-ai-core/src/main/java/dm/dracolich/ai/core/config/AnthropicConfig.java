package dm.dracolich.ai.core.config;

import dm.dracolich.ai.core.tool.CardSearchTool;
import dm.dracolich.ai.core.tool.ReportIssuesTool;
import dm.dracolich.ai.core.tool.SuggestCardsTool;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AnthropicConfig {

    @Value("${spring.ai.anthropic.api-key}")
    private String apiKey;

    @Value("${spring.ai.anthropic.chat.options.model:claude-sonnet-4-20250514}")
    private String model;

    @Value("${spring.ai.anthropic.chat.options.max-tokens:4096}")
    private int maxTokens;

    @Value("${spring.ai.anthropic.chat.options.temperature:0.7}")
    private double temperature;

    @Bean
    public AnthropicApi anthropicApi() {
        return new AnthropicApi(apiKey);
    }

    @Bean
    public AnthropicChatModel anthropicChatModel(AnthropicApi api) {
        return new AnthropicChatModel(api, AnthropicChatOptions.builder()
                .model(model)
                .maxTokens(maxTokens)
                .temperature(temperature)
                .build());
    }

    @Bean
    public ChatClient.Builder chatClientBuilder(AnthropicChatModel chatModel) {
        return ChatClient.builder(chatModel);
    }

    /**
     * Singleton ChatClient with tools registered once at startup.
     * AgentService should inject this — NOT the Builder.
     * Re-registering tools per-call on a shared Builder accumulates duplicates
     * and crashes Spring AI with "Multiple tools with the same name".
     *
     * <p>Stats (mana curve, color pie, lands, etc.) are pre-computed by deck-builder-api
     * and injected into the user prompt — no stats tool is registered, which saves
     * ~70 mtg-library-api round trips per analyze call.</p>
     */
    @Bean
    public ChatClient agentChatClient(ChatClient.Builder builder,
                                      CardSearchTool cardSearchTool,
                                      SuggestCardsTool suggestCardsTool,
                                      ReportIssuesTool reportIssuesTool) {
        return builder
                .defaultTools(cardSearchTool, suggestCardsTool, reportIssuesTool)
                .build();
    }
}
