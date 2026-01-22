package com.mzc.secondproject.serverless.domain.speaking.handler.websocket;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.mzc.secondproject.serverless.common.util.WebSocketEventUtil;
import com.mzc.secondproject.serverless.domain.speaking.repository.SpeakingConnectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Speaking WebSocket $disconnect 핸들러
 * 연결 해제 시 DynamoDB에서 연결 정보 삭제
 */
public class SpeakingDisconnectHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {
	
	private static final Logger logger = LoggerFactory.getLogger(SpeakingDisconnectHandler.class);
	
	private final SpeakingConnectionRepository connectionRepository;
	
	public SpeakingDisconnectHandler() {
		this.connectionRepository = new SpeakingConnectionRepository();
	}
	
	@Override
	public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
		logger.info("Speaking WebSocket disconnect event");
		
		try {
			String connectionId = WebSocketEventUtil.extractConnectionId(event);
			
			// 연결 정보 삭제
			connectionRepository.delete(connectionId);
			
			logger.info("Speaking connection closed: connectionId={}", connectionId);
			return WebSocketEventUtil.ok("Disconnected");
			
		} catch (Exception e) {
			logger.error("Error handling disconnect: {}", e.getMessage(), e);
			return WebSocketEventUtil.serverError("Internal server error");
		}
	}
}
