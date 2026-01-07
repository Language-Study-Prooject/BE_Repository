package com.mzc.secondproject.serverless.chatting.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class BedrockService {

    private static final Logger logger = LoggerFactory.getLogger(BedrockService.class);
    private static final Gson gson = new Gson();

    // Claude 3 Sonnet 모델 ID
    private static final String MODEL_ID = "anthropic.claude-3-sonnet-20240229-v1:0";

    private final BedrockRuntimeClient bedrockClient;

    public BedrockService() {
        this.bedrockClient = BedrockRuntimeClient.builder().build();
    }

    public String generateResponse(String prompt) {
        logger.info("Generating AI response for prompt");

        try {
            // Claude 3 Messages API 형식
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("anthropic_version", "bedrock-2023-05-31");
            requestBody.addProperty("max_tokens", 1024);

            JsonArray messages = new JsonArray();
            JsonObject userMessage = new JsonObject();
            userMessage.addProperty("role", "user");
            userMessage.addProperty("content", prompt);
            messages.add(userMessage);

            requestBody.add("messages", messages);

            InvokeModelRequest request = InvokeModelRequest.builder()
                    .modelId(MODEL_ID)
                    .contentType("application/json")
                    .accept("application/json")
                    .body(SdkBytes.fromUtf8String(gson.toJson(requestBody)))
                    .build();

            InvokeModelResponse response = bedrockClient.invokeModel(request);

            String responseBody = response.body().asUtf8String();
            JsonObject jsonResponse = gson.fromJson(responseBody, JsonObject.class);

            // Claude 3 응답에서 텍스트 추출
            return jsonResponse.getAsJsonArray("content")
                    .get(0).getAsJsonObject()
                    .get("text").getAsString();

        } catch (Exception e) {
            logger.error("Error calling Bedrock", e);
            throw new RuntimeException("Failed to generate AI response", e);
        }
    }
}
