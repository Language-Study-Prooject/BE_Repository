package com.mzc.secondproject.serverless.domain.vocabulary.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.mzc.secondproject.serverless.common.dto.ApiResponse;
import com.mzc.secondproject.serverless.common.dto.PaginatedResult;
import com.mzc.secondproject.serverless.common.router.HandlerRouter;
import com.mzc.secondproject.serverless.common.router.Route;
import com.mzc.secondproject.serverless.common.util.ResponseUtil;
import com.mzc.secondproject.serverless.common.validation.RequestValidator;
import com.mzc.secondproject.serverless.common.validation.ValidationResult;
import com.mzc.secondproject.serverless.domain.vocabulary.dto.request.CreateWordGroupRequest;
import com.mzc.secondproject.serverless.domain.vocabulary.model.WordGroup;
import com.mzc.secondproject.serverless.domain.vocabulary.service.WordGroupCommandService;
import com.mzc.secondproject.serverless.domain.vocabulary.service.WordGroupQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

import static com.mzc.secondproject.serverless.common.util.ResponseUtil.createResponse;

public class WordGroupHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(WordGroupHandler.class);

    private final WordGroupCommandService commandService;
    private final WordGroupQueryService queryService;
    private final HandlerRouter router;

    public WordGroupHandler() {
        this.commandService = new WordGroupCommandService();
        this.queryService = new WordGroupQueryService();
        this.router = initRouter();
    }

    private HandlerRouter initRouter() {
        return new HandlerRouter().addRoutes(
                Route.post("/users/{userId}/groups", this::createGroup),
                Route.get("/users/{userId}/groups", this::getGroups),
                Route.get("/users/{userId}/groups/{groupId}", this::getGroupDetail),
                Route.put("/users/{userId}/groups/{groupId}", this::updateGroup),
                Route.delete("/users/{userId}/groups/{groupId}", this::deleteGroup),
                Route.post("/users/{userId}/groups/{groupId}/words/{wordId}", this::addWordToGroup),
                Route.delete("/users/{userId}/groups/{groupId}/words/{wordId}", this::removeWordFromGroup)
        );
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        logger.info("Received request: {} {}", request.getHttpMethod(), request.getPath());
        return router.route(request);
    }

    private APIGatewayProxyResponseEvent createGroup(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        String userId = pathParams != null ? pathParams.get("userId") : null;

        String body = request.getBody();
        CreateWordGroupRequest req = ResponseUtil.gson().fromJson(body, CreateWordGroupRequest.class);

        ValidationResult validation = RequestValidator.create()
                .requireNotEmpty(userId, "userId")
                .requireNotEmpty(req != null ? req.getGroupName() : null, "groupName")
                .build();

        if (validation.isInvalid()) {
            return createResponse(400, ApiResponse.fail(validation.getErrorMessage().orElse("Validation failed")));
        }

        WordGroup group = commandService.createGroup(userId, req.getGroupName(), req.getDescription());
        return createResponse(201, ApiResponse.ok("Group created", group));
    }

    private APIGatewayProxyResponseEvent getGroups(APIGatewayProxyRequestEvent request) {
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

        PaginatedResult<WordGroup> result = queryService.getGroups(userId, limit, cursor);

        Map<String, Object> response = new HashMap<>();
        response.put("groups", result.items());
        response.put("nextCursor", result.nextCursor());
        response.put("hasMore", result.hasMore());

        return createResponse(200, ApiResponse.ok("Groups retrieved", response));
    }

    private APIGatewayProxyResponseEvent getGroupDetail(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        String userId = pathParams != null ? pathParams.get("userId") : null;
        String groupId = pathParams != null ? pathParams.get("groupId") : null;

        ValidationResult validation = RequestValidator.create()
                .requireNotEmpty(userId, "userId")
                .requireNotEmpty(groupId, "groupId")
                .build();

        if (validation.isInvalid()) {
            return createResponse(400, ApiResponse.fail(validation.getErrorMessage().orElse("Validation failed")));
        }

        var optDetail = queryService.getGroupDetail(userId, groupId);
        if (optDetail.isEmpty()) {
            return createResponse(404, ApiResponse.fail("Group not found"));
        }

        var detail = optDetail.get();

        Map<String, Object> response = new HashMap<>();
        response.put("groupId", detail.group().getGroupId());
        response.put("groupName", detail.group().getGroupName());
        response.put("description", detail.group().getDescription());
        response.put("wordCount", detail.group().getWordCount());
        response.put("words", detail.words());
        response.put("createdAt", detail.group().getCreatedAt());
        response.put("updatedAt", detail.group().getUpdatedAt());

        return createResponse(200, ApiResponse.ok("Group detail retrieved", response));
    }

    private APIGatewayProxyResponseEvent updateGroup(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        String userId = pathParams != null ? pathParams.get("userId") : null;
        String groupId = pathParams != null ? pathParams.get("groupId") : null;

        ValidationResult validation = RequestValidator.create()
                .requireNotEmpty(userId, "userId")
                .requireNotEmpty(groupId, "groupId")
                .build();

        if (validation.isInvalid()) {
            return createResponse(400, ApiResponse.fail(validation.getErrorMessage().orElse("Validation failed")));
        }

        String body = request.getBody();
        CreateWordGroupRequest req = ResponseUtil.gson().fromJson(body, CreateWordGroupRequest.class);

        try {
            WordGroup group = commandService.updateGroup(userId, groupId,
                    req != null ? req.getGroupName() : null,
                    req != null ? req.getDescription() : null);
            return createResponse(200, ApiResponse.ok("Group updated", group));
        } catch (IllegalArgumentException e) {
            return createResponse(404, ApiResponse.fail(e.getMessage()));
        }
    }

    private APIGatewayProxyResponseEvent deleteGroup(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        String userId = pathParams != null ? pathParams.get("userId") : null;
        String groupId = pathParams != null ? pathParams.get("groupId") : null;

        ValidationResult validation = RequestValidator.create()
                .requireNotEmpty(userId, "userId")
                .requireNotEmpty(groupId, "groupId")
                .build();

        if (validation.isInvalid()) {
            return createResponse(400, ApiResponse.fail(validation.getErrorMessage().orElse("Validation failed")));
        }

        try {
            commandService.deleteGroup(userId, groupId);
            return createResponse(200, ApiResponse.ok("Group deleted", null));
        } catch (IllegalArgumentException e) {
            return createResponse(404, ApiResponse.fail(e.getMessage()));
        }
    }

    private APIGatewayProxyResponseEvent addWordToGroup(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        String userId = pathParams != null ? pathParams.get("userId") : null;
        String groupId = pathParams != null ? pathParams.get("groupId") : null;
        String wordId = pathParams != null ? pathParams.get("wordId") : null;

        ValidationResult validation = RequestValidator.create()
                .requireNotEmpty(userId, "userId")
                .requireNotEmpty(groupId, "groupId")
                .requireNotEmpty(wordId, "wordId")
                .build();

        if (validation.isInvalid()) {
            return createResponse(400, ApiResponse.fail(validation.getErrorMessage().orElse("Validation failed")));
        }

        try {
            WordGroup group = commandService.addWordToGroup(userId, groupId, wordId);
            return createResponse(200, ApiResponse.ok("Word added to group", group));
        } catch (IllegalArgumentException e) {
            return createResponse(404, ApiResponse.fail(e.getMessage()));
        }
    }

    private APIGatewayProxyResponseEvent removeWordFromGroup(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        String userId = pathParams != null ? pathParams.get("userId") : null;
        String groupId = pathParams != null ? pathParams.get("groupId") : null;
        String wordId = pathParams != null ? pathParams.get("wordId") : null;

        ValidationResult validation = RequestValidator.create()
                .requireNotEmpty(userId, "userId")
                .requireNotEmpty(groupId, "groupId")
                .requireNotEmpty(wordId, "wordId")
                .build();

        if (validation.isInvalid()) {
            return createResponse(400, ApiResponse.fail(validation.getErrorMessage().orElse("Validation failed")));
        }

        try {
            WordGroup group = commandService.removeWordFromGroup(userId, groupId, wordId);
            return createResponse(200, ApiResponse.ok("Word removed from group", group));
        } catch (IllegalArgumentException e) {
            return createResponse(404, ApiResponse.fail(e.getMessage()));
        }
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
