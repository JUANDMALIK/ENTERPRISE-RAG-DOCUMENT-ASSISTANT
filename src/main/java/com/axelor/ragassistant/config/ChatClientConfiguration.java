package com.axelor.ragassistant.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RagRetrievalProperties.class)
public class ChatClientConfiguration {

    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }
}
