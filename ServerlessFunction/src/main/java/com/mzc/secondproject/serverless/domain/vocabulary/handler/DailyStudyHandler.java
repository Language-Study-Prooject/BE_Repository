package com.mzc.secondproject.serverless.domain.vocabulary.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.mzc.secondproject.serverless.common.dto.ApiResponse;
import static com.mzc.secondproject.serverless.common.util.ResponseUtil.createResponse;
import com.mzc.secondproject.serverless.domain.vocabulary.service.DailyStudyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class DailyStudyHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(DailyStudyHandler.class);

    private final DailyStudyService dailyStudyService;

    public DailyStudyHandler() {
        this.dailyStudyService = new DailyStudyService();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        String httpMethod = request.getHttpMethod();
        String path = request.getPath();

        logger.info("Received request: {} {}", httpMethod, path);

        try {
            if ("GET".equals(httpMethod) && !path.contains("/learned")) {
                return getDailyWords(request);
            }

            if ("POST".equals(httpMethod) && path.endsWith("/learned")) {
                return markWordLearned(request);
            }

            return createResponse(404, ApiResponse.error("Not found"));

        } catch (Exception e) {
            logger.error("Error handling request", e);
            return createResponse(500, ApiResponse.error("Internal server error: " + e.getMessage()));
        }
    }

    private APIGatewayProxyResponseEvent getDailyWords(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        Map<String, String> queryParams = request.getQueryStringParameters();
        String userId = pathParams != null ? pathParams.get("userId") : null;

        if (userId == null) {
            return createResponse(400, ApiResponse.error("userId is required"));
        }

        String level = queryParams != null ? queryParams.get("level") : null;

        try {
            DailyStudyService.DailyStudyResult result = dailyStudyService.getDailyWords(userId, level);

            Map<String, Object> response = new HashMap<>();
            response.put("dailyStudy", result.dailyStudy());
            response.put("newWords", result.newWords());
            response.put("reviewWords", result.reviewWords());
            response.put("progress", result.progress());

            return createResponse(200, ApiResponse.success("Daily words retrieved", response));
        } catch (IllegalArgumentException e) {
            return createResponse(400, ApiResponse.error(e.getMessage()));
        }
    }

    private APIGatewayProxyResponseEvent markWordLearned(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        String userId = pathParams != null ? pathParams.get("userId") : null;
        String wordId = pathParams != null ? pathParams.get("wordId") : null;

        if (userId == null || wordId == null) {
            return createResponse(400, ApiResponse.error("userId and wordId are required"));
        }

        try {
            Map<String, Object> progress = dailyStudyService.markWordLearned(userId, wordId);
            return createResponse(200, ApiResponse.success("Word marked as learned", progress));
        } catch (IllegalStateException e) {
            return createResponse(404, ApiResponse.error(e.getMessage()));
        }
    }
}
