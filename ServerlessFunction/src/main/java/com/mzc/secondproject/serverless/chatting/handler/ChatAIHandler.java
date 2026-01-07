package com.mzc.secondproject.serverless.chatting.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mzc.secondproject.serverless.common.dto.ApiResponse;
import com.mzc.secondproject.serverless.chatting.service.BedrockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class ChatAIHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(ChatAIHandler.class);
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final BedrockService bedrockService;

    public ChatAIHandler() {
        this.bedrockService = new BedrockService();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        logger.info("Received AI generation request");

        try {
            if (!"POST".equals(request.getHttpMethod())) {
                return createResponse(405, ApiResponse.error("Method not allowed"));
            }

            String body = request.getBody();
            // TODO: Parse request and generate AI response using Bedrock

            String aiResponse = bedrockService.generateResponse("Hello, how can I help you?");

            return createResponse(200, ApiResponse.success("AI response generated", Map.of("response", aiResponse)));

        } catch (Exception e) {
            logger.error("Error generating AI response", e);
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
