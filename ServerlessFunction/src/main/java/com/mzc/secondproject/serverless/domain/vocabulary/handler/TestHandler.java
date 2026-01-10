package com.mzc.secondproject.serverless.domain.vocabulary.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.mzc.secondproject.serverless.common.dto.PaginatedResult;
import com.mzc.secondproject.serverless.common.exception.CommonErrorCode;
import com.mzc.secondproject.serverless.common.validation.BeanValidator;
import com.mzc.secondproject.serverless.domain.vocabulary.dto.request.StartTestRequest;
import com.mzc.secondproject.serverless.domain.vocabulary.dto.request.SubmitTestRequest;
import com.mzc.secondproject.serverless.common.router.HandlerRouter;
import com.mzc.secondproject.serverless.common.router.Route;
import com.mzc.secondproject.serverless.common.util.ResponseGenerator;
import com.mzc.secondproject.serverless.domain.vocabulary.model.TestResult;
import com.mzc.secondproject.serverless.domain.vocabulary.service.TestCommandService;
import com.mzc.secondproject.serverless.domain.vocabulary.service.TestQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
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
                Route.post("/test/{userId}/start", this::startTest),
                Route.post("/test/{userId}/submit", this::submitAnswer),
                Route.get("/test/{userId}/results/{testId}", this::getTestResultDetail),
                Route.get("/test/{userId}/results", this::getTestResults)
        );
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        logger.info("Received request: {} {}", request.getHttpMethod(), request.getPath());
        return router.route(request);
    }

    private APIGatewayProxyResponseEvent startTest(APIGatewayProxyRequestEvent request) {
        String userId = request.getPathParameters().get("userId");
        StartTestRequest req = ResponseGenerator.gson().fromJson(request.getBody(), StartTestRequest.class);
        String testType = req != null && req.getTestType() != null ? req.getTestType() : "DAILY";

        TestCommandService.StartTestResult result = commandService.startTest(userId, testType);

        Map<String, Object> response = new HashMap<>();
        response.put("testId", result.testId());
        response.put("testType", result.testType());
        response.put("questions", result.questions());
        response.put("totalQuestions", result.totalQuestions());
        response.put("startedAt", result.startedAt());

        return ResponseGenerator.ok("Test started", response);
    }

    private APIGatewayProxyResponseEvent submitAnswer(APIGatewayProxyRequestEvent request) {
        String userId = request.getPathParameters().get("userId");
        SubmitTestRequest req = ResponseGenerator.gson().fromJson(request.getBody(), SubmitTestRequest.class);

        return BeanValidator.validateAndExecute(req, dto -> {
            String testType = dto.getTestType() != null ? dto.getTestType() : "DAILY";

            TestCommandService.SubmitTestResult result = commandService.submitTest(
                    userId, dto.getTestId(), testType, dto.getAnswers(), dto.getStartedAt());

            Map<String, Object> response = new HashMap<>();
            response.put("testId", result.testId());
            response.put("testType", result.testType());
            response.put("totalQuestions", result.totalQuestions());
            response.put("correctCount", result.correctCount());
            response.put("incorrectCount", result.incorrectCount());
            response.put("successRate", result.successRate());
            response.put("results", result.results());

            return ResponseGenerator.ok("Test submitted", response);
        });
    }

    private APIGatewayProxyResponseEvent getTestResults(APIGatewayProxyRequestEvent request) {
        String userId = request.getPathParameters().get("userId");
        Map<String, String> queryParams = request.getQueryStringParameters();
        String cursor = queryParams != null ? queryParams.get("cursor") : null;

        int limit = 10;
        if (queryParams != null && queryParams.get("limit") != null) {
            limit = Math.min(Integer.parseInt(queryParams.get("limit")), 50);
        }

        PaginatedResult<TestResult> resultPage = queryService.getTestResults(userId, limit, cursor);

        Map<String, Object> result = new HashMap<>();
        result.put("testResults", resultPage.items());
        result.put("nextCursor", resultPage.nextCursor());
        result.put("hasMore", resultPage.hasMore());

        return ResponseGenerator.ok("Test results retrieved", result);
    }

    private APIGatewayProxyResponseEvent getTestResultDetail(APIGatewayProxyRequestEvent request) {
        String userId = request.getPathParameters().get("userId");
        String testId = request.getPathParameters().get("testId");

        var optDetail = queryService.getTestResultDetail(userId, testId);
        if (optDetail.isEmpty()) {
            return ResponseGenerator.fail(CommonErrorCode.RESOURCE_NOT_FOUND,"Test result not found");
        }

        var detail = optDetail.get();

        Map<String, Object> result = new HashMap<>();
        result.put("testId", detail.testResult().getTestId());
        result.put("testType", detail.testResult().getTestType());
        result.put("totalQuestions", detail.testResult().getTotalQuestions());
        result.put("correctAnswers", detail.testResult().getCorrectAnswers());
        result.put("incorrectAnswers", detail.testResult().getIncorrectAnswers());
        result.put("successRate", detail.testResult().getSuccessRate());
        result.put("incorrectWords", detail.incorrectWords());
        result.put("startedAt", detail.testResult().getStartedAt());
        result.put("completedAt", detail.testResult().getCompletedAt());

        return ResponseGenerator.ok("Test result detail retrieved", result);
    }
}
