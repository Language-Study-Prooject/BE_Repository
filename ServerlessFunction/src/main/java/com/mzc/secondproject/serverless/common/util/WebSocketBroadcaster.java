package com.mzc.secondproject.serverless.common.util;

import com.mzc.secondproject.serverless.common.config.WebSocketConfig;
import com.mzc.secondproject.serverless.domain.chatting.model.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.apigatewaymanagementapi.ApiGatewayManagementApiClient;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.GoneException;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.PostToConnectionRequest;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * WebSocket 연결들에게 메시지를 브로드캐스트하는 유틸리티
 */
public class WebSocketBroadcaster {
	
	private static final Logger logger = LoggerFactory.getLogger(WebSocketBroadcaster.class);
	
	private final ApiGatewayManagementApiClient apiClient;
	
	public WebSocketBroadcaster() {
		String endpoint = WebSocketConfig.websocketEndpoint();
		this.apiClient = ApiGatewayManagementApiClient.builder()
				.endpointOverride(URI.create(endpoint))
				.build();
	}
	
	public WebSocketBroadcaster(String endpoint) {
		this.apiClient = ApiGatewayManagementApiClient.builder()
				.endpointOverride(URI.create(endpoint))
				.build();
	}
	
	/**
	 * 단일 연결에 메시지 전송
	 *
	 * @return 전송 성공 여부
	 */
	public boolean sendToConnection(String connectionId, String message) {
		try {
			PostToConnectionRequest request = PostToConnectionRequest.builder()
					.connectionId(connectionId)
					.data(SdkBytes.fromString(message, StandardCharsets.UTF_8))
					.build();
			
			apiClient.postToConnection(request);
			logger.debug("Message sent to connection: {}", connectionId);
			return true;
			
		} catch (GoneException e) {
			logger.warn("Connection gone: {}", connectionId);
			return false;
			
		} catch (Exception e) {
			logger.error("Failed to send message to connection {}: {}", connectionId, e.getMessage());
			return false;
		}
	}
	
	/**
	 * 여러 연결에 메시지 브로드캐스트
	 *
	 * @return 전송 실패한 connectionId 목록
	 */
	public List<String> broadcast(List<Connection> connections, String message) {
		List<String> failedConnections = new ArrayList<>();
		
		for (Connection connection : connections) {
			boolean success = sendToConnection(connection.getConnectionId(), message);
			if (!success) {
				failedConnections.add(connection.getConnectionId());
			}
		}
		
		logger.info("Broadcast completed: total={}, failed={}",
				connections.size(), failedConnections.size());
		
		return failedConnections;
	}
}
