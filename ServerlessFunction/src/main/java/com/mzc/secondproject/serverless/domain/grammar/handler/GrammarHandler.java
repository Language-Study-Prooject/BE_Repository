package com.mzc.secondproject.serverless.domain.grammar.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.mzc.secondproject.serverless.common.router.HandlerRouter;
import com.mzc.secondproject.serverless.common.router.Route;
import com.mzc.secondproject.serverless.common.util.ResponseGenerator;
import com.mzc.secondproject.serverless.common.validation.BeanValidator;
import com.mzc.secondproject.serverless.domain.grammar.dto.request.ConversationRequest;
import com.mzc.secondproject.serverless.domain.grammar.dto.request.GrammarCheckRequest;
import com.mzc.secondproject.serverless.domain.grammar.dto.response.ConversationResponse;
import com.mzc.secondproject.serverless.domain.grammar.dto.response.GrammarCheckResponse;
import com.mzc.secondproject.serverless.domain.grammar.service.GrammarCheckService;
import com.mzc.secondproject.serverless.domain.grammar.service.GrammarConversationService;
import com.mzc.secondproject.serverless.domain.grammar.service.GrammarSessionQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class GrammarHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
	
	private static final Logger logger = LoggerFactory.getLogger(GrammarHandler.class);
	
	private final GrammarCheckService grammarCheckService;
	private final GrammarConversationService conversationService;
	private final GrammarSessionQueryService sessionQueryService;
	private final HandlerRouter router;
	
	/**
	 * 기본 생성자 (Lambda에서 사용)
	 */
	public GrammarHandler() {
		this(new GrammarCheckService(), new GrammarConversationService(), new GrammarSessionQueryService());
	}
	
	/**
	 * 의존성 주입 생성자 (테스트 용이성)
	 */
	public GrammarHandler(GrammarCheckService grammarCheckService, GrammarConversationService conversationService,
	                      GrammarSessionQueryService sessionQueryService) {
		this.grammarCheckService = grammarCheckService;
		this.conversationService = conversationService;
		this.sessionQueryService = sessionQueryService;
		this.router = initRouter();
	}
	
	private HandlerRouter initRouter() {
		return new HandlerRouter().addRoutes(
				Route.postAuth("/grammar/check", this::checkGrammar),
				Route.postAuth("/grammar/conversation", this::conversation),
				Route.getAuth("/grammar/sessions", this::getSessions),
				Route.getAuth("/grammar/sessions/{sessionId}", this::getSessionDetail),
				Route.deleteAuth("/grammar/sessions/{sessionId}", this::deleteSession)
		);
	}
	
	@Override
	public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
		logger.info("Received request: {} {}", request.getHttpMethod(), request.getPath());
		return router.route(request);
	}
	
	private APIGatewayProxyResponseEvent checkGrammar(APIGatewayProxyRequestEvent request, String userId) {
		GrammarCheckRequest req = ResponseGenerator.gson().fromJson(request.getBody(), GrammarCheckRequest.class);
		
		return BeanValidator.validateAndExecute(req, dto -> {
			GrammarCheckResponse result = grammarCheckService.checkGrammar(dto);
			
			Map<String, Object> response = new HashMap<>();
			response.put("originalSentence", result.getOriginalSentence());
			response.put("correctedSentence", result.getCorrectedSentence());
			response.put("score", result.getScore());
			response.put("isCorrect", result.getIsCorrect());
			response.put("errors", result.getErrors());
			response.put("feedback", result.getFeedback());
			
			return ResponseGenerator.ok("Grammar checked successfully", response);
		});
	}
	
	private APIGatewayProxyResponseEvent conversation(APIGatewayProxyRequestEvent request, String userId) {
		ConversationRequest req = ResponseGenerator.gson().fromJson(request.getBody(), ConversationRequest.class);
		req.setUserId(userId);  // JWT에서 추출한 userId 설정
		
		return BeanValidator.validateAndExecute(req, dto -> {
			ConversationResponse result = conversationService.chat(dto);
			
			Map<String, Object> response = new HashMap<>();
			response.put("sessionId", result.getSessionId());
			response.put("grammarCheck", result.getGrammarCheck());
			response.put("aiResponse", result.getAiResponse());
			response.put("conversationTip", result.getConversationTip());
			
			return ResponseGenerator.ok("Conversation generated successfully", response);
		});
	}
	
	private APIGatewayProxyResponseEvent getSessions(APIGatewayProxyRequestEvent request, String userId) {
		Map<String, String> queryParams = request.getQueryStringParameters();
		String cursor = queryParams != null ? queryParams.get("cursor") : null;
		
		int limit = 10;
		if (queryParams != null && queryParams.get("limit") != null) {
			limit = Math.min(Integer.parseInt(queryParams.get("limit")), 50);
		}
		
		var result = sessionQueryService.getSessions(userId, limit, cursor);
		
		Map<String, Object> response = new HashMap<>();
		response.put("sessions", result.items());
		response.put("nextCursor", result.nextCursor());
		response.put("hasMore", result.hasMore());
		
		return ResponseGenerator.ok("Sessions retrieved successfully", response);
	}
	
	private APIGatewayProxyResponseEvent getSessionDetail(APIGatewayProxyRequestEvent request, String userId) {
		String sessionId = request.getPathParameters().get("sessionId");
		
		Map<String, String> queryParams = request.getQueryStringParameters();
		int messageLimit = 50;
		if (queryParams != null && queryParams.get("messageLimit") != null) {
			messageLimit = Math.min(Integer.parseInt(queryParams.get("messageLimit")), 100);
		}
		
		var detail = sessionQueryService.getSessionDetail(userId, sessionId, messageLimit);
		
		Map<String, Object> response = new HashMap<>();
		response.put("session", detail.session());
		response.put("messages", detail.messages());
		
		return ResponseGenerator.ok("Session detail retrieved successfully", response);
	}
	
	private APIGatewayProxyResponseEvent deleteSession(APIGatewayProxyRequestEvent request, String userId) {
		String sessionId = request.getPathParameters().get("sessionId");
		
		sessionQueryService.deleteSession(userId, sessionId);
		
		return ResponseGenerator.ok("Session deleted successfully", null);
	}
}
