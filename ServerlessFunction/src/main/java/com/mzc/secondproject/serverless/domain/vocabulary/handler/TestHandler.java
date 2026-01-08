package com.mzc.secondproject.serverless.domain.vocabulary.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.mzc.secondproject.serverless.common.dto.ApiResponse;
import com.mzc.secondproject.serverless.common.dto.PaginatedResult;
import com.mzc.secondproject.serverless.common.util.ResponseUtil;
import static com.mzc.secondproject.serverless.common.util.ResponseUtil.createResponse;
import com.mzc.secondproject.serverless.domain.vocabulary.model.TestResult;
import com.mzc.secondproject.serverless.domain.vocabulary.service.TestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(TestHandler.class);

    private final TestService testService;

    public TestHandler() {
        this.testService = new TestService();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        String httpMethod = request.getHttpMethod();
        String path = request.getPath();

        logger.info("Received request: {} {}", httpMethod, path);

        try {
            if ("POST".equals(httpMethod) && path.endsWith("/start")) {
                return startTest(request);
            }

            if ("POST".equals(httpMethod) && path.endsWith("/submit")) {
                return submitAnswer(request);
            }

            if ("GET".equals(httpMethod) && path.endsWith("/results")) {
                return getTestResults(request);
            }

            return createResponse(404, ApiResponse.error("Not found"));

        } catch (Exception e) {
            logger.error("Error handling request", e);
            return createResponse(500, ApiResponse.error("Internal server error: " + e.getMessage()));
        }
    }

    private APIGatewayProxyResponseEvent startTest(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        String userId = pathParams != null ? pathParams.get("userId") : null;

        if (userId == null) {
            return createResponse(400, ApiResponse.error("userId is required"));
        }

        String body = request.getBody();
        Map<String, Object> requestBody = ResponseUtil.gson().fromJson(body, Map.class);
        String testType = (String) requestBody.getOrDefault("testType", "DAILY");

        try {
            TestService.StartTestResult result = testService.startTest(userId, testType);

            Map<String, Object> response = new HashMap<>();
            response.put("testId", result.testId());
            response.put("testType", result.testType());
            response.put("questions", result.questions());
            response.put("totalQuestions", result.totalQuestions());
            response.put("startedAt", result.startedAt());

            return createResponse(200, ApiResponse.success("Test started", response));
        } catch (IllegalStateException e) {
            return createResponse(404, ApiResponse.error(e.getMessage()));
        }
    }

    @SuppressWarnings("unchecked")
    private APIGatewayProxyResponseEvent submitAnswer(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        String userId = pathParams != null ? pathParams.get("userId") : null;

        if (userId == null) {
            return createResponse(400, ApiResponse.error("userId is required"));
        }

        String body = request.getBody();
        Map<String, Object> requestBody = ResponseUtil.gson().fromJson(body, Map.class);

        String testId = (String) requestBody.get("testId");
        String testType = (String) requestBody.getOrDefault("testType", "DAILY");
        List<Map<String, Object>> answers = (List<Map<String, Object>>) requestBody.get("answers");
        String startedAt = (String) requestBody.get("startedAt");

        if (testId == null || answers == null) {
            return createResponse(400, ApiResponse.error("testId and answers are required"));
        }

        TestService.SubmitTestResult result = testService.submitTest(userId, testId, testType, answers, startedAt);

        Map<String, Object> response = new HashMap<>();
        response.put("testId", result.testId());
        response.put("testType", result.testType());
        response.put("totalQuestions", result.totalQuestions());
        response.put("correctCount", result.correctCount());
        response.put("incorrectCount", result.incorrectCount());
        response.put("successRate", result.successRate());
        response.put("results", result.results());

        return createResponse(200, ApiResponse.success("Test submitted", response));
    }

    private APIGatewayProxyResponseEvent getTestResults(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        Map<String, String> queryParams = request.getQueryStringParameters();

        String userId = pathParams != null ? pathParams.get("userId") : null;
        String cursor = queryParams != null ? queryParams.get("cursor") : null;

        if (userId == null) {
            return createResponse(400, ApiResponse.error("userId is required"));
        }

        int limit = 10;
        if (queryParams != null && queryParams.get("limit") != null) {
            limit = Math.min(Integer.parseInt(queryParams.get("limit")), 50);
        }

        PaginatedResult<TestResult> resultPage = testService.getTestResults(userId, limit, cursor);

        Map<String, Object> result = new HashMap<>();
        result.put("testResults", resultPage.getItems());
        result.put("nextCursor", resultPage.getNextCursor());
        result.put("hasMore", resultPage.hasMore());

        return createResponse(200, ApiResponse.success("Test results retrieved", result));
    }
}
