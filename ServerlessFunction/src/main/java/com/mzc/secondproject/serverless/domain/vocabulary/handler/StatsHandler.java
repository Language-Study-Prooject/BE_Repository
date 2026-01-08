package com.mzc.secondproject.serverless.domain.vocabulary.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.mzc.secondproject.serverless.common.dto.ApiResponse;
import com.mzc.secondproject.serverless.common.router.HandlerRouter;
import com.mzc.secondproject.serverless.common.router.Route;
import static com.mzc.secondproject.serverless.common.util.ResponseUtil.createResponse;
import com.mzc.secondproject.serverless.domain.vocabulary.service.StatsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class StatsHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(StatsHandler.class);

    private final StatsService statsService;
    private final HandlerRouter router;

    public StatsHandler() {
        this.statsService = new StatsService();
        this.router = initRouter();
    }

    private HandlerRouter initRouter() {
        return new HandlerRouter().addRoutes(
                Route.get("/stats/{userId}/weakness", this::getWeaknessAnalysis),
                Route.get("/stats/{userId}/daily", this::getDailyStats),
                Route.get("/stats/{userId}", this::getOverallStats)
        );
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        logger.info("Received request: {} {}", request.getHttpMethod(), request.getPath());
        return router.route(request);
    }

    private APIGatewayProxyResponseEvent getOverallStats(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        String userId = pathParams != null ? pathParams.get("userId") : null;

        if (userId == null) {
            return createResponse(400, ApiResponse.error("userId is required"));
        }

        Map<String, Object> stats = statsService.getOverallStats(userId);
        return createResponse(200, ApiResponse.success("Stats retrieved", stats));
    }

    private APIGatewayProxyResponseEvent getDailyStats(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        Map<String, String> queryParams = request.getQueryStringParameters();

        String userId = pathParams != null ? pathParams.get("userId") : null;
        String cursor = queryParams != null ? queryParams.get("cursor") : null;

        if (userId == null) {
            return createResponse(400, ApiResponse.error("userId is required"));
        }

        int limit = 30;
        if (queryParams != null && queryParams.get("limit") != null) {
            limit = Math.min(Integer.parseInt(queryParams.get("limit")), 90);
        }

        StatsService.DailyStatsResult dailyResult = statsService.getDailyStats(userId, limit, cursor);

        Map<String, Object> result = new HashMap<>();
        result.put("dailyStats", dailyResult.dailyStats());
        result.put("nextCursor", dailyResult.nextCursor());
        result.put("hasMore", dailyResult.hasMore());

        return createResponse(200, ApiResponse.success("Daily stats retrieved", result));
    }

    private APIGatewayProxyResponseEvent getWeaknessAnalysis(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        String userId = pathParams != null ? pathParams.get("userId") : null;

        if (userId == null) {
            return createResponse(400, ApiResponse.error("userId is required"));
        }

        Map<String, Object> analysis = statsService.getWeaknessAnalysis(userId);
        return createResponse(200, ApiResponse.success("Weakness analysis completed", analysis));
    }
}
