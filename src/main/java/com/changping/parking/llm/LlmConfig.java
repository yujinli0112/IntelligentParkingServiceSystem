package com.changping.parking.llm;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Slf4j
@Configuration
public class LlmConfig {

    @Value("${llm.deepseek.baseUrl:https://api.deepseek.com}")
    private String deepseekBaseUrl;

    @Value("${llm.deepseek.apiKey:}")
    private String deepseekApiKey;

    @Value("${llm.deepseek.model:deepseek-chat}")
    private String deepseekModel;

    @Value("${llm.openai.baseUrl:https://api.openai.com/v1}")
    private String openaiBaseUrl;

    @Value("${llm.openai.apiKey:}")
    private String openaiApiKey;

    @Value("${llm.openai.model:gpt-3.5-turbo}")
    private String openaiModel;

    @Bean
    @ConditionalOnProperty(name = "llm.provider", havingValue = "deepseek")
    public ChatLanguageModel deepseekChatModel() {
        log.info("初始化 DeepSeek LLM 模型: {}", deepseekModel);
        return OpenAiChatModel.builder()
                .baseUrl(deepseekBaseUrl)
                .apiKey(deepseekApiKey)
                .modelName(deepseekModel)
                .temperature(0.3)
                .timeout(Duration.ofSeconds(30))
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "llm.provider", havingValue = "openai")
    public ChatLanguageModel openAiChatModel() {
        log.info("初始化 OpenAI LLM 模型: {}", openaiModel);
        return OpenAiChatModel.builder()
                .baseUrl(openaiBaseUrl)
                .apiKey(openaiApiKey)
                .modelName(openaiModel)
                .temperature(0.3)
                .timeout(Duration.ofSeconds(30))
                .build();
    }
}
