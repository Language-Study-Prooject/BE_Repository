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
import com.mzc.secondproject.serverless.domain.vocabulary.dto.request.StartTestRequest;
import com.mzc.secondproject.serverless.domain.vocabulary.dto.request.SubmitTestRequest;
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
	
	/**
	 * 기본 생성자 (Lambda에서 사용)
	 */
	public TestHandler() {
		this(new TestCommandService(), new TestQueryService());
	}

	/**
	 * 의존성 주입 생성자 (테스트 용이성)
	 */
	public TestHandler(TestCommandService commandService, TestQueryService queryService) {
		this.commandService = commandService;
		this.queryService = queryService;
		this.router = initRouter();
	}
	
	private HandlerRouter initRouter() {
		return new HandlerRouter().addRoutes(
				Route.postAuth("/test/start", this::startTest),
				Route.postAuth("/test/submit", this::submitAnswer),
				Route.getAuth("/test/results/{testId}", this::getTestResultDetail),
				Route.getAuth("/test/results", this::getTestResults),
				Route.getAuth("/test/tested-words", this::getTestedWords)
		);
	}
	
	@Override
	public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
		logger.info("Received request: {} {}", request.getHttpMethod(), request.getPath());
		return router.route(request);
	}
	
	private APIGatewayProxyResponseEvent startTest(APIGatewayProxyRequestEvent request, String userId) {
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
	
	private APIGatewayProxyResponseEvent submitAnswer(APIGatewayProxyRequestEvent request, String userId) {
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
	
	private APIGatewayProxyResponseEvent getTestResults(APIGatewayProxyRequestEvent request, String userId) {
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
	
	private APIGatewayProxyResponseEvent getTestResultDetail(APIGatewayProxyRequestEvent request, String userId) {
		String testId = request.getPathParameters().get("testId");
		
		var optDetail = queryService.getTestResultDetail(userId, testId);
		if (optDetail.isEmpty()) {
			return ResponseGenerator.fail(CommonErrorCode.RESOURCE_NOT_FOUND);
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
	
	private APIGatewayProxyResponseEvent getTestedWords(APIGatewayProxyRequestEvent request, String userId) {
		Map<String, String> queryParams = request.getQueryStringParameters();
		
		int recentTests = 10;
		int limit = 50;
		
		if (queryParams != null) {
			if (queryParams.get("recentTests") != null) {
				recentTests = Math.min(Integer.parseInt(queryParams.get("recentTests")), 50);
			}
			if (queryParams.get("limit") != null) {
				limit = Math.min(Integer.parseInt(queryParams.get("limit")), 100);
			}
		}
		
		TestQueryService.TestedWordsResult result = queryService.getTestedWords(userId, recentTests, limit);
		
		Map<String, Object> response = new HashMap<>();
		response.put("testedWords", result.words());
		response.put("totalCount", result.totalCount());
		
		return ResponseGenerator.ok("Tested words retrieved", response);
	}
}
