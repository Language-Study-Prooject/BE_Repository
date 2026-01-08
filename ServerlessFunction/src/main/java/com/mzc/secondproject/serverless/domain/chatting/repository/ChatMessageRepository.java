package com.mzc.secondproject.serverless.domain.chatting.repository;

import com.mzc.secondproject.serverless.domain.chatting.model.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import com.mzc.secondproject.serverless.common.dto.PaginatedResult;
import com.mzc.secondproject.serverless.common.util.CursorUtil;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ChatMessageRepository {

    private static final Logger logger = LoggerFactory.getLogger(ChatMessageRepository.class);

    // Singleton 패턴으로 Cold Start 최적화
    private static final DynamoDbClient dynamoDbClient = DynamoDbClient.builder().build();
    private static final DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder()
            .dynamoDbClient(dynamoDbClient)
            .build();
    private static final String tableName = System.getenv("CHAT_TABLE_NAME");

    private final DynamoDbTable<ChatMessage> table;

    public ChatMessageRepository() {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(ChatMessage.class));
    }

    public ChatMessage save(ChatMessage message) {
        logger.info("Saving message to DynamoDB: {}", message.getMessageId());
        table.putItem(message);
        return message;
    }

    public Optional<ChatMessage> findByRoomIdAndMessageId(String roomId, String messageId) {
        // GSI2를 사용하여 messageId로 직접 조회 (풀스캔 방지)
        QueryConditional queryConditional = QueryConditional
                .keyEqualTo(Key.builder()
                        .partitionValue("MSG#" + messageId)
                        .sortValue("ROOM#" + roomId)
                        .build());

        return table.index("GSI2")
                .query(queryConditional)
                .stream()
                .flatMap(page -> page.items().stream())
                .findFirst();
    }

    /**
     * 채팅방 메시지 조회 - 최신순, 페이지네이션 지원
     * @param roomId 채팅방 ID
     * @param limit 조회 개수 (기본 20)
     * @param cursor Base64 인코딩된 커서 (무한스크롤용)
     * @return 메시지 목록과 다음 페이지 커서
     */
    public PaginatedResult<ChatMessage> findByRoomIdWithPagination(String roomId, int limit, String cursor) {
        QueryConditional queryConditional = QueryConditional
                .sortBeginsWith(Key.builder()
                        .partitionValue("ROOM#" + roomId)
                        .sortValue("MSG#")
                        .build());

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

        Page<ChatMessage> page = table.query(requestBuilder.build()).iterator().next();
        List<ChatMessage> messages = page.items();

        // 다음 페이지 커서 (Base64 인코딩)
        String nextCursor = CursorUtil.encode(page.lastEvaluatedKey());

        return new PaginatedResult<>(messages, nextCursor);
    }

    /**
     * 사용자별 메시지 조회 - 페이지네이션 지원 (OOM 방지)
     */
    public PaginatedResult<ChatMessage> findByUserIdWithPagination(String userId, int limit, String cursor) {
        QueryConditional queryConditional = QueryConditional
                .keyEqualTo(Key.builder().partitionValue("USER#" + userId).build());

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

        Page<ChatMessage> page = table.index("GSI1")
                .query(requestBuilder.build())
                .iterator()
                .next();

        String nextCursor = CursorUtil.encode(page.lastEvaluatedKey());
        return new PaginatedResult<>(page.items(), nextCursor);
    }
}
