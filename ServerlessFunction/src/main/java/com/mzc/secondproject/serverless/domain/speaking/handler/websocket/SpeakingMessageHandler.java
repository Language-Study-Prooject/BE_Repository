package com.mzc.secondproject.serverless.domain.speaking.handler.websocket;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mzc.secondproject.serverless.common.util.WebSocketEventUtil;
import com.mzc.secondproject.serverless.domain.speaking.repository.SpeakingConnectionRepository;
import com.mzc.secondproject.serverless.domain.speaking.service.SpeakingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.apigatewaymanagementapi.ApiGatewayManagementApiClient;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.PostToConnectionRequest;

import java.net.URI;
import java.util.Map;

/**
 * Speaking WebSocket 메시지 핸들러
 * <p>
 * 지원하는 action:
 * - speak: 음성 입력 처리 (audio base64)
 * - text: 텍스트 입력 처리
 * - setLevel: 레벨 변경
 * - reset: 대화 히스토리 초기화
 */
public class SpeakingMessageHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {
	
	private static final Logger logger = LoggerFactory.getLogger(SpeakingMessageHandler.class);
	private static final Gson gson = new GsonBuilder().create();
	
	private final SpeakingService speakingService;
	private final SpeakingConnectionRepository connectionRepository;
	
	public SpeakingMessageHandler() {
		this.speakingService = new SpeakingService();
		this.connectionRepository = new SpeakingConnectionRepository();
	}
	
	@Override
	public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
		logger.info("Speaking message event received");
		
		String connectionId = null;
		String endpoint = null;
		
		try {
			connectionId = WebSocketEventUtil.extractConnectionId(event);
			endpoint = WebSocketEventUtil.extractWebSocketEndpoint(event);
			
			// 연결 정보 확인
			if (connectionRepository.findByConnectionId(connectionId).isEmpty()) {
				logger.warn("Connection not found: {}", connectionId);
				return sendError(connectionId, endpoint, "Unauthorized - please reconnect");
			}
			
			// 요청 바디 파싱
			String body = (String) event.get("body");
			if (body == null || body.isEmpty()) {
				return sendError(connectionId, endpoint, "Message body is required");
			}
			
			JsonObject request = JsonParser.parseString(body).getAsJsonObject();
			String action = request.has("action") ? request.get("action").getAsString() : "speak";
			
			logger.info("Processing action: {} for connectionId: {}", action, connectionId);
			
			// 액션별 처리
			switch (action) {
				case "speak" -> handleSpeak(connectionId, endpoint, request);
				case "text" -> handleText(connectionId, endpoint, request);
				case "setLevel" -> handleSetLevel(connectionId, endpoint, request);
				case "reset" -> handleReset(connectionId, endpoint);
				default -> sendError(connectionId, endpoint, "Unknown action: " + action);
			}
			
			return WebSocketEventUtil.ok("Processed");
			
		} catch (Exception e) {
			logger.error("Error processing message: {}", e.getMessage(), e);
			if (connectionId != null && endpoint != null) {
				sendError(connectionId, endpoint, "Processing error: " + e.getMessage());
			}
			return WebSocketEventUtil.serverError("Internal server error");
		}
	}
	
	/**
	 * 음성 입력 처리
	 */
	private void handleSpeak(String connectionId, String endpoint, JsonObject request) {
		// 시작 이벤트 전송
		sendToConnection(connectionId, endpoint, Map.of(
				"type", "start",
				"message", "Processing your voice..."
		));
		
		// 음성 데이터 추출
		String audioBase64 = request.has("audio") ? request.get("audio").getAsString() : null;
		if (audioBase64 == null || audioBase64.isEmpty()) {
			sendError(connectionId, endpoint, "audio data is required for speak action");
			return;
		}
		
		// 음성 처리
		SpeakingService.SpeakingResponse response = speakingService.processVoiceInput(
				connectionId, audioBase64
		);
		
		// 결과 전송
		sendToConnection(connectionId, endpoint, Map.of(
				"type", "complete",
				"userTranscript", response.userTranscript(),
				"aiText", response.aiText(),
				"aiAudioUrl", response.aiAudioUrl(),
				"confidence", response.confidence()
		));
	}
	
	/**
	 * 텍스트 입력 처리
	 */
	private void handleText(String connectionId, String endpoint, JsonObject request) {
		String text = request.has("text") ? request.get("text").getAsString() : null;
		if (text == null || text.trim().isEmpty()) {
			sendError(connectionId, endpoint, "text is required for text action");
			return;
		}
		
		// 시작 이벤트 전송
		sendToConnection(connectionId, endpoint, Map.of(
				"type", "start",
				"message", "Processing your message..."
		));
		
		// 텍스트 처리
		SpeakingService.SpeakingResponse response = speakingService.processTextInput(
				connectionId, text.trim()
		);
		
		// 결과 전송
		sendToConnection(connectionId, endpoint, Map.of(
				"type", "complete",
				"userTranscript", response.userTranscript(),
				"aiText", response.aiText(),
				"aiAudioUrl", response.aiAudioUrl(),
				"confidence", response.confidence()
		));
	}
	
	/**
	 * 레벨 변경 처리
	 */
	private void handleSetLevel(String connectionId, String endpoint, JsonObject request) {
		String level = request.has("level") ? request.get("level").getAsString() : null;
		if (level == null || level.isEmpty()) {
			sendError(connectionId, endpoint, "level is required");
			return;
		}
		
		speakingService.updateLevel(connectionId, level);
		
		sendToConnection(connectionId, endpoint, Map.of(
				"type", "levelChanged",
				"level", level.toUpperCase()
		));
	}
	
	/**
	 * 대화 초기화 처리
	 */
	private void handleReset(String connectionId, String endpoint) {
		speakingService.resetConversation(connectionId);
		
		sendToConnection(connectionId, endpoint, Map.of(
				"type", "reset",
				"message", "Conversation has been reset. Let's start fresh!"
		));
	}
	
	/**
	 * WebSocket으로 메시지 전송
	 */
	private void sendToConnection(String connectionId, String endpoint, Map<String, Object> data) {
		try {
			ApiGatewayManagementApiClient apiClient = ApiGatewayManagementApiClient.builder()
					.endpointOverride(URI.create(endpoint))
					.build();
			
			String message = gson.toJson(data);
			
			apiClient.postToConnection(PostToConnectionRequest.builder()
					.connectionId(connectionId)
					.data(SdkBytes.fromUtf8String(message))
					.build());
			
			logger.debug("Message sent to {}: {}", connectionId, data.get("type"));
			
		} catch (Exception e) {
			logger.error("Failed to send message to {}: {}", connectionId, e.getMessage());
		}
	}
	
	/**
	 * 에러 메시지 전송
	 */
	private Map<String, Object> sendError(String connectionId, String endpoint, String errorMessage) {
		sendToConnection(connectionId, endpoint, Map.of(
				"type", "error",
				"message", errorMessage
		));
		return WebSocketEventUtil.ok("Error sent");
	}
}
