package com.mzc.secondproject.serverless.domain.grammar.repository;

import com.mzc.secondproject.serverless.common.config.AwsClients;
import com.mzc.secondproject.serverless.common.config.EnvConfig;
import com.mzc.secondproject.serverless.domain.grammar.model.GrammarConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.util.Optional;

/**
 * Grammar WebSocket 연결 정보 Repository
 */
public class GrammarConnectionRepository {
	
	private static final Logger logger = LoggerFactory.getLogger(GrammarConnectionRepository.class);
	private static final String TABLE_NAME = EnvConfig.getRequired("CHAT_TABLE_NAME");
	
	private final DynamoDbTable<GrammarConnection> table;

	/**
	 * 기본 생성자 (Lambda에서 사용)
	 */
	public GrammarConnectionRepository() {
		this(AwsClients.dynamoDbEnhanced());
	}

	/**
	 * 의존성 주입 생성자 (테스트 용이성)
	 */
	public GrammarConnectionRepository(DynamoDbEnhancedClient enhancedClient) {
		this.table = enhancedClient.table(TABLE_NAME, TableSchema.fromBean(GrammarConnection.class));
	}
	
	/**
	 * 연결 정보 저장
	 */
	public void save(GrammarConnection connection) {
		table.putItem(connection);
		logger.info("Connection saved: connectionId={}, userId={}",
				connection.getConnectionId(), connection.getUserId());
	}
	
	/**
	 * connectionId로 연결 정보 조회
	 */
	public Optional<GrammarConnection> findByConnectionId(String connectionId) {
		Key key = Key.builder()
				.partitionValue(GrammarConnection.PK_PREFIX + connectionId)
				.sortValue(GrammarConnection.SK_METADATA)
				.build();
		
		GrammarConnection connection = table.getItem(key);
		return Optional.ofNullable(connection);
	}
	
	/**
	 * 연결 정보 삭제
	 */
	public void delete(String connectionId) {
		Key key = Key.builder()
				.partitionValue(GrammarConnection.PK_PREFIX + connectionId)
				.sortValue(GrammarConnection.SK_METADATA)
				.build();
		
		table.deleteItem(key);
		logger.info("Connection deleted: connectionId={}", connectionId);
	}
}
