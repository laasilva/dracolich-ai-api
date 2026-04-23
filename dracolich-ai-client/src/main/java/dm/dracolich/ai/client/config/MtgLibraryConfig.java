package dm.dracolich.ai.client.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.codec.json.JacksonJsonDecoder;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class MtgLibraryConfig {

    @Value("${dracolich.mtg-library.api.base-url}")
    private String baseUrl;

    @Bean
    public WebClient mtgLibraryWebClient() {
        var mapper = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        return WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Accept", "application/json")
                .codecs(configurer -> {
                    configurer.defaultCodecs().maxInMemorySize(4 * 1024 * 1024);
                    configurer.defaultCodecs().jacksonJsonDecoder(new JacksonJsonDecoder(mapper));
                })
                .build();
    }
}
