package com.mzc.secondproject.serverless.domain.vocabulary.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.mzc.secondproject.serverless.common.dto.ApiResponse;
import com.mzc.secondproject.serverless.common.validation.RequestValidator;
import com.mzc.secondproject.serverless.common.validation.ValidationResult;
import com.mzc.secondproject.serverless.domain.vocabulary.dto.request.UpdateUserWordRequest;
import com.mzc.secondproject.serverless.domain.vocabulary.dto.request.UpdateUserWordTagRequest;
import com.mzc.secondproject.serverless.common.router.HandlerRouter;
import com.mzc.secondproject.serverless.common.router.Route;
import com.mzc.secondproject.serverless.common.util.ResponseUtil;
import static com.mzc.secondproject.serverless.common.util.ResponseUtil.createResponse;
import com.mzc.secondproject.serverless.domain.vocabulary.model.UserWord;
import com.mzc.secondproject.serverless.domain.vocabulary.service.UserWordCommandService;
import com.mzc.secondproject.serverless.domain.vocabulary.service.UserWordQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class UserWordHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(UserWordHandler.class);

    private final UserWordCommandService commandService;
    private final UserWordQueryService queryService;
    private final HandlerRouter router;

    public UserWordHandler() {
        this.commandService = new UserWordCommandService();
        this.queryService = new UserWordQueryService();
        this.router = initRouter();
    }

    private HandlerRouter initRouter() {
        return new HandlerRouter().addRoutes(
                Route.get("/users/{userId}/wrong-answers", this::getWrongAnswers),
                Route.get("/users/{userId}/words", this::getUserWords),
                Route.get("/users/{userId}/words/{wordId}", this::getUserWord),
                Route.put("/users/{userId}/words/{wordId}/tag", this::updateUserWordTag),
                Route.put("/users/{userId}/words/{wordId}", this::updateUserWord)
        );
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        logger.info("Received request: {} {}", request.getHttpMethod(), request.getPath());
        return router.route(request);
    }

    private APIGatewayProxyResponseEvent getUserWords(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        Map<String, String> queryParams = request.getQueryStringParameters();

        String userId = pathParams != null ? pathParams.get("userId") : null;
        String status = queryParams != null ? queryParams.get("status") : null;
        String cursor = queryParams != null ? queryParams.get("cursor") : null;
        String bookmarked = queryParams != null ? queryParams.get("bookmarked") : null;
        String incorrectOnly = queryParams != null ? queryParams.get("incorrectOnly") : null;

        if (userId == null) {
            return createResponse(400, ApiResponse.fail("userId is required"));
        }

        int limit = 20;
        if (queryParams != null && queryParams.get("limit") != null) {
            limit = Math.min(Integer.parseInt(queryParams.get("limit")), 50);
        }

        UserWordQueryService.UserWordsResult result = queryService.getUserWords(userId, status, bookmarked, incorrectOnly, limit, cursor);

        Map<String, Object> response = new HashMap<>();
        response.put("userWords", result.userWords());
        response.put("nextCursor", result.nextCursor());
        response.put("hasMore", result.hasMore());

        return createResponse(200, ApiResponse.ok("User words retrieved", response));
    }

    private APIGatewayProxyResponseEvent getUserWord(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        String userId = pathParams != null ? pathParams.get("userId") : null;
        String wordId = pathParams != null ? pathParams.get("wordId") : null;

        if (userId == null || wordId == null) {
            return createResponse(400, ApiResponse.fail("userId and wordId are required"));
        }

        Optional<UserWord> optUserWord = queryService.getUserWord(userId, wordId);
        if (optUserWord.isEmpty()) {
            return createResponse(404, ApiResponse.fail("UserWord not found"));
        }

        return createResponse(200, ApiResponse.ok("UserWord retrieved", optUserWord.get()));
    }

    private APIGatewayProxyResponseEvent updateUserWord(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        String userId = pathParams != null ? pathParams.get("userId") : null;
        String wordId = pathParams != null ? pathParams.get("wordId") : null;

        if (userId == null || wordId == null) {
            return createResponse(400, ApiResponse.fail("userId and wordId are required"));
        }

        String body = request.getBody();
        UpdateUserWordRequest req = ResponseUtil.gson().fromJson(body, UpdateUserWordRequest.class);

        if (req.getIsCorrect() == null) {
            return createResponse(400, ApiResponse.fail("isCorrect is required"));
        }

        UserWord userWord = commandService.updateUserWord(userId, wordId, req.getIsCorrect());
        return createResponse(200, ApiResponse.ok("UserWord updated", userWord));
    }

    private APIGatewayProxyResponseEvent updateUserWordTag(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        String userId = pathParams != null ? pathParams.get("userId") : null;
        String wordId = pathParams != null ? pathParams.get("wordId") : null;

        if (userId == null || wordId == null) {
            return createResponse(400, ApiResponse.fail("userId and wordId are required"));
        }

        String body = request.getBody();
        UpdateUserWordTagRequest req = ResponseUtil.gson().fromJson(body, UpdateUserWordTagRequest.class);

        UserWord userWord = commandService.updateUserWordTag(userId, wordId, req.getBookmarked(), req.getFavorite(), req.getDifficulty());
        return createResponse(200, ApiResponse.ok("Tag updated", userWord));
    }

    private APIGatewayProxyResponseEvent getWrongAnswers(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        Map<String, String> queryParams = request.getQueryStringParameters();

        String userId = pathParams != null ? pathParams.get("userId") : null;
        String cursor = queryParams != null ? queryParams.get("cursor") : null;

        ValidationResult validation = RequestValidator.create()
                .requireNotEmpty(userId, "userId")
                .build();

        if (validation.isInvalid()) {
            return createResponse(400, ApiResponse.fail(validation.getErrorMessage().orElse("Validation failed")));
        }

        int limit = parseIntParam(queryParams, "limit", 20, 1, 50);
        int minCount = parseIntParam(queryParams, "minCount", 1, 1, 100);

        UserWordQueryService.UserWordsResult result = queryService.getWrongAnswers(userId, minCount, limit, cursor);

        Map<String, Object> response = new HashMap<>();
        response.put("wrongAnswers", result.userWords());
        response.put("nextCursor", result.nextCursor());
        response.put("hasMore", result.hasMore());

        return createResponse(200, ApiResponse.ok("Wrong answers retrieved", response));
    }

    private int parseIntParam(Map<String, String> params, String key, int defaultValue, int min, int max) {
        if (params == null || params.get(key) == null) {
            return defaultValue;
        }
        try {
            int value = Integer.parseInt(params.get(key));
            return Math.max(min, Math.min(value, max));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
