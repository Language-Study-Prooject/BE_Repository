package com.mzc.secondproject.serverless.domain.chatting.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.mzc.secondproject.serverless.common.dto.ApiResponse;
import com.mzc.secondproject.serverless.common.dto.PaginatedResult;
import com.mzc.secondproject.serverless.common.router.HandlerRouter;
import com.mzc.secondproject.serverless.common.router.Route;
import com.mzc.secondproject.serverless.common.util.ResponseUtil;
import static com.mzc.secondproject.serverless.common.util.ResponseUtil.createResponse;
import com.mzc.secondproject.serverless.domain.chatting.model.ChatRoom;
import com.mzc.secondproject.serverless.domain.chatting.service.ChatRoomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ChatRoomHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(ChatRoomHandler.class);

    private final ChatRoomService roomService;
    private final HandlerRouter router;

    public ChatRoomHandler() {
        this.roomService = new ChatRoomService();
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
        String body = request.getBody();
        Map<String, Object> requestBody = ResponseUtil.gson().fromJson(body, Map.class);

        String name = (String) requestBody.get("name");
        String description = (String) requestBody.get("description");
        String level = (String) requestBody.getOrDefault("level", "beginner");
        Integer maxMembers = ((Double) requestBody.getOrDefault("maxMembers", 6.0)).intValue();
        Boolean isPrivate = (Boolean) requestBody.getOrDefault("isPrivate", false);
        String password = (String) requestBody.get("password");
        String createdBy = (String) requestBody.get("createdBy");

        if (name == null || name.isEmpty()) {
            return createResponse(400, ApiResponse.error("name is required"));
        }

        ChatRoom room = roomService.createRoom(name, description, level, maxMembers, isPrivate, password, createdBy);
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

        PaginatedResult<ChatRoom> roomPage = roomService.getRooms(level, limit, cursor);
        List<ChatRoom> rooms = roomPage.getItems();

        if ("true".equals(joined) && userId != null) {
            rooms = roomService.filterByJoinedUser(rooms, userId);
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

        Optional<ChatRoom> optRoom = roomService.getRoom(roomId);
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

        String body = request.getBody();
        Map<String, String> requestBody = ResponseUtil.gson().fromJson(body, Map.class);
        String userId = requestBody.get("userId");
        String password = requestBody.get("password");

        if (roomId == null || userId == null) {
            return createResponse(400, ApiResponse.error("roomId and userId are required"));
        }

        ChatRoom room = roomService.joinRoom(roomId, userId, password);
        room.setPassword(null);
        return createResponse(200, ApiResponse.success("Joined room", room));
    }

    private APIGatewayProxyResponseEvent leaveRoom(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        String roomId = pathParams != null ? pathParams.get("roomId") : null;

        String body = request.getBody();
        Map<String, String> requestBody = ResponseUtil.gson().fromJson(body, Map.class);
        String userId = requestBody.get("userId");

        if (roomId == null || userId == null) {
            return createResponse(400, ApiResponse.error("roomId and userId are required"));
        }

        ChatRoomService.LeaveResult result = roomService.leaveRoom(roomId, userId);
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

        roomService.deleteRoom(roomId, userId);
        return createResponse(200, ApiResponse.success("Room deleted", null));
    }
}
