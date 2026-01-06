package com.mzc.secondproject.serverless.chatting.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mzc.secondproject.serverless.chatting.dto.ApiResponse;
import com.mzc.secondproject.serverless.chatting.model.ChatRoom;
import com.mzc.secondproject.serverless.chatting.repository.ChatRoomRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class ChatRoomHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(ChatRoomHandler.class);
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final ChatRoomRepository roomRepository;

    public ChatRoomHandler() {
        this.roomRepository = new ChatRoomRepository();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        String httpMethod = request.getHttpMethod();
        String path = request.getPath();

        logger.info("Received request: {} {}", httpMethod, path);

        try {
            // POST /chat/rooms - 방 생성
            if ("POST".equals(httpMethod) && path.endsWith("/rooms")) {
                return createRoom(request);
            }

            // GET /chat/rooms - 방 목록 조회
            if ("GET".equals(httpMethod) && path.endsWith("/rooms")) {
                return getRooms(request);
            }

            // GET /chat/rooms/{roomId} - 방 상세 조회
            if ("GET".equals(httpMethod) && path.contains("/rooms/") && !path.contains("/join")) {
                return getRoom(request);
            }

            // POST /chat/rooms/{roomId}/join - 방 입장
            if ("POST".equals(httpMethod) && path.endsWith("/join")) {
                return joinRoom(request);
            }

            // POST /chat/rooms/{roomId}/leave - 방 퇴장
            if ("POST".equals(httpMethod) && path.endsWith("/leave")) {
                return leaveRoom(request);
            }

            // DELETE /chat/rooms/{roomId} - 방 삭제
            if ("DELETE".equals(httpMethod) && path.contains("/rooms/")) {
                return deleteRoom(request);
            }

            return createResponse(404, ApiResponse.error("Not found"));

        } catch (Exception e) {
            logger.error("Error handling request", e);
            return createResponse(500, ApiResponse.error("Internal server error: " + e.getMessage()));
        }
    }

    private APIGatewayProxyResponseEvent createRoom(APIGatewayProxyRequestEvent request) {
        String body = request.getBody();
        Map<String, Object> requestBody = gson.fromJson(body, Map.class);

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

        String roomId = UUID.randomUUID().toString();
        String now = Instant.now().toString();

        ChatRoom room = ChatRoom.builder()
                .pk("ROOM#" + roomId)
                .sk("METADATA")
                .gsi1pk("ROOMS")
                .gsi1sk(level + "#" + now)
                .roomId(roomId)
                .name(name)
                .description(description)
                .level(level)
                .currentMembers(1)  // 방장 포함
                .maxMembers(maxMembers)
                .isPrivate(isPrivate)
                .password(isPrivate ? password : null)
                .createdBy(createdBy)
                .createdAt(now)
                .lastMessageAt(now)
                .memberIds(new ArrayList<>(List.of(createdBy)))
                .build();

        roomRepository.save(room);

        // 비밀번호는 응답에서 제외
        room.setPassword(null);

        logger.info("Created room: {}", roomId);
        return createResponse(201, ApiResponse.success("Room created", room));
    }

    private APIGatewayProxyResponseEvent getRooms(APIGatewayProxyRequestEvent request) {
        Map<String, String> queryParams = request.getQueryStringParameters();

        String level = queryParams != null ? queryParams.get("level") : null;
        String userId = queryParams != null ? queryParams.get("userId") : null;
        String joined = queryParams != null ? queryParams.get("joined") : null;
        String cursor = queryParams != null ? queryParams.get("cursor") : null;

        int limit = 10;  // 기본값
        if (queryParams != null && queryParams.get("limit") != null) {
            limit = Math.min(Integer.parseInt(queryParams.get("limit")), 20);  // 최대 20
        }

        ChatRoomRepository.RoomPage roomPage;
        if (level != null && !level.isEmpty()) {
            roomPage = roomRepository.findByLevelWithPagination(level, limit, cursor);
        } else {
            roomPage = roomRepository.findAllWithPagination(limit, cursor);
        }

        List<ChatRoom> rooms = roomPage.getRooms();

        // "참여중" 필터 - userId가 memberIds에 포함된 방만
        if ("true".equals(joined) && userId != null) {
            rooms = rooms.stream()
                    .filter(room -> room.getMemberIds() != null && room.getMemberIds().contains(userId))
                    .toList();
        }

        // 비밀번호 제외
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

        Optional<ChatRoom> optRoom = roomRepository.findById(roomId);
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
        Map<String, String> requestBody = gson.fromJson(body, Map.class);
        String userId = requestBody.get("userId");
        String password = requestBody.get("password");

        if (roomId == null || userId == null) {
            return createResponse(400, ApiResponse.error("roomId and userId are required"));
        }

        Optional<ChatRoom> optRoom = roomRepository.findById(roomId);
        if (optRoom.isEmpty()) {
            return createResponse(404, ApiResponse.error("Room not found"));
        }

        ChatRoom room = optRoom.get();

        // 비밀번호 확인
        if (room.getIsPrivate() && !room.getPassword().equals(password)) {
            return createResponse(403, ApiResponse.error("Invalid password"));
        }

        // 인원 확인
        if (room.getCurrentMembers() >= room.getMaxMembers()) {
            return createResponse(400, ApiResponse.error("Room is full"));
        }

        // 이미 참여 중인지 확인
        if (room.getMemberIds() != null && room.getMemberIds().contains(userId)) {
            room.setPassword(null);
            return createResponse(200, ApiResponse.success("Already joined", room));
        }

        // 멤버 추가
        if (room.getMemberIds() == null) {
            room.setMemberIds(new ArrayList<>());
        }
        room.getMemberIds().add(userId);
        room.setCurrentMembers(room.getCurrentMembers() + 1);

        roomRepository.save(room);
        room.setPassword(null);

        logger.info("User {} joined room {}", userId, roomId);
        return createResponse(200, ApiResponse.success("Joined room", room));
    }

    private APIGatewayProxyResponseEvent leaveRoom(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        String roomId = pathParams != null ? pathParams.get("roomId") : null;

        String body = request.getBody();
        Map<String, String> requestBody = gson.fromJson(body, Map.class);
        String userId = requestBody.get("userId");

        if (roomId == null || userId == null) {
            return createResponse(400, ApiResponse.error("roomId and userId are required"));
        }

        Optional<ChatRoom> optRoom = roomRepository.findById(roomId);
        if (optRoom.isEmpty()) {
            return createResponse(404, ApiResponse.error("Room not found"));
        }

        ChatRoom room = optRoom.get();

        // 멤버에서 제거
        if (room.getMemberIds() != null) {
            room.getMemberIds().remove(userId);
            room.setCurrentMembers(Math.max(0, room.getCurrentMembers() - 1));
        }

        // 방장이 나가거나 인원이 0이면 방 삭제
        if (userId.equals(room.getCreatedBy()) || room.getCurrentMembers() <= 0) {
            roomRepository.delete(roomId);
            return createResponse(200, ApiResponse.success("Room deleted", null));
        }

        roomRepository.save(room);
        room.setPassword(null);

        logger.info("User {} left room {}", userId, roomId);
        return createResponse(200, ApiResponse.success("Left room", room));
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

        // 방장 확인
        Optional<ChatRoom> optRoom = roomRepository.findById(roomId);
        if (optRoom.isEmpty()) {
            return createResponse(404, ApiResponse.error("Room not found"));
        }

        ChatRoom room = optRoom.get();
        if (!userId.equals(room.getCreatedBy())) {
            return createResponse(403, ApiResponse.error("Only the room owner can delete the room"));
        }

        roomRepository.delete(roomId);
        logger.info("Deleted room: {} by owner: {}", roomId, userId);

        return createResponse(200, ApiResponse.success("Room deleted", null));
    }

    private APIGatewayProxyResponseEvent createResponse(int statusCode, Object body) {
        return new APIGatewayProxyResponseEvent()
                .withStatusCode(statusCode)
                .withHeaders(Map.of(
                        "Content-Type", "application/json",
                        "Access-Control-Allow-Origin", "*",
                        "Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS",
                        "Access-Control-Allow-Headers", "Content-Type,Authorization"
                ))
                .withBody(gson.toJson(body));
    }
}
