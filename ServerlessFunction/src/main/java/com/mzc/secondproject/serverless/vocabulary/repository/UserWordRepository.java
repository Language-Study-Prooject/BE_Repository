package com.mzc.secondproject.serverless.vocabulary.repository;

import com.mzc.secondproject.serverless.vocabulary.model.UserWord;
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
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class UserWordRepository {

    private static final Logger logger = LoggerFactory.getLogger(UserWordRepository.class);

    // Singleton 패턴으로 Cold Start 최적화
    private static final DynamoDbClient dynamoDbClient = DynamoDbClient.builder().build();
    private static final DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder()
            .dynamoDbClient(dynamoDbClient)
            .build();
    private static final String tableName = System.getenv("VOCAB_TABLE_NAME");

    private final DynamoDbTable<UserWord> table;

    public UserWordRepository() {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(UserWord.class));
    }

    public UserWord save(UserWord userWord) {
        logger.info("Saving user word: userId={}, wordId={}", userWord.getUserId(), userWord.getWordId());
        table.putItem(userWord);
        return userWord;
    }

    public Optional<UserWord> findByUserIdAndWordId(String userId, String wordId) {
        Key key = Key.builder()
                .partitionValue("USER#" + userId)
                .sortValue("WORD#" + wordId)
                .build();

        UserWord userWord = table.getItem(key);
        return Optional.ofNullable(userWord);
    }

    /**
     * 사용자의 모든 단어 학습 상태 조회 - 페이지네이션
     */
    public UserWordPage findByUserIdWithPagination(String userId, int limit, String cursor) {
        QueryConditional queryConditional = QueryConditional
                .sortBeginsWith(Key.builder()
                        .partitionValue("USER#" + userId)
                        .sortValue("WORD#")
                        .build());

        QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .limit(limit);

        if (cursor != null && !cursor.isEmpty()) {
            Map<String, AttributeValue> exclusiveStartKey = decodeCursor(cursor);
            if (exclusiveStartKey != null) {
                requestBuilder.exclusiveStartKey(exclusiveStartKey);
            }
        }

        Page<UserWord> page = table.query(requestBuilder.build()).iterator().next();
        String nextCursor = encodeCursor(page.lastEvaluatedKey());

        return new UserWordPage(page.items(), nextCursor);
    }

    /**
     * 복습 예정 단어 조회 (오늘 이전 날짜) - 페이지네이션
     */
    public UserWordPage findReviewDueWords(String userId, String todayDate, int limit, String cursor) {
        QueryConditional queryConditional = QueryConditional
                .sortLessThanOrEqualTo(Key.builder()
                        .partitionValue("USER#" + userId + "#REVIEW")
                        .sortValue("DATE#" + todayDate)
                        .build());

        QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .limit(limit);

        if (cursor != null && !cursor.isEmpty()) {
            Map<String, AttributeValue> exclusiveStartKey = decodeCursor(cursor);
            if (exclusiveStartKey != null) {
                requestBuilder.exclusiveStartKey(exclusiveStartKey);
            }
        }

        DynamoDbIndex<UserWord> gsi1 = table.index("GSI1");
        Page<UserWord> page = gsi1.query(requestBuilder.build()).iterator().next();
        String nextCursor = encodeCursor(page.lastEvaluatedKey());

        return new UserWordPage(page.items(), nextCursor);
    }

    /**
     * 북마크된 단어만 조회 - FilterExpression 사용 (GSI 추가 없이 비용 최적화)
     */
    public UserWordPage findBookmarkedWords(String userId, int limit, String cursor) {
        QueryConditional queryConditional = QueryConditional
                .sortBeginsWith(Key.builder()
                        .partitionValue("USER#" + userId)
                        .sortValue("WORD#")
                        .build());

        Expression filterExpression = Expression.builder()
                .expression("bookmarked = :bookmarked")
                .putExpressionValue(":bookmarked", AttributeValue.builder().bool(true).build())
                .build();

        QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .filterExpression(filterExpression)
                .limit(limit);

        if (cursor != null && !cursor.isEmpty()) {
            Map<String, AttributeValue> exclusiveStartKey = decodeCursor(cursor);
            if (exclusiveStartKey != null) {
                requestBuilder.exclusiveStartKey(exclusiveStartKey);
            }
        }

        Page<UserWord> page = table.query(requestBuilder.build()).iterator().next();
        String nextCursor = encodeCursor(page.lastEvaluatedKey());

        return new UserWordPage(page.items(), nextCursor);
    }

    /**
     * 틀린 적 있는 단어만 조회 - FilterExpression 사용 (GSI 추가 없이 비용 최적화)
     */
    public UserWordPage findIncorrectWords(String userId, int limit, String cursor) {
        QueryConditional queryConditional = QueryConditional
                .sortBeginsWith(Key.builder()
                        .partitionValue("USER#" + userId)
                        .sortValue("WORD#")
                        .build());

        Expression filterExpression = Expression.builder()
                .expression("incorrectCount > :zero")
                .putExpressionValue(":zero", AttributeValue.builder().n("0").build())
                .build();

        QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .filterExpression(filterExpression)
                .limit(limit);

        if (cursor != null && !cursor.isEmpty()) {
            Map<String, AttributeValue> exclusiveStartKey = decodeCursor(cursor);
            if (exclusiveStartKey != null) {
                requestBuilder.exclusiveStartKey(exclusiveStartKey);
            }
        }

        Page<UserWord> page = table.query(requestBuilder.build()).iterator().next();
        String nextCursor = encodeCursor(page.lastEvaluatedKey());

        return new UserWordPage(page.items(), nextCursor);
    }

    /**
     * 상태별 단어 조회 - 페이지네이션
     */
    public UserWordPage findByUserIdAndStatus(String userId, String status, int limit, String cursor) {
        QueryConditional queryConditional = QueryConditional
                .keyEqualTo(Key.builder()
                        .partitionValue("USER#" + userId + "#STATUS")
                        .sortValue("STATUS#" + status)
                        .build());

        QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .limit(limit);

        if (cursor != null && !cursor.isEmpty()) {
            Map<String, AttributeValue> exclusiveStartKey = decodeCursor(cursor);
            if (exclusiveStartKey != null) {
                requestBuilder.exclusiveStartKey(exclusiveStartKey);
            }
        }

        DynamoDbIndex<UserWord> gsi2 = table.index("GSI2");
        Page<UserWord> page = gsi2.query(requestBuilder.build()).iterator().next();
        String nextCursor = encodeCursor(page.lastEvaluatedKey());

        return new UserWordPage(page.items(), nextCursor);
    }

    private String encodeCursor(Map<String, AttributeValue> lastEvaluatedKey) {
        if (lastEvaluatedKey == null || lastEvaluatedKey.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, AttributeValue> entry : lastEvaluatedKey.entrySet()) {
            if (sb.length() > 0) sb.append("|");
            sb.append(entry.getKey()).append("=").append(entry.getValue().s());
        }

        return Base64.getUrlEncoder().encodeToString(sb.toString().getBytes());
    }

    private Map<String, AttributeValue> decodeCursor(String cursor) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor));
            Map<String, AttributeValue> result = new HashMap<>();

            for (String pair : decoded.split("\\|")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    result.put(kv[0], AttributeValue.builder().s(kv[1]).build());
                }
            }

            return result.isEmpty() ? null : result;
        } catch (Exception e) {
            logger.error("Failed to decode cursor: {}", cursor, e);
            return null;
        }
    }

    public static class UserWordPage {
        private final List<UserWord> userWords;
        private final String nextCursor;

        public UserWordPage(List<UserWord> userWords, String nextCursor) {
            this.userWords = userWords;
            this.nextCursor = nextCursor;
        }

        public List<UserWord> getUserWords() {
            return userWords;
        }

        public String getNextCursor() {
            return nextCursor;
        }

        public boolean hasMore() {
            return nextCursor != null;
        }
    }
}
