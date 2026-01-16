package com.mzc.secondproject.serverless.domain.vocabulary.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.reflect.TypeToken;
import com.mzc.secondproject.serverless.common.exception.CommonErrorCode;
import com.mzc.secondproject.serverless.common.router.HandlerRouter;
import com.mzc.secondproject.serverless.common.router.Route;
import com.mzc.secondproject.serverless.common.util.ResponseGenerator;
import com.mzc.secondproject.serverless.common.validation.BeanValidator;
import com.mzc.secondproject.serverless.domain.vocabulary.dto.request.UpdateUserWordRequest;
import com.mzc.secondproject.serverless.domain.vocabulary.dto.request.UpdateUserWordTagRequest;
import com.mzc.secondproject.serverless.domain.vocabulary.exception.VocabularyErrorCode;
import com.mzc.secondproject.serverless.domain.vocabulary.model.UserWord;
import com.mzc.secondproject.serverless.domain.vocabulary.service.UserWordCommandService;
import com.mzc.secondproject.serverless.domain.vocabulary.service.UserWordQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class UserWordHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
	
	private static final Logger logger = LoggerFactory.getLogger(UserWordHandler.class);
	
	private final UserWordCommandService commandService;
	private final UserWordQueryService queryService;
	private final HandlerRouter router;
	
	public UserWordHandler() {
		this.commandService = new UserWordCommandService();
		this.queryService = new UserWordQueryService();
		this.router = initRouter();
	}
	
	private HandlerRouter initRouter() {
		return new HandlerRouter().addRoutes(
				Route.getAuth("/wrong-answers", this::getWrongAnswers),
				Route.getAuth("/user-words", this::getUserWords),
				Route.getAuth("/user-words/{wordId}", this::getUserWord),
				Route.patchAuth("/user-words/{wordId}/tag", this::updateUserWordTag),
				Route.patchAuth("/user-words/{wordId}/status", this::updateWordStatus),
				Route.putAuth("/user-words/{wordId}", this::updateUserWord)
		);
	}
	
	@Override
	public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
		logger.info("Received request: {} {}", request.getHttpMethod(), request.getPath());
		return router.route(request);
	}
	
	private APIGatewayProxyResponseEvent getUserWords(APIGatewayProxyRequestEvent request, String userId) {
		Map<String, String> queryParams = request.getQueryStringParameters();
		
		String status = queryParams != null ? queryParams.get("status") : null;
		String cursor = queryParams != null ? queryParams.get("cursor") : null;
		String bookmarked = queryParams != null ? queryParams.get("bookmarked") : null;
		String incorrectOnly = queryParams != null ? queryParams.get("incorrectOnly") : null;
		
		int limit = 20;
		if (queryParams != null && queryParams.get("limit") != null) {
			limit = Math.min(Integer.parseInt(queryParams.get("limit")), 50);
		}
		
		UserWordQueryService.UserWordsResult result = queryService.getUserWords(userId, status, bookmarked, incorrectOnly, limit, cursor);
		
		Map<String, Object> response = new HashMap<>();
		response.put("userWords", result.userWords());
		response.put("nextCursor", result.nextCursor());
		response.put("hasMore", result.hasMore());
		
		return ResponseGenerator.ok("User words retrieved", response);
	}
	
	private APIGatewayProxyResponseEvent getUserWord(APIGatewayProxyRequestEvent request, String userId) {
		String wordId = request.getPathParameters().get("wordId");
		
		Optional<UserWord> optUserWord = queryService.getUserWord(userId, wordId);
		if (optUserWord.isEmpty()) {
			return ResponseGenerator.fail(VocabularyErrorCode.USER_WORD_NOT_FOUND);
		}
		
		return ResponseGenerator.ok("UserWord retrieved", optUserWord.get());
	}
	
	private APIGatewayProxyResponseEvent updateUserWord(APIGatewayProxyRequestEvent request, String userId) {
		String wordId = request.getPathParameters().get("wordId");
		UpdateUserWordRequest req = ResponseGenerator.gson().fromJson(request.getBody(), UpdateUserWordRequest.class);
		
		return BeanValidator.validateAndExecute(req, dto -> {
			UserWord userWord = commandService.updateUserWord(userId, wordId, dto.getIsCorrect());
			return ResponseGenerator.ok("UserWord updated", userWord);
		});
	}
	
	private APIGatewayProxyResponseEvent updateUserWordTag(APIGatewayProxyRequestEvent request, String userId) {
		String wordId = request.getPathParameters().get("wordId");
		UpdateUserWordTagRequest req = ResponseGenerator.gson().fromJson(request.getBody(), UpdateUserWordTagRequest.class);
		
		UserWord userWord = commandService.updateUserWordTag(userId, wordId, req.getBookmarked(), req.getFavorite(), req.getDifficulty());
		return ResponseGenerator.ok("Tag updated", userWord);
	}
	
	private APIGatewayProxyResponseEvent updateWordStatus(APIGatewayProxyRequestEvent request, String userId) {
		String wordId = request.getPathParameters().get("wordId");
		
		Map<String, String> body = ResponseGenerator.gson().fromJson(request.getBody(),
				new TypeToken<Map<String, String>>() {
				}.getType());
		
		String status = body != null ? body.get("status") : null;
		if (status == null || status.isEmpty()) {
			return ResponseGenerator.fail(CommonErrorCode.REQUIRED_FIELD_MISSING);
		}
		
		try {
			UserWord userWord = commandService.updateWordStatus(userId, wordId, status);
			return ResponseGenerator.ok("Word status updated", userWord);
		} catch (IllegalArgumentException e) {
			return ResponseGenerator.fail(VocabularyErrorCode.INVALID_WORD_STATUS);
		}
	}
	
	private APIGatewayProxyResponseEvent getWrongAnswers(APIGatewayProxyRequestEvent request, String userId) {
		Map<String, String> queryParams = request.getQueryStringParameters();
		String cursor = queryParams != null ? queryParams.get("cursor") : null;
		
		int limit = parseIntParam(queryParams, "limit", 20, 1, 50);
		int minCount = parseIntParam(queryParams, "minCount", 1, 1, 100);
		
		UserWordQueryService.UserWordsResult result = queryService.getWrongAnswers(userId, minCount, limit, cursor);
		
		Map<String, Object> response = new HashMap<>();
		response.put("wrongAnswers", result.userWords());
		response.put("nextCursor", result.nextCursor());
		response.put("hasMore", result.hasMore());
		
		return ResponseGenerator.ok("Wrong answers retrieved", response);
	}
	
	private int parseIntParam(Map<String, String> params, String key, int defaultValue, int min, int max) {
		if (params == null || params.get(key) == null) {
			return defaultValue;
		}
		try {
			int value = Integer.parseInt(params.get(key));
			return Math.max(min, Math.min(value, max));
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}
}
