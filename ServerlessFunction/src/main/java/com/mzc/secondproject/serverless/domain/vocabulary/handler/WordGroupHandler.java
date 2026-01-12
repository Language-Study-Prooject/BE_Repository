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
import com.mzc.secondproject.serverless.domain.vocabulary.dto.request.CreateWordGroupRequest;
import com.mzc.secondproject.serverless.domain.vocabulary.model.WordGroup;
import com.mzc.secondproject.serverless.domain.vocabulary.service.WordGroupCommandService;
import com.mzc.secondproject.serverless.domain.vocabulary.service.WordGroupQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

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
                Route.postAuth("/groups", this::createGroup),
                Route.getAuth("/groups", this::getGroups),
                Route.getAuth("/groups/{groupId}", this::getGroupDetail),
                Route.putAuth("/groups/{groupId}", this::updateGroup),
                Route.deleteAuth("/groups/{groupId}", this::deleteGroup),
                Route.postAuth("/groups/{groupId}/words/{wordId}", this::addWordToGroup),
                Route.deleteAuth("/groups/{groupId}/words/{wordId}", this::removeWordFromGroup)
        );
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        logger.info("Received request: {} {}", request.getHttpMethod(), request.getPath());
        return router.route(request);
    }

    private APIGatewayProxyResponseEvent createGroup(APIGatewayProxyRequestEvent request, String userId) {
        CreateWordGroupRequest req = ResponseGenerator.gson().fromJson(request.getBody(), CreateWordGroupRequest.class);

        return BeanValidator.validateAndExecute(req, dto -> {
            WordGroup group = commandService.createGroup(userId, dto.getGroupName(), dto.getDescription());
            return ResponseGenerator.created("Group created", group);
        });
    }

    private APIGatewayProxyResponseEvent getGroups(APIGatewayProxyRequestEvent request, String userId) {
        Map<String, String> queryParams = request.getQueryStringParameters();
        String cursor = queryParams != null ? queryParams.get("cursor") : null;

        int limit = parseIntParam(queryParams, "limit", 20, 1, 50);

        PaginatedResult<WordGroup> result = queryService.getGroups(userId, limit, cursor);

        Map<String, Object> response = new HashMap<>();
        response.put("groups", result.items());
        response.put("nextCursor", result.nextCursor());
        response.put("hasMore", result.hasMore());

        return ResponseGenerator.ok("Groups retrieved", response);
    }

    private APIGatewayProxyResponseEvent getGroupDetail(APIGatewayProxyRequestEvent request, String userId) {
        String groupId = request.getPathParameters().get("groupId");

        var optDetail = queryService.getGroupDetail(userId, groupId);
        if (optDetail.isEmpty()) {
            return ResponseGenerator.fail(CommonErrorCode.RESOURCE_NOT_FOUND);
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

        return ResponseGenerator.ok("Group detail retrieved", response);
    }

    private APIGatewayProxyResponseEvent updateGroup(APIGatewayProxyRequestEvent request, String userId) {
        String groupId = request.getPathParameters().get("groupId");
        CreateWordGroupRequest req = ResponseGenerator.gson().fromJson(request.getBody(), CreateWordGroupRequest.class);

        try {
            WordGroup group = commandService.updateGroup(userId, groupId,
                    req != null ? req.getGroupName() : null,
                    req != null ? req.getDescription() : null);
            return ResponseGenerator.ok("Group updated", group);
        } catch (IllegalArgumentException e) {
            return ResponseGenerator.fail(CommonErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private APIGatewayProxyResponseEvent deleteGroup(APIGatewayProxyRequestEvent request, String userId) {
        String groupId = request.getPathParameters().get("groupId");

        try {
            commandService.deleteGroup(userId, groupId);
            return ResponseGenerator.ok("Group deleted", null);
        } catch (IllegalArgumentException e) {
            return ResponseGenerator.fail(CommonErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private APIGatewayProxyResponseEvent addWordToGroup(APIGatewayProxyRequestEvent request, String userId) {
        String groupId = request.getPathParameters().get("groupId");
        String wordId = request.getPathParameters().get("wordId");

        try {
            WordGroup group = commandService.addWordToGroup(userId, groupId, wordId);
            return ResponseGenerator.ok("Word added to group", group);
        } catch (IllegalArgumentException e) {
            return ResponseGenerator.fail(CommonErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private APIGatewayProxyResponseEvent removeWordFromGroup(APIGatewayProxyRequestEvent request, String userId) {
        String groupId = request.getPathParameters().get("groupId");
        String wordId = request.getPathParameters().get("wordId");

        try {
            WordGroup group = commandService.removeWordFromGroup(userId, groupId, wordId);
            return ResponseGenerator.ok("Word removed from group", group);
        } catch (IllegalArgumentException e) {
            return ResponseGenerator.fail(CommonErrorCode.RESOURCE_NOT_FOUND);
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
