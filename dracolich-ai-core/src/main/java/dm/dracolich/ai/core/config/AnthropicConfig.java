package dm.dracolich.ai.core.config;

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
}
