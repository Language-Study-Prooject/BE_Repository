package com.mzc.secondproject.serverless.domain.chatting.repository;

import com.mzc.secondproject.serverless.common.config.AwsClients;
import com.mzc.secondproject.serverless.common.config.EnvConfig;
import com.mzc.secondproject.serverless.common.dto.PaginatedResult;
import com.mzc.secondproject.serverless.common.util.CursorUtil;
import com.mzc.secondproject.serverless.domain.chatting.model.ChatRoom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ChatRoomRepository {
	
	private static final Logger logger = LoggerFactory.getLogger(ChatRoomRepository.class);
	private static final String TABLE_NAME = EnvConfig.getRequired("CHAT_TABLE_NAME");
	
	private final DynamoDbTable<ChatRoom> table;
	
	public ChatRoomRepository() {
		this.table = AwsClients.dynamoDbEnhanced().table(TABLE_NAME, TableSchema.fromBean(ChatRoom.class));
	}
	
	public ChatRoom save(ChatRoom room) {
		logger.info("Saving room to DynamoDB: {}", room.getRoomId());
		table.putItem(room);
		return room;
	}
	
	public Optional<ChatRoom> findById(String roomId) {
		Key key = Key.builder()
				.partitionValue("ROOM#" + roomId)
				.sortValue("METADATA")
				.build();
		
		ChatRoom room = table.getItem(key);
		return Optional.ofNullable(room);
	}
	
	/**
	 * 채팅방 목록 조회 - 최신순, 페이지네이션 지원
	 *
	 * @param limit  조회 개수 (기본 10)
	 * @param cursor Base64 인코딩된 커서 (무한스크롤용)
	 * @return 채팅방 목록과 다음 페이지 커서
	 */
	public PaginatedResult<ChatRoom> findAllWithPagination(int limit, String cursor) {
		QueryConditional queryConditional = QueryConditional
				.keyEqualTo(Key.builder().partitionValue("ROOMS").build());
		
		QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
				.queryConditional(queryConditional)
				.scanIndexForward(false)  // 최신순 (역순)
				.limit(limit);
		
		// 커서 기반 페이지네이션 (Base64 디코딩)
		if (cursor != null && !cursor.isEmpty()) {
			Map<String, AttributeValue> exclusiveStartKey = CursorUtil.decode(cursor);
			if (exclusiveStartKey != null) {
				requestBuilder.exclusiveStartKey(exclusiveStartKey);
			}
		}
		
		DynamoDbIndex<ChatRoom> gsi1 = table.index("GSI1");
		Page<ChatRoom> page = gsi1.query(requestBuilder.build()).iterator().next();
		List<ChatRoom> rooms = page.items();
		
		// 다음 페이지 커서 (Base64 인코딩)
		String nextCursor = CursorUtil.encode(page.lastEvaluatedKey());
		
		return new PaginatedResult<>(rooms, nextCursor);
	}
	
	/**
	 * 필터 조건으로 채팅방 조회 - 최신순, 페이지네이션 지원
	 * GSI1SK 포맷: {type}#{gameType}#{status}#{level}#{createdAt}
	 *
	 * @param type     방 타입 (CHAT, GAME) - nullable
	 * @param gameType 게임 타입 (CATCHMIND 등) - nullable
	 * @param status   방 상태 (WAITING, PLAYING, FINISHED) - nullable
	 * @param level    레벨 (beginner, intermediate, advanced) - nullable
	 * @param limit    조회 개수
	 * @param cursor   페이지네이션 커서
	 * @return 필터링된 채팅방 목록
	 */
	public PaginatedResult<ChatRoom> findByFilters(String type, String gameType, String status, String level, int limit, String cursor) {
		// GSI1SK prefix 생성: {type}#{gameType}#{status}#{level}#
		StringBuilder prefixBuilder = new StringBuilder();

		if (type != null && !type.isEmpty()) {
			prefixBuilder.append(type).append("#");
			if (gameType != null && !gameType.isEmpty()) {
				prefixBuilder.append(gameType).append("#");
				if (status != null && !status.isEmpty()) {
					prefixBuilder.append(status).append("#");
					if (level != null && !level.isEmpty()) {
						prefixBuilder.append(level).append("#");
					}
				}
			}
		}

		String prefix = prefixBuilder.toString();

		QueryConditional queryConditional;
		if (prefix.isEmpty()) {
			// 필터 없음 - 전체 조회
			queryConditional = QueryConditional.keyEqualTo(Key.builder().partitionValue("ROOMS").build());
		} else {
			// prefix로 필터링
			queryConditional = QueryConditional.sortBeginsWith(
					Key.builder()
							.partitionValue("ROOMS")
							.sortValue(prefix)
							.build()
			);
		}

		QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
				.queryConditional(queryConditional)
				.scanIndexForward(false)  // 최신순
				.limit(limit);

		if (cursor != null && !cursor.isEmpty()) {
			Map<String, AttributeValue> exclusiveStartKey = CursorUtil.decode(cursor);
			if (exclusiveStartKey != null) {
				requestBuilder.exclusiveStartKey(exclusiveStartKey);
			}
		}

		DynamoDbIndex<ChatRoom> gsi1 = table.index("GSI1");
		Page<ChatRoom> page = gsi1.query(requestBuilder.build()).iterator().next();
		List<ChatRoom> rooms = page.items();

		String nextCursor = CursorUtil.encode(page.lastEvaluatedKey());

		logger.info("Query with prefix '{}': found {} rooms", prefix, rooms.size());
		return new PaginatedResult<>(rooms, nextCursor);
	}

	/**
	 * 레벨별 채팅방 조회 - 최신순, 페이지네이션 지원
	 * @deprecated findByFilters 사용 권장
	 */
	@Deprecated
	public PaginatedResult<ChatRoom> findByLevelWithPagination(String level, int limit, String cursor) {
		return findByFilters(null, null, null, null, limit, cursor);
	}
	
	public void delete(String roomId) {
		Key key = Key.builder()
				.partitionValue("ROOM#" + roomId)
				.sortValue("METADATA")
				.build();
		
		table.deleteItem(key);
		logger.info("Deleted room: {}", roomId);
	}
	
	/**
	 * 방 상태 변경 시 GSI1SK도 함께 업데이트
	 * GSI1SK 포맷: {type}#{gameType}#{status}#{level}#{createdAt}
	 */
	public void updateStatus(ChatRoom room, String newStatus) {
		String oldGsi1sk = room.getGsi1sk();
		String[] parts = oldGsi1sk.split("#", 5);  // type, gameType, oldStatus, level, createdAt

		if (parts.length < 5) {
			logger.warn("Invalid GSI1SK format: {}", oldGsi1sk);
			// 폴백: 새 포맷으로 생성
			String type = room.getType() != null ? room.getType() : "CHAT";
			String gameType = room.getGameType() != null ? room.getGameType() : "-";
			String level = room.getLevel() != null ? room.getLevel() : "beginner";
			String createdAt = room.getCreatedAt();
			room.setGsi1sk(String.format("%s#%s#%s#%s#%s", type, gameType, newStatus, level, createdAt));
		} else {
			// 기존 포맷에서 status만 교체
			room.setGsi1sk(String.format("%s#%s#%s#%s#%s", parts[0], parts[1], newStatus, parts[3], parts[4]));
		}

		room.setStatus(newStatus);
		table.putItem(room);
		logger.info("Updated room {} status to {} (GSI1SK: {})", room.getRoomId(), newStatus, room.getGsi1sk());
	}

	/**
	 * 채팅방 lastMessageAt 업데이트 (N+1 방지 - UpdateExpression 사용)
	 */
	public void updateLastMessageAt(String roomId, String timestamp) {
		Map<String, AttributeValue> key = new HashMap<>();
		key.put("PK", AttributeValue.builder().s("ROOM#" + roomId).build());
		key.put("SK", AttributeValue.builder().s("METADATA").build());
		
		Map<String, AttributeValue> expressionValues = new HashMap<>();
		expressionValues.put(":ts", AttributeValue.builder().s(timestamp).build());
		
		UpdateItemRequest updateRequest = UpdateItemRequest.builder()
				.tableName(TABLE_NAME)
				.key(key)
				.updateExpression("SET lastMessageAt = :ts")
				.expressionAttributeValues(expressionValues)
				.build();
		
		AwsClients.dynamoDb().updateItem(updateRequest);
		logger.info("Updated lastMessageAt for room: {}", roomId);
	}
	
}
