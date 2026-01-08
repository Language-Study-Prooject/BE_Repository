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
import com.mzc.secondproject.serverless.domain.chatting.model.ChatMessage;
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

    private final ChatMessageService chatMessageService;
    private final ChatRoomRepository chatRoomRepository;
    private final HandlerRouter router;

    public ChatMessageHandler() {
        this.chatMessageService = new ChatMessageService();
        this.chatRoomRepository = new ChatRoomRepository();
        this.router = initRouter();
    }

    private HandlerRouter initRouter() {
        return new HandlerRouter().addRoutes(
                Route.post("/rooms/{roomId}/messages", this::sendMessage),
                Route.get("/rooms/{roomId}/messages/{messageId}", this::getMessage),
                Route.get("/rooms/{roomId}/messages", this::getMessages)
        );
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        logger.info("Received request: {} {}", request.getHttpMethod(), request.getPath());
        return router.route(request);
    }

    private APIGatewayProxyResponseEvent sendMessage(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        String roomId = pathParams != null ? pathParams.get("roomId") : null;

        if (roomId == null) {
            return createResponse(400, ApiResponse.error("roomId is required"));
        }

        String body = request.getBody();
        Map<String, Object> requestBody = ResponseUtil.gson().fromJson(body, Map.class);

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
                .gsi2pk("MSG#" + messageId)
                .gsi2sk("ROOM#" + roomId)
                .messageId(messageId)
                .roomId(roomId)
                .userId(userId)
                .content(content)
                .messageType(messageType)
                .createdAt(now)
                .build();

        ChatMessage savedMessage = chatMessageService.saveMessage(message);
        chatRoomRepository.updateLastMessageAt(roomId, now);

        logger.info("Message sent: {} in room: {}", messageId, roomId);
        return createResponse(201, ApiResponse.success("Message sent", savedMessage));
    }

    private APIGatewayProxyResponseEvent getMessage(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        String roomId = pathParams != null ? pathParams.get("roomId") : null;
        String messageId = pathParams != null ? pathParams.get("messageId") : null;

        if (roomId == null || messageId == null) {
            return createResponse(400, ApiResponse.error("roomId and messageId are required"));
        }

        Optional<ChatMessage> message = chatMessageService.getMessage(roomId, messageId);
        if (message.isEmpty()) {
            return createResponse(404, ApiResponse.error("Message not found"));
        }
        return createResponse(200, ApiResponse.success("Message retrieved", message.get()));
    }

    private APIGatewayProxyResponseEvent getMessages(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        Map<String, String> queryParams = request.getQueryStringParameters();

        String roomId = pathParams != null ? pathParams.get("roomId") : null;

        if (roomId == null) {
            return createResponse(400, ApiResponse.error("roomId is required"));
        }

        int limit = 20;
        String cursor = null;

        if (queryParams != null) {
            if (queryParams.get("limit") != null) {
                limit = Math.min(Integer.parseInt(queryParams.get("limit")), 50);
            }
            cursor = queryParams.get("cursor");
        }

        PaginatedResult<ChatMessage> messagePage = chatMessageService.getMessagesByRoomWithPagination(roomId, limit, cursor);

        Map<String, Object> result = new HashMap<>();
        result.put("messages", messagePage.getItems());
        result.put("nextCursor", messagePage.getNextCursor());
        result.put("hasMore", messagePage.hasMore());

        return createResponse(200, ApiResponse.success("Messages retrieved", result));
    }
}
