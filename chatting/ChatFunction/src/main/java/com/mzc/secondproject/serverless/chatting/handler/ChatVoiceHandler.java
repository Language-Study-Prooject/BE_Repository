package com.mzc.secondproject.serverless.chatting.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mzc.secondproject.serverless.chatting.dto.ApiResponse;
import com.mzc.secondproject.serverless.chatting.service.PollyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class ChatVoiceHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(ChatVoiceHandler.class);
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final PollyService pollyService;

    public ChatVoiceHandler() {
        this.pollyService = new PollyService();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        logger.info("Received voice synthesis request");

        try {
            if (!"POST".equals(request.getHttpMethod())) {
                return createResponse(405, ApiResponse.error("Method not allowed"));
            }

            String body = request.getBody();
            Map<String, String> requestBody = gson.fromJson(body, Map.class);
            String text = requestBody.get("text");

            if (text == null || text.isEmpty()) {
                return createResponse(400, ApiResponse.error("text is required"));
            }

            String audioUrl = pollyService.synthesizeSpeech(text);

            return createResponse(200, ApiResponse.success("Speech synthesized", Map.of("audioUrl", audioUrl)));

        } catch (Exception e) {
            logger.error("Error synthesizing speech", e);
            return createResponse(500, ApiResponse.error("Internal server error: " + e.getMessage()));
        }
    }

    private APIGatewayProxyResponseEvent createResponse(int statusCode, Object body) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(Map.of(
                        "Content-Type", "application/json",
                        "Access-Control-Allow-Origin", "*",
                        "Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS",
                        "Access-Control-Allow-Headers", "Content-Type,Authorization"
                ))
                .withBody(gson.toJson(body));
    }
}
