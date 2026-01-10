package com.mzc.secondproject.serverless.domain.chatting.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.mzc.secondproject.serverless.common.exception.CommonErrorCode;
import com.mzc.secondproject.serverless.common.util.ResponseGenerator;
import com.mzc.secondproject.serverless.domain.chatting.service.BedrockService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class ChatAIHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(ChatAIHandler.class);

    private final BedrockService bedrockService;

    public ChatAIHandler() {
        this.bedrockService = new BedrockService();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        logger.info("Received AI generation request");

        try {
            if (!"POST".equals(request.getHttpMethod())) {
                return ResponseGenerator.fail(CommonErrorCode.METHOD_NOT_ALLOWED);
            }

            String body = request.getBody();
            // TODO: Parse request and generate AI response using Bedrock

            String aiResponse = bedrockService.generateResponse("Hello, how can I help you?");

            return ResponseGenerator.ok("AI response generated", Map.of("response", aiResponse));

        } catch (Exception e) {
            logger.error("Error generating AI response", e);
            return ResponseGenerator.fail(CommonErrorCode.INTERNAL_SERVER_ERROR);
        }
    }
}
