package com.mzc.secondproject.serverless.domain.grammar.handler.websocket;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.mzc.secondproject.serverless.common.config.WebSocketConfig;
import com.mzc.secondproject.serverless.common.util.JwtUtil;
import com.mzc.secondproject.serverless.domain.grammar.model.GrammarConnection;
import com.mzc.secondproject.serverless.domain.grammar.repository.GrammarConnectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Grammar Streaming WebSocket $connect 핸들러
 * JWT 토큰 검증 후 연결 정보를 DynamoDB에 저장
 *
 * 연결 방법:
 * wss://{api-id}.execute-api.{region}.amazonaws.com/{stage}?token={jwt}
 */
public class GrammarStreamingConnectHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

	private static final Logger logger = LoggerFactory.getLogger(GrammarStreamingConnectHandler.class);

	private final GrammarConnectionRepository connectionRepository;

	public GrammarStreamingConnectHandler() {
		this.connectionRepository = new GrammarConnectionRepository();
	}

	@Override
	public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
		logger.info("Grammar WebSocket connect event");

		try {
			String connectionId = extractConnectionId(event);
			Map<String, String> queryParams = extractQueryStringParameters(event);

			// JWT 토큰 검증
			String token = queryParams.get("token");

			if (token == null || token.isEmpty()) {
				logger.warn("Missing token parameter");
				return createResponse(401, "token is required");
			}

			// 토큰 유효성 검사
			if (!JwtUtil.isValid(token)) {
				logger.warn("Invalid or expired token");
				return createResponse(401, "Invalid or expired token");
			}

			// userId 추출
			Optional<String> userIdOpt = JwtUtil.extractUserId(token);
			if (userIdOpt.isEmpty()) {
				logger.warn("Failed to extract userId from token");
				return createResponse(401, "Invalid token");
			}

			String userId = userIdOpt.get();

			// 연결 정보 저장
			GrammarConnection connection = GrammarConnection.create(
					connectionId,
					userId,
					WebSocketConfig.connectionTtlSeconds()
			);

			connectionRepository.save(connection);

			logger.info("Grammar connection established: connectionId={}, userId={}", connectionId, userId);
			return createResponse(200, "Connected");

		} catch (Exception e) {
			logger.error("Error handling connect: {}", e.getMessage(), e);
			return createResponse(500, "Internal server error");
		}
	}

	@SuppressWarnings("unchecked")
	private String extractConnectionId(Map<String, Object> event) {
		Map<String, Object> requestContext = (Map<String, Object>) event.get("requestContext");
		return (String) requestContext.get("connectionId");
	}

	@SuppressWarnings("unchecked")
	private Map<String, String> extractQueryStringParameters(Map<String, Object> event) {
		Map<String, String> params = (Map<String, String>) event.get("queryStringParameters");
		return params != null ? params : new HashMap<>();
	}

	private Map<String, Object> createResponse(int statusCode, String body) {
		return Map.of("statusCode", statusCode, "body", body);
	}
}
