package com.mzc.secondproject.serverless.domain.chatting.repository;

import com.mzc.secondproject.serverless.common.config.AwsClients;
import com.mzc.secondproject.serverless.common.config.EnvConfig;
import com.mzc.secondproject.serverless.domain.chatting.model.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ConnectionRepository {
	
	private static final Logger logger = LoggerFactory.getLogger(ConnectionRepository.class);
	private static final String TABLE_NAME = EnvConfig.getRequired("CHAT_TABLE_NAME");
	
	private final DynamoDbTable<Connection> table;
	
	/**
	 * 기본 생성자 (Lambda에서 사용)
	 */
	public ConnectionRepository() {
		this(AwsClients.dynamoDbEnhanced());
	}
	
	/**
	 * 의존성 주입 생성자 (테스트 용이성)
	 */
	public ConnectionRepository(DynamoDbEnhancedClient enhancedClient) {
		this.table = enhancedClient.table(TABLE_NAME, TableSchema.fromBean(Connection.class));
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
	 * GSI1: ROOM#{roomId}로 조회, GSI1SK가 CONN#으로 시작하는 항목만 반환
	 * (GSI1에 GameSession도 포함되어 있으므로 CONN# prefix로 필터링)
	 */
	public List<Connection> findByRoomId(String roomId) {
		// GSI1SK가 CONN#으로 시작하는 항목만 조회
		QueryConditional queryConditional = QueryConditional
				.sortBeginsWith(Key.builder()
						.partitionValue("ROOM#" + roomId)
						.sortValue("CONN#")
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
	
	/**
	 * 같은 방에서 사용자의 기존 연결 삭제 (중복 연결 방지)
	 * 새로고침 등으로 인한 중복 연결을 정리
	 */
	public void deleteUserConnectionsInRoom(String userId, String roomId) {
		List<Connection> userConnections = findByUserId(userId);
		
		int deletedCount = 0;
		for (Connection conn : userConnections) {
			if (roomId.equals(conn.getRoomId())) {
				delete(conn.getConnectionId());
				deletedCount++;
			}
		}
		
		if (deletedCount > 0) {
			logger.info("Deleted {} existing connections for user {} in room {}", deletedCount, userId, roomId);
		}
	}
}
