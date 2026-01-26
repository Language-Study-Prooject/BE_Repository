package com.mzc.secondproject.serverless.domain.chatting.repository;

import com.mzc.secondproject.serverless.common.config.AwsClients;
import com.mzc.secondproject.serverless.common.config.EnvConfig;
import com.mzc.secondproject.serverless.domain.chatting.model.WordChainSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.Optional;

/**
 * 끝말잇기 게임 세션 Repository
 */
public class WordChainSessionRepository {

	private static final Logger logger = LoggerFactory.getLogger(WordChainSessionRepository.class);
	private static final String TABLE_NAME = EnvConfig.getRequired("CHAT_TABLE_NAME");
	private static final String GSI1_INDEX_NAME = "GSI1";

	private final DynamoDbTable<WordChainSession> table;
	private final DynamoDbIndex<WordChainSession> gsi1Index;

	public WordChainSessionRepository() {
		this.table = AwsClients.dynamoDbEnhanced()
				.table(TABLE_NAME, TableSchema.fromBean(WordChainSession.class));
		this.gsi1Index = table.index(GSI1_INDEX_NAME);
	}

	public WordChainSessionRepository(DynamoDbTable<WordChainSession> table) {
		this.table = table;
		this.gsi1Index = table.index(GSI1_INDEX_NAME);
	}

	/**
	 * 세션 저장
	 */
	public void save(WordChainSession session) {
		table.putItem(session);
		logger.debug("Saved WordChainSession: {}", session.getSessionId());
	}

	/**
	 * 세션 ID로 조회
	 */
	public Optional<WordChainSession> findById(String sessionId) {
		Key key = Key.builder()
				.partitionValue("WORDCHAIN#" + sessionId)
				.sortValue("METADATA")
				.build();
		WordChainSession session = table.getItem(key);
		return Optional.ofNullable(session);
	}

	/**
	 * 방의 활성 세션 조회 (GSI1 인덱스 사용)
	 */
	public Optional<WordChainSession> findActiveByRoomId(String roomId) {
		return gsi1Index.query(QueryConditional.sortBeginsWith(
						Key.builder()
								.partitionValue("ROOM#" + roomId)
								.sortValue("WORDCHAIN#")
								.build()))
				.stream()
				.flatMap(page -> page.items().stream())
				.filter(WordChainSession::isActive)
				.findFirst();
	}

	/**
	 * 세션 삭제
	 */
	public void delete(String sessionId) {
		Key key = Key.builder()
				.partitionValue("WORDCHAIN#" + sessionId)
				.sortValue("METADATA")
				.build();
		table.deleteItem(key);
		logger.debug("Deleted WordChainSession: {}", sessionId);
	}

	/**
	 * 게임 종료 처리
	 */
	public void finishGame(String sessionId, long endedAt, long ttl) {
		findById(sessionId).ifPresent(session -> {
			session.setStatus("FINISHED");
			session.setEndedAt(endedAt);
			session.setTtl(ttl);
			save(session);
			logger.info("Finished WordChainSession: {}", sessionId);
		});
	}
}
