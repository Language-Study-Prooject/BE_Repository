package com.mzc.secondproject.serverless.domain.vocabulary.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.mzc.secondproject.serverless.common.router.HandlerRouter;
import com.mzc.secondproject.serverless.common.router.Route;
import com.mzc.secondproject.serverless.common.util.ResponseGenerator;
import com.mzc.secondproject.serverless.domain.vocabulary.exception.VocabularyErrorCode;
import com.mzc.secondproject.serverless.domain.vocabulary.service.DailyStudyCommandService;
import com.mzc.secondproject.serverless.domain.vocabulary.service.DailyStudyQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class DailyStudyHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
	
	private static final Logger logger = LoggerFactory.getLogger(DailyStudyHandler.class);
	
	private final DailyStudyCommandService commandService;
	private final DailyStudyQueryService queryService;
	private final HandlerRouter router;
	
	public DailyStudyHandler() {
		this.commandService = new DailyStudyCommandService();
		this.queryService = new DailyStudyQueryService();
		this.router = initRouter();
	}
	
	private HandlerRouter initRouter() {
		return new HandlerRouter().addRoutes(
				Route.postAuth("/daily/words/{wordId}/learned", this::markWordLearned),
				Route.getAuth("/daily", this::getDailyWords)
		);
	}
	
	@Override
	public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
		logger.info("Received request: {} {}", request.getHttpMethod(), request.getPath());
		return router.route(request);
	}
	
	private APIGatewayProxyResponseEvent getDailyWords(APIGatewayProxyRequestEvent request, String userId) {
		Map<String, String> queryParams = request.getQueryStringParameters();
		
		String date = queryParams != null ? queryParams.get("date") : null;
		String level = queryParams != null ? queryParams.get("level") : null;
		
		// 특정 날짜 조회 (읽기 전용)
		if (date != null && !date.isEmpty()) {
			return getDailyStudyByDate(userId, date);
		}
		
		// 오늘 날짜 (없으면 생성)
		DailyStudyCommandService.DailyStudyResult result = commandService.getDailyWords(userId, level);
		
		Map<String, Object> response = new HashMap<>();
		response.put("dailyStudy", result.dailyStudy());
		response.put("newWords", result.newWords());
		response.put("reviewWords", result.reviewWords());
		response.put("progress", result.progress());
		
		return ResponseGenerator.ok("Daily words retrieved", response);
	}
	
	private APIGatewayProxyResponseEvent getDailyStudyByDate(String userId, String date) {
		var optDailyStudy = queryService.getDailyStudy(userId, date);
		
		if (optDailyStudy.isEmpty()) {
			return ResponseGenerator.fail(VocabularyErrorCode.DAILY_STUDY_NOT_FOUND);
		}
		
		var dailyStudy = optDailyStudy.get();
		var newWords = queryService.getWordDetails(dailyStudy.getNewWordIds());
		var reviewWords = queryService.getWordDetails(dailyStudy.getReviewWordIds());
		var progress = queryService.calculateProgress(dailyStudy);
		
		Map<String, Object> response = new HashMap<>();
		response.put("dailyStudy", dailyStudy);
		response.put("newWords", newWords);
		response.put("reviewWords", reviewWords);
		response.put("progress", progress);
		
		return ResponseGenerator.ok("Daily study retrieved for " + date, response);
	}
	
	private APIGatewayProxyResponseEvent markWordLearned(APIGatewayProxyRequestEvent request, String userId) {
		String wordId = request.getPathParameters().get("wordId");
		
		Map<String, Object> progress = commandService.markWordLearned(userId, wordId);
		return ResponseGenerator.ok("Word marked as learned", progress);
	}
}
