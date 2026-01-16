package com.mzc.secondproject.serverless.domain.grammar.handler.websocket;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.mzc.secondproject.serverless.common.util.WebSocketEventUtil;
import com.mzc.secondproject.serverless.domain.grammar.repository.GrammarConnectionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Grammar Streaming WebSocket $disconnect 핸들러
 * 연결 해제 시 DynamoDB에서 연결 정보 삭제
 */
public class GrammarStreamingDisconnectHandler implements RequestHandler<Map<String, Object>, Map<String, Object>> {
	
	private static final Logger logger = LoggerFactory.getLogger(GrammarStreamingDisconnectHandler.class);
	
	private final GrammarConnectionRepository connectionRepository;
	
	public GrammarStreamingDisconnectHandler() {
		this.connectionRepository = new GrammarConnectionRepository();
	}
	
	@Override
	public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
		logger.info("Grammar WebSocket disconnect event");
		
		try {
			String connectionId = WebSocketEventUtil.extractConnectionId(event);
			
			// 연결 정보 삭제
			connectionRepository.delete(connectionId);
			
			logger.info("Grammar connection closed: connectionId={}", connectionId);
			return WebSocketEventUtil.ok("Disconnected");
			
		} catch (Exception e) {
			logger.error("Error handling disconnect: {}", e.getMessage(), e);
			return WebSocketEventUtil.serverError("Internal server error");
		}
	}
}
