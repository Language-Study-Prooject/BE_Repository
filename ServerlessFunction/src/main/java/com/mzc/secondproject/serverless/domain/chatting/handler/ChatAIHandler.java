package com.mzc.secondproject.serverless.domain.chatting.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.mzc.secondproject.serverless.common.exception.CommonErrorCode;
import com.mzc.secondproject.serverless.common.util.ResponseGenerator;
import com.mzc.secondproject.serverless.domain.chatting.enums.ChatLevel;
import com.mzc.secondproject.serverless.domain.chatting.factory.AiChatResponseFactory;
import com.mzc.secondproject.serverless.domain.chatting.factory.ChatResponse;
import com.mzc.secondproject.serverless.domain.chatting.factory.ChatResponseFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class ChatAIHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
	
	private static final Logger logger = LoggerFactory.getLogger(ChatAIHandler.class);
	private static final Gson gson = new Gson();
	
	private final ChatResponseFactory chatResponseFactory;
	
	public ChatAIHandler() {
		this.chatResponseFactory = new AiChatResponseFactory();
	}
	
	public ChatAIHandler(ChatResponseFactory chatResponseFactory) {
		this.chatResponseFactory = chatResponseFactory;
	}
	
	@Override
	public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
		logger.info("Received AI generation request");
		
		try {
			if (!"POST".equals(request.getHttpMethod())) {
				return ResponseGenerator.fail(CommonErrorCode.METHOD_NOT_ALLOWED);
			}
			
			String body = request.getBody();
			ChatRequest chatRequest = gson.fromJson(body, ChatRequest.class);
			
			String userMessage = chatRequest.message != null ? chatRequest.message : "Hello";
			ChatLevel level = ChatLevel.fromStringOrDefault(chatRequest.level, ChatLevel.BEGINNER);
			
			ChatResponse aiResponse = chatResponseFactory.create(userMessage, level, chatRequest.conversationHistory);
			
			return ResponseGenerator.ok("AI response generated", Map.of(
					"response", aiResponse.content(),
					"modelId", aiResponse.modelId(),
					"processingTimeMs", aiResponse.processingTimeMs()
			));
			
		} catch (Exception e) {
			logger.error("Error generating AI response", e);
			return ResponseGenerator.fail(CommonErrorCode.INTERNAL_SERVER_ERROR);
		}
	}
	
	private static class ChatRequest {
		String message;
		String level;
		String conversationHistory;
	}
}
