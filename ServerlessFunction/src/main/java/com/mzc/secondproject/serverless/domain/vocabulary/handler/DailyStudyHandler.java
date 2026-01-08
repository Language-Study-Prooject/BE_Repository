package com.mzc.secondproject.serverless.domain.vocabulary.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.mzc.secondproject.serverless.common.dto.ApiResponse;
import com.mzc.secondproject.serverless.common.router.HandlerRouter;
import com.mzc.secondproject.serverless.common.router.Route;
import static com.mzc.secondproject.serverless.common.util.ResponseUtil.createResponse;
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
                Route.post("/daily/{userId}/words/{wordId}/learned", this::markWordLearned),
                Route.get("/daily/{userId}", this::getDailyWords)
        );
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        logger.info("Received request: {} {}", request.getHttpMethod(), request.getPath());
        return router.route(request);
    }

    private APIGatewayProxyResponseEvent getDailyWords(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        Map<String, String> queryParams = request.getQueryStringParameters();
        String userId = pathParams != null ? pathParams.get("userId") : null;

        if (userId == null) {
            return createResponse(400, ApiResponse.error("userId is required"));
        }

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

        return createResponse(200, ApiResponse.success("Daily words retrieved", response));
    }

    private APIGatewayProxyResponseEvent getDailyStudyByDate(String userId, String date) {
        var optDailyStudy = queryService.getDailyStudy(userId, date);

        if (optDailyStudy.isEmpty()) {
            return createResponse(404, ApiResponse.error("No daily study found for date: " + date));
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

        return createResponse(200, ApiResponse.success("Daily study retrieved for " + date, response));
    }

    private APIGatewayProxyResponseEvent markWordLearned(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        String userId = pathParams != null ? pathParams.get("userId") : null;
        String wordId = pathParams != null ? pathParams.get("wordId") : null;

        if (userId == null || wordId == null) {
            return createResponse(400, ApiResponse.error("userId and wordId are required"));
        }

        Map<String, Object> progress = commandService.markWordLearned(userId, wordId);
        return createResponse(200, ApiResponse.success("Word marked as learned", progress));
    }
}
