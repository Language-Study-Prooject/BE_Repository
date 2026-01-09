package com.mzc.secondproject.serverless.domain.chatting.handler.websocket;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.mzc.secondproject.serverless.domain.chatting.model.Connection;
import com.mzc.secondproject.serverless.domain.chatting.repository.ConnectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * WebSocket $disconnect 라우트 핸들러
 * 클라이언트 연결 해제 시 Connection 정보를 DynamoDB에서 삭제
 */
public class WebSocketDisconnectHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketDisconnectHandler.class);

    private final ConnectionRepository connectionRepository;

    public WebSocketDisconnectHandler() {
        this.connectionRepository = new ConnectionRepository();
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        logger.info("WebSocket disconnect event: {}", event);

        try {
            String connectionId = extractConnectionId(event);

            Optional<Connection> connection = connectionRepository.findByConnectionId(connectionId);

            if (connection.isPresent()) {
                Connection conn = connection.get();
                connectionRepository.delete(connectionId);
                logger.info("Connection deleted: connectionId={}, userId={}, roomId={}",
                        connectionId, conn.getUserId(), conn.getRoomId());
            } else {
                logger.warn("Connection not found for deletion: connectionId={}", connectionId);
            }

            return createResponse(200, "Disconnected");

        } catch (Exception e) {
            logger.error("Error handling disconnect: {}", e.getMessage(), e);
            return createResponse(500, "Internal server error");
        }
    }

    @SuppressWarnings("unchecked")
    private String extractConnectionId(Map<String, Object> event) {
        Map<String, Object> requestContext = (Map<String, Object>) event.get("requestContext");
        return (String) requestContext.get("connectionId");
    }

    private Map<String, Object> createResponse(int statusCode, String body) {
        Map<String, Object> response = new HashMap<>();
        response.put("statusCode", statusCode);
        response.put("body", body);
        return response;
    }
}
