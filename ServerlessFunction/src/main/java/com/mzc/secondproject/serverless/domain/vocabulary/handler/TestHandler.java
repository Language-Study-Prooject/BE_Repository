package com.mzc.secondproject.serverless.domain.vocabulary.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.mzc.secondproject.serverless.common.dto.ApiResponse;
import com.mzc.secondproject.serverless.common.dto.PaginatedResult;
import com.mzc.secondproject.serverless.domain.vocabulary.dto.request.StartTestRequest;
import com.mzc.secondproject.serverless.domain.vocabulary.dto.request.SubmitTestRequest;
import com.mzc.secondproject.serverless.common.router.HandlerRouter;
import com.mzc.secondproject.serverless.common.router.Route;
import com.mzc.secondproject.serverless.common.util.ResponseUtil;
import static com.mzc.secondproject.serverless.common.util.ResponseUtil.createResponse;
import com.mzc.secondproject.serverless.domain.vocabulary.model.TestResult;
import com.mzc.secondproject.serverless.domain.vocabulary.service.TestCommandService;
import com.mzc.secondproject.serverless.domain.vocabulary.service.TestQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(TestHandler.class);

    private final TestCommandService commandService;
    private final TestQueryService queryService;
    private final HandlerRouter router;

    public TestHandler() {
        this.commandService = new TestCommandService();
        this.queryService = new TestQueryService();
        this.router = initRouter();
    }

    private HandlerRouter initRouter() {
        return new HandlerRouter().addRoutes(
                Route.post("/tests/{userId}/start", this::startTest),
                Route.post("/tests/{userId}/submit", this::submitAnswer),
                Route.get("/tests/{userId}/results", this::getTestResults)
        );
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        logger.info("Received request: {} {}", request.getHttpMethod(), request.getPath());
        return router.route(request);
    }

    private APIGatewayProxyResponseEvent startTest(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        String userId = pathParams != null ? pathParams.get("userId") : null;

        if (userId == null) {
            return createResponse(400, ApiResponse.error("userId is required"));
        }

        String body = request.getBody();
        StartTestRequest req = ResponseUtil.gson().fromJson(body, StartTestRequest.class);
        String testType = req != null && req.getTestType() != null ? req.getTestType() : "DAILY";

        TestCommandService.StartTestResult result = commandService.startTest(userId, testType);

        Map<String, Object> response = new HashMap<>();
        response.put("testId", result.testId());
        response.put("testType", result.testType());
        response.put("questions", result.questions());
        response.put("totalQuestions", result.totalQuestions());
        response.put("startedAt", result.startedAt());

        return createResponse(200, ApiResponse.success("Test started", response));
    }

    private APIGatewayProxyResponseEvent submitAnswer(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        String userId = pathParams != null ? pathParams.get("userId") : null;

        if (userId == null) {
            return createResponse(400, ApiResponse.error("userId is required"));
        }

        String body = request.getBody();
        SubmitTestRequest req = ResponseUtil.gson().fromJson(body, SubmitTestRequest.class);

        if (req.getTestId() == null || req.getAnswers() == null) {
            return createResponse(400, ApiResponse.error("testId and answers are required"));
        }

        String testType = req.getTestType() != null ? req.getTestType() : "DAILY";

        TestCommandService.SubmitTestResult result = commandService.submitTest(userId, req.getTestId(), testType, req.getAnswers(), req.getStartedAt());

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

        PaginatedResult<TestResult> resultPage = queryService.getTestResults(userId, limit, cursor);

        Map<String, Object> result = new HashMap<>();
        result.put("testResults", resultPage.getItems());
        result.put("nextCursor", resultPage.getNextCursor());
        result.put("hasMore", resultPage.hasMore());

        return createResponse(200, ApiResponse.success("Test results retrieved", result));
    }
}
