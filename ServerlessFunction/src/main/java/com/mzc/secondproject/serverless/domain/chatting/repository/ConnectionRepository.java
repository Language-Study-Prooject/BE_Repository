package com.mzc.secondproject.serverless.domain.chatting.repository;

import com.mzc.secondproject.serverless.common.config.AwsClients;
import com.mzc.secondproject.serverless.domain.chatting.model.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ConnectionRepository {
	
	private static final Logger logger = LoggerFactory.getLogger(ConnectionRepository.class);
	private static final String TABLE_NAME = System.getenv("CHAT_TABLE_NAME");
	
	private final DynamoDbTable<Connection> table;
	
	public ConnectionRepository() {
		this.table = AwsClients.dynamoDbEnhanced().table(TABLE_NAME, TableSchema.fromBean(Connection.class));
	}
	
	public Connection save(Connection connection) {
		logger.info("Saving connection: {} for user: {} in room: {}",
				connection.getConnectionId(), connection.getUserId(), connection.getRoomId());
		table.putItem(connection);
		return connection;
	}
	
	public void delete(String connectionId) {
		Key key = Key.builder()
				.partitionValue("CONN#" + connectionId)
				.sortValue("METADATA")
				.build();
		
		table.deleteItem(key);
		logger.info("Deleted connection: {}", connectionId);
	}
	
	public Optional<Connection> findByConnectionId(String connectionId) {
		Key key = Key.builder()
				.partitionValue("CONN#" + connectionId)
				.sortValue("METADATA")
				.build();
		
		Connection connection = table.getItem(key);
		return Optional.ofNullable(connection);
	}
	
	/**
	 * 채팅방의 모든 연결 조회 (브로드캐스트용)
	 * GSI1: ROOM#{roomId}로 조회
	 */
	public List<Connection> findByRoomId(String roomId) {
		QueryConditional queryConditional = QueryConditional
				.keyEqualTo(Key.builder()
						.partitionValue("ROOM#" + roomId)
						.build());
		
		QueryEnhancedRequest request = QueryEnhancedRequest.builder()
				.queryConditional(queryConditional)
				.build();
		
		DynamoDbIndex<Connection> gsi1 = table.index("GSI1");
		
		return gsi1.query(request).stream()
				.flatMap(page -> page.items().stream())
				.collect(Collectors.toList());
	}
	
	/**
	 * 사용자의 모든 연결 조회 (다중 기기 지원)
	 * GSI2: USER#{userId}로 조회
	 */
	public List<Connection> findByUserId(String userId) {
		QueryConditional queryConditional = QueryConditional
				.keyEqualTo(Key.builder()
						.partitionValue("USER#" + userId)
						.build());
		
		QueryEnhancedRequest request = QueryEnhancedRequest.builder()
				.queryConditional(queryConditional)
				.build();
		
		DynamoDbIndex<Connection> gsi2 = table.index("GSI2");
		
		return gsi2.query(request).stream()
				.flatMap(page -> page.items().stream())
				.collect(Collectors.toList());
	}
}
