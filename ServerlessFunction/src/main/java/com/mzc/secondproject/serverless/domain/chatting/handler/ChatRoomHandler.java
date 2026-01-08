package com.mzc.secondproject.serverless.domain.chatting.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.mzc.secondproject.serverless.common.dto.ApiResponse;
import com.mzc.secondproject.serverless.common.dto.PaginatedResult;
import com.mzc.secondproject.serverless.common.dto.request.chatting.CreateRoomRequest;
import com.mzc.secondproject.serverless.common.dto.request.chatting.JoinRoomRequest;
import com.mzc.secondproject.serverless.common.dto.request.chatting.LeaveRoomRequest;
import com.mzc.secondproject.serverless.common.router.HandlerRouter;
import com.mzc.secondproject.serverless.common.router.Route;
import com.mzc.secondproject.serverless.common.util.ResponseUtil;
import static com.mzc.secondproject.serverless.common.util.ResponseUtil.createResponse;
import com.mzc.secondproject.serverless.domain.chatting.model.ChatRoom;
import com.mzc.secondproject.serverless.domain.chatting.service.ChatRoomCommandService;
import com.mzc.secondproject.serverless.domain.chatting.service.ChatRoomQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ChatRoomHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(ChatRoomHandler.class);

    private final ChatRoomCommandService commandService;
    private final ChatRoomQueryService queryService;
    private final HandlerRouter router;

    public ChatRoomHandler() {
        this.commandService = new ChatRoomCommandService();
        this.queryService = new ChatRoomQueryService();
        this.router = initRouter();
    }

    private HandlerRouter initRouter() {
        return new HandlerRouter().addRoutes(
                Route.post("/rooms", this::createRoom),
                Route.get("/rooms", this::getRooms),
                Route.get("/rooms/{roomId}", this::getRoom),
                Route.post("/rooms/{roomId}/join", this::joinRoom),
                Route.post("/rooms/{roomId}/leave", this::leaveRoom),
                Route.delete("/rooms/{roomId}", this::deleteRoom)
        );
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        logger.info("Received request: {} {}", request.getHttpMethod(), request.getPath());
        return router.route(request);
    }

    private APIGatewayProxyResponseEvent createRoom(APIGatewayProxyRequestEvent request) {
        CreateRoomRequest req = ResponseUtil.gson().fromJson(request.getBody(), CreateRoomRequest.class);

        if (req.getName() == null || req.getName().isEmpty()) {
            return createResponse(400, ApiResponse.error("name is required"));
        }

        String level = req.getLevel() != null ? req.getLevel() : "beginner";
        Integer maxMembers = req.getMaxMembers() != null ? req.getMaxMembers() : 6;
        Boolean isPrivate = req.getIsPrivate() != null ? req.getIsPrivate() : false;

        ChatRoom room = commandService.createRoom(
                req.getName(), req.getDescription(), level, maxMembers, isPrivate, req.getPassword(), req.getCreatedBy());
        room.setPassword(null);

        return createResponse(201, ApiResponse.success("Room created", room));
    }

    private APIGatewayProxyResponseEvent getRooms(APIGatewayProxyRequestEvent request) {
        Map<String, String> queryParams = request.getQueryStringParameters();

        String level = queryParams != null ? queryParams.get("level") : null;
        String userId = queryParams != null ? queryParams.get("userId") : null;
        String joined = queryParams != null ? queryParams.get("joined") : null;
        String cursor = queryParams != null ? queryParams.get("cursor") : null;

        int limit = 10;
        if (queryParams != null && queryParams.get("limit") != null) {
            limit = Math.min(Integer.parseInt(queryParams.get("limit")), 20);
        }

        PaginatedResult<ChatRoom> roomPage = queryService.getRooms(level, limit, cursor);
        List<ChatRoom> rooms = roomPage.getItems();

        if ("true".equals(joined) && userId != null) {
            rooms = queryService.filterByJoinedUser(rooms, userId);
        }

        rooms.forEach(room -> room.setPassword(null));

        Map<String, Object> result = new HashMap<>();
        result.put("rooms", rooms);
        result.put("nextCursor", roomPage.getNextCursor());
        result.put("hasMore", roomPage.hasMore());

        return createResponse(200, ApiResponse.success("Rooms retrieved", result));
    }

    private APIGatewayProxyResponseEvent getRoom(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        String roomId = pathParams != null ? pathParams.get("roomId") : null;

        if (roomId == null) {
            return createResponse(400, ApiResponse.error("roomId is required"));
        }

        Optional<ChatRoom> optRoom = queryService.getRoom(roomId);
        if (optRoom.isEmpty()) {
            return createResponse(404, ApiResponse.error("Room not found"));
        }

        ChatRoom room = optRoom.get();
        room.setPassword(null);

        return createResponse(200, ApiResponse.success("Room retrieved", room));
    }

    private APIGatewayProxyResponseEvent joinRoom(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        String roomId = pathParams != null ? pathParams.get("roomId") : null;

        JoinRoomRequest req = ResponseUtil.gson().fromJson(request.getBody(), JoinRoomRequest.class);

        if (roomId == null || req.getUserId() == null) {
            return createResponse(400, ApiResponse.error("roomId and userId are required"));
        }

        ChatRoom room = commandService.joinRoom(roomId, req.getUserId(), req.getPassword());
        room.setPassword(null);
        return createResponse(200, ApiResponse.success("Joined room", room));
    }

    private APIGatewayProxyResponseEvent leaveRoom(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        String roomId = pathParams != null ? pathParams.get("roomId") : null;

        LeaveRoomRequest req = ResponseUtil.gson().fromJson(request.getBody(), LeaveRoomRequest.class);

        if (roomId == null || req.getUserId() == null) {
            return createResponse(400, ApiResponse.error("roomId and userId are required"));
        }

        ChatRoomCommandService.LeaveResult result = commandService.leaveRoom(roomId, req.getUserId());
        if (result.deleted()) {
            return createResponse(200, ApiResponse.success("Room deleted", null));
        }
        result.room().setPassword(null);
        return createResponse(200, ApiResponse.success("Left room", result.room()));
    }

    private APIGatewayProxyResponseEvent deleteRoom(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        Map<String, String> queryParams = request.getQueryStringParameters();

        String roomId = pathParams != null ? pathParams.get("roomId") : null;
        String userId = queryParams != null ? queryParams.get("userId") : null;

        if (roomId == null) {
            return createResponse(400, ApiResponse.error("roomId is required"));
        }

        if (userId == null) {
            return createResponse(400, ApiResponse.error("userId is required"));
        }

        commandService.deleteRoom(roomId, userId);
        return createResponse(200, ApiResponse.success("Room deleted", null));
    }
}
