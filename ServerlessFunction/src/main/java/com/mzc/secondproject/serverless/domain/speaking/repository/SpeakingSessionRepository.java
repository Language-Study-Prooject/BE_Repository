package com.mzc.secondproject.serverless.domain.speaking.repository;

import com.mzc.secondproject.serverless.common.config.AwsClients;
import com.mzc.secondproject.serverless.common.config.EnvConfig;
import com.mzc.secondproject.serverless.domain.speaking.model.SpeakingSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.util.Optional;

/**
 * Speaking WebSocket 연결 정보 Repository
 */
public class SpeakingSessionRepository {
	
	private static final Logger logger = LoggerFactory.getLogger(SpeakingSessionRepository.class);
	private static final String TABLE_NAME = EnvConfig.getRequired("CHAT_TABLE_NAME");
	
	private final DynamoDbTable<SpeakingSession> table;
	
	public SpeakingSessionRepository() {
		this.table = AwsClients.dynamoDbEnhanced().table(
				TABLE_NAME,
				TableSchema.fromBean(SpeakingSession.class)
		);
	}
	
	/**
	 * 연결 정보 저장
	 */
	public void save(SpeakingSession session) {
		table.putItem(session);
		logger.debug("Speaking session saved: sessionId={}, userId={}",
				session.getSessionId(), session.getUserId());
	}
	
	/**
	 * sessionId로 연결 정보 조회
	 */
	public Optional<SpeakingSession> findBySessionId(String sessionId) {
		Key key = Key.builder()
				.partitionValue(SpeakingSession.PK_PREFIX + sessionId)
				.sortValue(SpeakingSession.SK_METADATA)
				.build();
		
		SpeakingSession session = table.getItem(key);
		return Optional.ofNullable(session);
	}
	
	/**
	 * 연결 정보 업데이트 (대화 히스토리 등)
	 */
	public void update(SpeakingSession session) {
		session.touch(); // 업데이트 시간 및 TTL 갱신
		table.putItem(session);
		logger.debug("Speaking session updated: sessionId={}", session.getSessionId());
	}
	
	/**
	 * 연결 정보 삭제
	 */
	public void delete(String sessionId) {
		Key key = Key.builder()
				.partitionValue(SpeakingSession.PK_PREFIX + sessionId)
				.sortValue(SpeakingSession.SK_METADATA)
				.build();
		
		table.deleteItem(key);
		logger.info("Speaking session deleted: sessionId={}", sessionId);
	}
}
