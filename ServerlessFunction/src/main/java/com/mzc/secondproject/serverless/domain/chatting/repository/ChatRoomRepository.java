package com.mzc.secondproject.serverless.domain.chatting.repository;

import com.mzc.secondproject.serverless.domain.chatting.model.ChatRoom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import com.mzc.secondproject.serverless.common.util.CursorUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ChatRoomRepository {

    private static final Logger logger = LoggerFactory.getLogger(ChatRoomRepository.class);

    // Singleton 패턴으로 Cold Start 최적화
    private static final DynamoDbClient dynamoDbClient = DynamoDbClient.builder().build();
    private static final DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder()
            .dynamoDbClient(dynamoDbClient)
            .build();
    private static final String tableName = System.getenv("CHAT_TABLE_NAME");

    private final DynamoDbTable<ChatRoom> table;

    public ChatRoomRepository() {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(ChatRoom.class));
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
     * @param limit 조회 개수 (기본 10)
     * @param cursor Base64 인코딩된 커서 (무한스크롤용)
     * @return 채팅방 목록과 다음 페이지 커서
     */
    public RoomPage findAllWithPagination(int limit, String cursor) {
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

        return new RoomPage(rooms, nextCursor);
    }

    /**
     * 레벨별 채팅방 조회 - 최신순, 페이지네이션 지원
     */
    public RoomPage findByLevelWithPagination(String level, int limit, String cursor) {
        QueryConditional queryConditional = QueryConditional
                .sortBeginsWith(Key.builder()
                        .partitionValue("ROOMS")
                        .sortValue(level + "#")
                        .build());

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

        return new RoomPage(rooms, nextCursor);
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
     * 채팅방 lastMessageAt 업데이트 (N+1 방지 - UpdateExpression 사용)
     */
    public void updateLastMessageAt(String roomId, String timestamp) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("PK", AttributeValue.builder().s("ROOM#" + roomId).build());
        key.put("SK", AttributeValue.builder().s("METADATA").build());

        Map<String, AttributeValue> expressionValues = new HashMap<>();
        expressionValues.put(":ts", AttributeValue.builder().s(timestamp).build());

        UpdateItemRequest updateRequest = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .updateExpression("SET lastMessageAt = :ts")
                .expressionAttributeValues(expressionValues)
                .build();

        dynamoDbClient.updateItem(updateRequest);
        logger.info("Updated lastMessageAt for room: {}", roomId);
    }

    /**
     * 페이지네이션 결과 클래스
     */
    public static class RoomPage {
        private final List<ChatRoom> rooms;
        private final String nextCursor;

        public RoomPage(List<ChatRoom> rooms, String nextCursor) {
            this.rooms = rooms;
            this.nextCursor = nextCursor;
        }

        public List<ChatRoom> getRooms() {
            return rooms;
        }

        public String getNextCursor() {
            return nextCursor;
        }

        public boolean hasMore() {
            return nextCursor != null;
        }
    }
}
