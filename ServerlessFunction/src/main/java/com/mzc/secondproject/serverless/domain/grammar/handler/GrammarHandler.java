package com.mzc.secondproject.serverless.domain.grammar.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.mzc.secondproject.serverless.common.router.HandlerRouter;
import com.mzc.secondproject.serverless.common.router.Route;
import com.mzc.secondproject.serverless.common.util.ResponseGenerator;
import com.mzc.secondproject.serverless.common.validation.BeanValidator;
import com.mzc.secondproject.serverless.domain.grammar.dto.request.GrammarCheckRequest;
import com.mzc.secondproject.serverless.domain.grammar.dto.response.GrammarCheckResponse;
import com.mzc.secondproject.serverless.domain.grammar.service.GrammarCheckService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

public class GrammarHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

	private static final Logger logger = LoggerFactory.getLogger(GrammarHandler.class);

	private final GrammarCheckService grammarCheckService;
	private final HandlerRouter router;

	public GrammarHandler() {
		this.grammarCheckService = new GrammarCheckService();
		this.router = initRouter();
	}

	private HandlerRouter initRouter() {
		return new HandlerRouter().addRoutes(
				Route.postAuth("/grammar/check", this::checkGrammar)
		);
	}

	@Override
	public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
		logger.info("Received request: {} {}", request.getHttpMethod(), request.getPath());
		return router.route(request);
	}

	private APIGatewayProxyResponseEvent checkGrammar(APIGatewayProxyRequestEvent request, String userId) {
		GrammarCheckRequest req = ResponseGenerator.gson().fromJson(request.getBody(), GrammarCheckRequest.class);

		return BeanValidator.validateAndExecute(req, dto -> {
			GrammarCheckResponse result = grammarCheckService.checkGrammar(dto);

			Map<String, Object> response = new HashMap<>();
			response.put("originalSentence", result.getOriginalSentence());
			response.put("correctedSentence", result.getCorrectedSentence());
			response.put("score", result.getScore());
			response.put("isCorrect", result.getIsCorrect());
			response.put("errors", result.getErrors());
			response.put("feedback", result.getFeedback());

			return ResponseGenerator.ok("Grammar checked successfully", response);
		});
	}
}
