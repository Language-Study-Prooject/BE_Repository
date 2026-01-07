package com.mzc.secondproject.serverless.domain.chatting.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mzc.secondproject.serverless.common.dto.ApiResponse;
import com.mzc.secondproject.serverless.domain.chatting.model.ChatMessage;
import com.mzc.secondproject.serverless.domain.chatting.model.ChatRoom;
import com.mzc.secondproject.serverless.domain.chatting.repository.ChatMessageRepository;
import com.mzc.secondproject.serverless.domain.chatting.repository.ChatRoomRepository;
import com.mzc.secondproject.serverless.domain.chatting.service.ChatMessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class ChatMessageHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(ChatMessageHandler.class);
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    private final ChatMessageService chatMessageService;
    private final ChatRoomRepository chatRoomRepository;

    public ChatMessageHandler() {
        this.chatMessageService = new ChatMessageService();
        this.chatRoomRepository = new ChatRoomRepository();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        logger.info("Received request: {} {}", request.getHttpMethod(), request.getPath());

        try {
            return switch (request.getHttpMethod()) {
                case "POST" -> handlePost(request);
                case "GET" -> handleGet(request);
                default -> createResponse(405, ApiResponse.error("Method not allowed"));
            };
        } catch (Exception e) {
            logger.error("Error processing request", e);
            return createResponse(500, ApiResponse.error("Internal server error: " + e.getMessage()));
        }
    }

    private APIGatewayProxyResponseEvent handlePost(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        String roomId = pathParams != null ? pathParams.get("roomId") : null;

        if (roomId == null) {
            return createResponse(400, ApiResponse.error("roomId is required"));
        }

        String body = request.getBody();
        Map<String, Object> requestBody = gson.fromJson(body, Map.class);

        String userId = (String) requestBody.get("userId");
        String content = (String) requestBody.get("content");
        String messageType = (String) requestBody.getOrDefault("messageType", "TEXT");

        if (userId == null || content == null) {
            return createResponse(400, ApiResponse.error("userId and content are required"));
        }

        String messageId = UUID.randomUUID().toString();
        String now = Instant.now().toString();

        ChatMessage message = ChatMessage.builder()
                .pk("ROOM#" + roomId)
                .sk("MSG#" + now + "#" + messageId)
                .gsi1pk("USER#" + userId)
                .gsi1sk("MSG#" + now)
                .gsi2pk("MSG#" + messageId)        // GSI2: messageId로 직접 조회용
                .gsi2sk("ROOM#" + roomId)
                .messageId(messageId)
                .roomId(roomId)
                .userId(userId)
                .content(content)
                .messageType(messageType)
                .createdAt(now)
                .build();

        ChatMessage savedMessage = chatMessageService.saveMessage(message);

        // 채팅방 lastMessageAt 업데이트 (UpdateExpression으로 1회 호출)
        chatRoomRepository.updateLastMessageAt(roomId, now);

        logger.info("Message sent: {} in room: {}", messageId, roomId);
        return createResponse(201, ApiResponse.success("Message sent", savedMessage));
    }

    private APIGatewayProxyResponseEvent handleGet(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        Map<String, String> queryParams = request.getQueryStringParameters();

        String roomId = pathParams != null ? pathParams.get("roomId") : null;
        String messageId = pathParams != null ? pathParams.get("messageId") : null;

        if (roomId == null) {
            return createResponse(400, ApiResponse.error("roomId is required"));
        }

        if (messageId != null) {
            // Get single message
            Optional<ChatMessage> message = chatMessageService.getMessage(roomId, messageId);
            if (message.isEmpty()) {
                return createResponse(404, ApiResponse.error("Message not found"));
            }
            return createResponse(200, ApiResponse.success("Message retrieved", message.get()));
        }

        // 페이지네이션 파라미터
        int limit = 20;  // 기본값
        String cursor = null;

        if (queryParams != null) {
            if (queryParams.get("limit") != null) {
                limit = Math.min(Integer.parseInt(queryParams.get("limit")), 50);  // 최대 50
            }
            cursor = queryParams.get("cursor");
        }

        // 메시지 목록 조회 (최신순, 페이지네이션)
        ChatMessageRepository.MessagePage messagePage = chatMessageService.getMessagesByRoomWithPagination(roomId, limit, cursor);

        Map<String, Object> result = new HashMap<>();
        result.put("messages", messagePage.getMessages());
        result.put("nextCursor", messagePage.getNextCursor());
        result.put("hasMore", messagePage.hasMore());

        return createResponse(200, ApiResponse.success("Messages retrieved", result));
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
