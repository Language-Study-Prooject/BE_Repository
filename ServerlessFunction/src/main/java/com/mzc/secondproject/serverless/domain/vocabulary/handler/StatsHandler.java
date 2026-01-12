package com.mzc.secondproject.serverless.domain.vocabulary.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.mzc.secondproject.serverless.common.router.HandlerRouter;
import com.mzc.secondproject.serverless.common.router.Route;
import com.mzc.secondproject.serverless.common.util.ResponseGenerator;
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
                Route.getAuth("/stats/weakness", this::getWeaknessAnalysis),
                Route.getAuth("/stats/daily", this::getDailyStats),
                Route.getAuth("/stats", this::getOverallStats)
        );
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        logger.info("Received request: {} {}", request.getHttpMethod(), request.getPath());
        return router.route(request);
    }

    private APIGatewayProxyResponseEvent getOverallStats(APIGatewayProxyRequestEvent request, String userId) {
        Map<String, Object> stats = statsService.getOverallStats(userId);
        return ResponseGenerator.ok("Stats retrieved", stats);
    }

    private APIGatewayProxyResponseEvent getDailyStats(APIGatewayProxyRequestEvent request, String userId) {
        Map<String, String> queryParams = request.getQueryStringParameters();
        String cursor = queryParams != null ? queryParams.get("cursor") : null;

        int limit = 30;
        if (queryParams != null && queryParams.get("limit") != null) {
            limit = Math.min(Integer.parseInt(queryParams.get("limit")), 90);
        }

        StatsService.DailyStatsResult dailyResult = statsService.getDailyStats(userId, limit, cursor);

        Map<String, Object> result = new HashMap<>();
        result.put("dailyStats", dailyResult.dailyStats());
        result.put("nextCursor", dailyResult.nextCursor());
        result.put("hasMore", dailyResult.hasMore());

        return ResponseGenerator.ok("Daily stats retrieved", result);
    }

    private APIGatewayProxyResponseEvent getWeaknessAnalysis(APIGatewayProxyRequestEvent request, String userId) {
        Map<String, Object> analysis = statsService.getWeaknessAnalysis(userId);
        return ResponseGenerator.ok("Weakness analysis completed", analysis);
    }
}
