package com.mzc.secondproject.serverless.domain.grammar.handler.websocket;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mzc.secondproject.serverless.common.util.WebSocketEventUtil;
import com.mzc.secondproject.serverless.domain.grammar.dto.response.ConversationResponse;
import com.mzc.secondproject.serverless.domain.grammar.model.GrammarConnection;
import com.mzc.secondproject.serverless.domain.grammar.repository.GrammarConnectionRepository;
import com.mzc.secondproject.serverless.domain.grammar.service.GrammarConversationService;
import com.mzc.secondproject.serverless.domain.grammar.streaming.StreamingCallback;
import com.mzc.secondproject.serverless.domain.grammar.streaming.StreamingEvent;
import com.mzc.secondproject.serverless.domain.grammar.streaming.StreamingRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.apigatewaymanagementapi.ApiGatewayManagementApiClient;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.GoneException;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.PostToConnectionRequest;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Grammar Streaming WebSocket 핸들러
 * Bedrock 스트리밍 응답을 실시간으로 클라이언트에 전송
 *
 * 인증: $connect에서 JWT 검증 후 저장된 연결 정보에서 userId 조회
 */
public class GrammarStreamingHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {

	private static final Logger logger = LoggerFactory.getLogger(GrammarStreamingHandler.class);
	private static final Gson gson = new GsonBuilder().create();

	private final GrammarConversationService conversationService;
	private final GrammarConnectionRepository connectionRepository;

	public GrammarStreamingHandler() {
		this.conversationService = new GrammarConversationService();
		this.connectionRepository = new GrammarConnectionRepository();
	}

	@Override
	public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
		logger.info("Grammar streaming event received");

		try {
			String connectionId = WebSocketEventUtil.extractConnectionId(event);
			String endpoint = WebSocketEventUtil.extractWebSocketEndpoint(event);

			// 연결 정보에서 userId 조회 (JWT 인증된 사용자)
			Optional<GrammarConnection> connectionOpt = connectionRepository.findByConnectionId(connectionId);
			if (connectionOpt.isEmpty()) {
				logger.warn("Connection not found: {}", connectionId);
				return sendError(connectionId, endpoint, "Unauthorized - please reconnect");
			}

			String userId = connectionOpt.get().getUserId();

			String body = (String) event.get("body");
			if (body == null || body.isEmpty()) {
				return sendError(connectionId, endpoint, "Message body is required");
			}

			StreamingRequest request = gson.fromJson(body, StreamingRequest.class);

			if (request.message() == null || request.message().trim().isEmpty()) {
				return sendError(connectionId, endpoint, "message is required");
			}

			// 스트리밍 대화 처리 (userId는 연결 정보에서 가져옴)
			processStreamingConversation(connectionId, endpoint, userId, request);

			return WebSocketEventUtil.ok("Streaming started");

		} catch (Exception e) {
			logger.error("Error handling streaming request: {}", e.getMessage(), e);
			return WebSocketEventUtil.serverError("Internal server error");
		}
	}

	private void processStreamingConversation(String connectionId, String endpoint, String userId, StreamingRequest request) {
		ApiGatewayManagementApiClient apiClient = createApiClient(endpoint);

		// 서비스에 스트리밍 처리 위임 (userId는 JWT 인증에서 가져온 값 사용)
		conversationService.chatStreaming(
				request.sessionId(),
				request.message(),
				userId,
				request.level(),
				// 세션 생성 콜백
				sessionId -> sendEvent(apiClient, connectionId, new StreamingEvent.StartEvent(sessionId)),
				// 스트리밍 콜백
				new StreamingCallback() {
					@Override
					public void onToken(String token) {
						sendEvent(apiClient, connectionId, new StreamingEvent.TokenEvent(token));
					}

					@Override
					public void onComplete(ConversationResponse response) {
						sendEvent(apiClient, connectionId, StreamingEvent.CompleteEvent.from(response));
						logger.info("Streaming completed for session: {}", response.getSessionId());
					}

					@Override
					public void onError(Throwable error) {
						logger.error("Streaming error: {}", error.getMessage(), error);
						sendEvent(apiClient, connectionId, new StreamingEvent.ErrorEvent(error.getMessage()));
					}
				}
		);
	}

	private void sendEvent(ApiGatewayManagementApiClient apiClient, String connectionId, StreamingEvent event) {
		String json = switch (event) {
			case StreamingEvent.StartEvent e -> gson.toJson(Map.of("type", e.type(), "sessionId", e.sessionId()));
			case StreamingEvent.TokenEvent e -> gson.toJson(Map.of("type", e.type(), "token", e.token()));
			case StreamingEvent.CompleteEvent e -> {
				Map<String, Object> data = new HashMap<>();
				data.put("type", e.type());
				data.put("sessionId", e.sessionId());
				data.put("grammarCheck", e.grammarCheck());
				data.put("aiResponse", e.aiResponse());
				data.put("conversationTip", e.conversationTip());
				yield gson.toJson(data);
			}
			case StreamingEvent.ErrorEvent e -> gson.toJson(Map.of("type", e.type(), "message", e.message()));
		};

		sendToConnection(apiClient, connectionId, json);
	}

	private ApiGatewayManagementApiClient createApiClient(String endpoint) {
		return ApiGatewayManagementApiClient.builder()
				.endpointOverride(URI.create(endpoint))
				.build();
	}

	private boolean sendToConnection(ApiGatewayManagementApiClient apiClient, String connectionId, String message) {
		try {
			PostToConnectionRequest request = PostToConnectionRequest.builder()
					.connectionId(connectionId)
					.data(SdkBytes.fromString(message, StandardCharsets.UTF_8))
					.build();

			apiClient.postToConnection(request);
			return true;

		} catch (GoneException e) {
			logger.warn("Connection gone: {}", connectionId);
			return false;

		} catch (Exception e) {
			logger.error("Failed to send message to connection {}: {}", connectionId, e.getMessage());
			return false;
		}
	}

	private Map<String, Object> sendError(String connectionId, String endpoint, String message) {
		ApiGatewayManagementApiClient apiClient = createApiClient(endpoint);
		sendEvent(apiClient, connectionId, new StreamingEvent.ErrorEvent(message));
		return WebSocketEventUtil.badRequest(message);
	}
}
