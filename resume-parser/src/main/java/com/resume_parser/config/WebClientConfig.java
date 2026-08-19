package com.resume_parser.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {
    @Value("${groq.api.key:}")
    private String groqApiKey;

    @Bean
    public WebClient webClient() {
        ExchangeStrategies exchangeStrategies = ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
                .build();

        WebClient.Builder builder = WebClient.builder()
                .exchangeStrategies(exchangeStrategies)
                .defaultHeader("Content-Type", "application/json");

        if (groqApiKey != null && !groqApiKey.isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + groqApiKey.trim());
        }

        return builder.build();
    }
}
