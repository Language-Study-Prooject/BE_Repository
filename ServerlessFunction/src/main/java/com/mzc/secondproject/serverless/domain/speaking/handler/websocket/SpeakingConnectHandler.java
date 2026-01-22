package com.mzc.secondproject.serverless.domain.speaking.handler.websocket;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.mzc.secondproject.serverless.common.config.WebSocketConfig;
import com.mzc.secondproject.serverless.common.util.JwtUtil;
import com.mzc.secondproject.serverless.common.util.WebSocketEventUtil;
import com.mzc.secondproject.serverless.domain.speaking.model.SpeakingConnection;
import com.mzc.secondproject.serverless.domain.speaking.repository.SpeakingConnectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Optional;

/**
 * Speaking WebSocket $connect 핸들러
 * JWT 토큰 검증 후 연결 정보를 DynamoDB에 저장
 *
 * 연결 방법:
 * wss://{api-id}.execute-api.{region}.amazonaws.com/{stage}?token={jwt}
 */
public class SpeakingConnectHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private static final Logger logger = LoggerFactory.getLogger(SpeakingConnectHandler.class);

    private final SpeakingConnectionRepository connectionRepository;

    public SpeakingConnectHandler() {
        this.connectionRepository = new SpeakingConnectionRepository();
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        logger.info("Speaking WebSocket connect event");

        try {
            String connectionId = WebSocketEventUtil.extractConnectionId(event);
            Map<String, String> queryParams = WebSocketEventUtil.extractQueryStringParameters(event);

            // JWT 토큰 검증
            String token = queryParams.get("token");

            if (token == null || token.isEmpty()) {
                logger.warn("Missing token parameter");
                return WebSocketEventUtil.unauthorized("token is required");
            }

            // 토큰 유효성 검사
            if (!JwtUtil.isValid(token)) {
                logger.warn("Invalid or expired token");
                return WebSocketEventUtil.unauthorized("Invalid or expired token");
            }

            // userId 추출
            Optional<String> userIdOpt = JwtUtil.extractUserId(token);
            if (userIdOpt.isEmpty()) {
                logger.warn("Failed to extract userId from token");
                return WebSocketEventUtil.unauthorized("Invalid token");
            }

            String userId = userIdOpt.get();

            // 연결 정보 저장
            SpeakingConnection connection = SpeakingConnection.create(
                    connectionId,
                    userId,
                    WebSocketConfig.connectionTtlSeconds()
            );

            // 레벨 파라미터가 있으면 설정
            String level = queryParams.get("level");
            if (level != null && !level.isEmpty()) {
                connection.setTargetLevel(level.toUpperCase());
            }

            connectionRepository.save(connection);

            logger.info("Speaking connection established: connectionId={}, userId={}, level={}",
                    connectionId, userId, connection.getTargetLevel());
            return WebSocketEventUtil.ok("Connected");

        } catch (Exception e) {
            logger.error("Error handling connect: {}", e.getMessage(), e);
            return WebSocketEventUtil.serverError("Internal server error");
        }
    }
}