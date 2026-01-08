package com.mzc.secondproject.serverless.domain.vocabulary.repository;

import com.mzc.secondproject.serverless.domain.vocabulary.model.DailyStudy;
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
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import com.mzc.secondproject.serverless.common.util.CursorUtil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DailyStudyRepository {

    private static final Logger logger = LoggerFactory.getLogger(DailyStudyRepository.class);

    // Singleton 패턴으로 Cold Start 최적화
    private static final DynamoDbClient dynamoDbClient = DynamoDbClient.builder().build();
    private static final DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder()
            .dynamoDbClient(dynamoDbClient)
            .build();
    private static final String tableName = System.getenv("VOCAB_TABLE_NAME");

    private final DynamoDbTable<DailyStudy> table;

    public DailyStudyRepository() {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(DailyStudy.class));
    }

    public DailyStudy save(DailyStudy dailyStudy) {
        logger.info("Saving daily study: userId={}, date={}", dailyStudy.getUserId(), dailyStudy.getDate());
        table.putItem(dailyStudy);
        return dailyStudy;
    }

    public Optional<DailyStudy> findByUserIdAndDate(String userId, String date) {
        Key key = Key.builder()
                .partitionValue("DAILY#" + userId)
                .sortValue("DATE#" + date)
                .build();

        DailyStudy dailyStudy = table.getItem(key);
        return Optional.ofNullable(dailyStudy);
    }

    /**
     * 사용자의 일일 학습 기록 조회 - 최신순, 페이지네이션
     */
    public DailyStudyPage findByUserIdWithPagination(String userId, int limit, String cursor) {
        QueryConditional queryConditional = QueryConditional
                .sortBeginsWith(Key.builder()
                        .partitionValue("DAILY#" + userId)
                        .sortValue("DATE#")
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

        Page<DailyStudy> page = table.query(requestBuilder.build()).iterator().next();
        String nextCursor = CursorUtil.encode(page.lastEvaluatedKey());

        return new DailyStudyPage(page.items(), nextCursor);
    }

    /**
     * 학습 완료 단어 추가 (UpdateExpression 사용 - N+1 방지)
     */
    public void addLearnedWord(String userId, String date, String wordId) {
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("PK", AttributeValue.builder().s("DAILY#" + userId).build());
        key.put("SK", AttributeValue.builder().s("DATE#" + date).build());

        Map<String, AttributeValue> expressionValues = new HashMap<>();
        expressionValues.put(":wordId", AttributeValue.builder().ss(wordId).build());
        expressionValues.put(":one", AttributeValue.builder().n("1").build());

        UpdateItemRequest updateRequest = UpdateItemRequest.builder()
                .tableName(tableName)
                .key(key)
                .updateExpression("ADD learnedWordIds :wordId, learnedCount :one")
                .expressionAttributeValues(expressionValues)
                .build();

        dynamoDbClient.updateItem(updateRequest);
        logger.info("Added learned word: userId={}, date={}, wordId={}", userId, date, wordId);
    }

    public static class DailyStudyPage {
        private final List<DailyStudy> dailyStudies;
        private final String nextCursor;

        public DailyStudyPage(List<DailyStudy> dailyStudies, String nextCursor) {
            this.dailyStudies = dailyStudies;
            this.nextCursor = nextCursor;
        }

        public List<DailyStudy> getDailyStudies() {
            return dailyStudies;
        }

        public String getNextCursor() {
            return nextCursor;
        }

        public boolean hasMore() {
            return nextCursor != null;
        }
    }
}
