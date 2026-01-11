package com.mzc.secondproject.serverless.domain.chatting.factory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mzc.secondproject.serverless.common.config.AwsClients;
import com.mzc.secondproject.serverless.domain.chatting.enums.ChatLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

/**
 * AWS Bedrock 기반 AI 채팅 응답 Factory.
 * ChatLevel에 따라 프롬프트와 응답 스타일을 조정한다.
 */
public class AiChatResponseFactory implements ChatResponseFactory {

    private static final Logger logger = LoggerFactory.getLogger(AiChatResponseFactory.class);
    private static final Gson gson = new Gson();

    private static final String MODEL_ID = "anthropic.claude-3-sonnet-20240229-v1:0";
    private static final int MAX_TOKENS = 1024;

    @Override
    public ChatResponse create(String userMessage, ChatLevel level, String conversationHistory) {
        logger.info("Generating AI response: level={}", level.name());

        long startTime = System.currentTimeMillis();

        try {
            String systemPrompt = buildSystemPrompt(level);
            String fullPrompt = buildFullPrompt(userMessage, conversationHistory, systemPrompt);

            JsonObject requestBody = buildRequestBody(fullPrompt, systemPrompt);

            InvokeModelRequest request = InvokeModelRequest.builder()
                    .modelId(MODEL_ID)
                    .contentType("application/json")
                    .accept("application/json")
                    .body(SdkBytes.fromUtf8String(gson.toJson(requestBody)))
                    .build();

            InvokeModelResponse response = AwsClients.bedrock().invokeModel(request);

            String responseBody = response.body().asUtf8String();
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);

            String content = jsonResponse.getAsJsonArray("content")
                    .get(0).getAsJsonObject()
                    .get("text").getAsString();

            long processingTime = System.currentTimeMillis() - startTime;
            logger.info("AI response generated in {}ms", processingTime);

            return ChatResponse.of(content, MODEL_ID, processingTime);

        } catch (Exception e) {
            logger.error("Error generating AI response", e);
            throw new RuntimeException("Failed to generate AI response", e);
        }
    }

    private String buildSystemPrompt(ChatLevel level) {
        return switch (level) {
            case BEGINNER -> """
                You are a friendly English tutor for beginners.
                - Use simple vocabulary and short sentences
                - Explain grammar points when needed
                - Provide Korean translations for difficult words
                - Be encouraging and patient
                - Speak slowly and clearly
                """;
            case INTERMEDIATE -> """
                You are an English conversation partner for intermediate learners.
                - Use natural, everyday English
                - Introduce new vocabulary with context clues
                - Gently correct mistakes
                - Encourage more complex sentence structures
                """;
            case ADVANCED -> """
                You are an advanced English conversation partner.
                - Use sophisticated vocabulary and idioms
                - Discuss complex topics naturally
                - Challenge the learner with nuanced expressions
                - Provide minimal corrections, focus on fluency
                """;
        };
    }

    private String buildFullPrompt(String userMessage, String conversationHistory, String systemPrompt) {
        StringBuilder prompt = new StringBuilder();

        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            prompt.append("Previous conversation:\n");
            prompt.append(conversationHistory);
            prompt.append("\n\n");
        }

        prompt.append("User: ").append(userMessage);

        return prompt.toString();
    }

    private JsonObject buildRequestBody(String userPrompt, String systemPrompt) {
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("anthropic_version", "bedrock-2023-05-31");
        requestBody.addProperty("max_tokens", MAX_TOKENS);
        requestBody.addProperty("system", systemPrompt);

        JsonArray messages = new JsonArray();
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", userPrompt);
        messages.add(userMessage);

        requestBody.add("messages", messages);

        return requestBody;
    }
}
