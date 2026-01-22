package com.mzc.secondproject.serverless.domain.vocabulary.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.mzc.secondproject.serverless.common.dto.PaginatedResult;
import com.mzc.secondproject.serverless.common.exception.CommonErrorCode;
import com.mzc.secondproject.serverless.common.router.HandlerRouter;
import com.mzc.secondproject.serverless.common.router.Route;
import com.mzc.secondproject.serverless.common.util.ResponseGenerator;
import com.mzc.secondproject.serverless.common.validation.BeanValidator;
import com.mzc.secondproject.serverless.domain.vocabulary.dto.request.BatchGetWordsRequest;
import com.mzc.secondproject.serverless.domain.vocabulary.dto.request.CreateWordRequest;
import com.mzc.secondproject.serverless.domain.vocabulary.dto.request.CreateWordsBatchRequest;
import com.mzc.secondproject.serverless.domain.vocabulary.exception.VocabularyErrorCode;
import com.mzc.secondproject.serverless.domain.vocabulary.model.Word;
import com.mzc.secondproject.serverless.domain.vocabulary.service.WordCommandService;
import com.mzc.secondproject.serverless.domain.vocabulary.service.WordQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class WordHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
	
	private static final Logger logger = LoggerFactory.getLogger(WordHandler.class);
	
	private final WordCommandService commandService;
	private final WordQueryService queryService;
	private final HandlerRouter router;
	
	/**
	 * 기본 생성자 (Lambda에서 사용)
	 */
	public WordHandler() {
		this(new WordCommandService(), new WordQueryService());
	}
	
	/**
	 * 의존성 주입 생성자 (테스트 용이성)
	 */
	public WordHandler(WordCommandService commandService, WordQueryService queryService) {
		this.commandService = commandService;
		this.queryService = queryService;
		this.router = initRouter();
	}
	
	private HandlerRouter initRouter() {
		return new HandlerRouter().addRoutes(
				Route.post("/words/batch/get", this::getWordsBatch),
				Route.post("/words/batch", this::createWordsBatch),
				Route.get("/words/search", this::searchWords).requireQueryParams("q"),
				Route.post("/words", this::createWord),
				Route.get("/words", this::getWords),
				Route.get("/words/{wordId}", this::getWord),
				Route.put("/words/{wordId}", this::updateWord),
				Route.delete("/words/{wordId}", this::deleteWord)
		);
	}
	
	@Override
	public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
		logger.info("Received request: {} {}", request.getHttpMethod(), request.getPath());
		return router.route(request);
	}
	
	private APIGatewayProxyResponseEvent createWord(APIGatewayProxyRequestEvent request) {
		String body = request.getBody();
		CreateWordRequest req = ResponseGenerator.gson().fromJson(body, CreateWordRequest.class);
		
		return BeanValidator.validateAndExecute(req, dto -> {
			String level = dto.getLevel() != null ? dto.getLevel() : "BEGINNER";
			String category = dto.getCategory() != null ? dto.getCategory() : "DAILY";
			
			Word word = commandService.createWord(dto.getEnglish(), dto.getKorean(), dto.getExample(), level, category);
			return ResponseGenerator.created("Word created", word);
		});
	}
	
	private APIGatewayProxyResponseEvent getWords(APIGatewayProxyRequestEvent request) {
		Map<String, String> queryParams = request.getQueryStringParameters();
		
		String level = queryParams != null ? queryParams.get("level") : null;
		String category = queryParams != null ? queryParams.get("category") : null;
		String cursor = queryParams != null ? queryParams.get("cursor") : null;
		
		int limit = 20;
		if (queryParams != null && queryParams.get("limit") != null) {
			limit = Math.min(Integer.parseInt(queryParams.get("limit")), 50);
		}
		
		PaginatedResult<Word> wordPage = queryService.getWords(level, category, limit, cursor);
		
		Map<String, Object> result = new HashMap<>();
		result.put("words", wordPage.items());
		result.put("nextCursor", wordPage.nextCursor());
		result.put("hasMore", wordPage.hasMore());
		
		return ResponseGenerator.ok("Words retrieved", result);
	}
	
	private APIGatewayProxyResponseEvent getWord(APIGatewayProxyRequestEvent request) {
		String wordId = request.getPathParameters().get("wordId");
		
		Optional<Word> optWord = queryService.getWord(wordId);
		if (optWord.isEmpty()) {
			return ResponseGenerator.fail(VocabularyErrorCode.WORD_NOT_FOUND);
		}
		
		return ResponseGenerator.ok("Word retrieved", optWord.get());
	}
	
	private APIGatewayProxyResponseEvent updateWord(APIGatewayProxyRequestEvent request) {
		String wordId = request.getPathParameters().get("wordId");
		String body = request.getBody();
		Map<String, Object> requestBody = ResponseGenerator.gson().fromJson(body, Map.class);
		
		Word word = commandService.updateWord(wordId, requestBody);
		return ResponseGenerator.ok("Word updated", word);
	}
	
	private APIGatewayProxyResponseEvent deleteWord(APIGatewayProxyRequestEvent request) {
		String wordId = request.getPathParameters().get("wordId");
		commandService.deleteWord(wordId);
		return ResponseGenerator.ok("Word deleted", null);
	}
	
	private APIGatewayProxyResponseEvent createWordsBatch(APIGatewayProxyRequestEvent request) {
		String body = request.getBody();
		CreateWordsBatchRequest req = ResponseGenerator.gson().fromJson(body, CreateWordsBatchRequest.class);
		
		return BeanValidator.validateAndExecute(req, dto -> {
			WordCommandService.BatchResult result = commandService.createWordsBatch(dto.getWords());
			
			Map<String, Object> response = new HashMap<>();
			response.put("successCount", result.successCount());
			response.put("failCount", result.failCount());
			response.put("totalRequested", result.totalRequested());
			
			return ResponseGenerator.created("Batch completed", response);
		});
	}
	
	private APIGatewayProxyResponseEvent searchWords(APIGatewayProxyRequestEvent request) {
		Map<String, String> queryParams = request.getQueryStringParameters();
		
		String query = queryParams.get("q");
		String cursor = queryParams.get("cursor");
		
		int limit = 20;
		if (queryParams.get("limit") != null) {
			limit = Math.min(Integer.parseInt(queryParams.get("limit")), 50);
		}
		
		PaginatedResult<Word> wordPage = queryService.searchWords(query, limit, cursor);
		
		Map<String, Object> result = new HashMap<>();
		result.put("words", wordPage.items());
		result.put("query", query);
		result.put("nextCursor", wordPage.nextCursor());
		result.put("hasMore", wordPage.hasMore());
		
		return ResponseGenerator.ok("Search completed", result);
	}
	
	private APIGatewayProxyResponseEvent getWordsBatch(APIGatewayProxyRequestEvent request) {
		String body = request.getBody();
		BatchGetWordsRequest req = ResponseGenerator.gson().fromJson(body, BatchGetWordsRequest.class);
		
		return BeanValidator.validateAndExecute(req, dto -> {
			if (dto.getWordIds().size() > 100) {
				return ResponseGenerator.fail(CommonErrorCode.VALUE_OUT_OF_RANGE, "Maximum 100 wordIds allowed per request");
			}
			
			List<Word> words = queryService.getWordsByIds(dto.getWordIds());
			
			Map<String, Object> result = new HashMap<>();
			result.put("words", words);
			result.put("requestedCount", dto.getWordIds().size());
			result.put("retrievedCount", words.size());
			
			return ResponseGenerator.ok("Words retrieved", result);
		});
	}
}
