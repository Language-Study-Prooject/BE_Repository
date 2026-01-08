package com.mzc.secondproject.serverless.domain.vocabulary.repository;

import com.mzc.secondproject.serverless.domain.vocabulary.model.TestResult;
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

public class TestResultRepository {

    private static final Logger logger = LoggerFactory.getLogger(TestResultRepository.class);

    // Singleton 패턴으로 Cold Start 최적화
    private static final DynamoDbClient dynamoDbClient = DynamoDbClient.builder().build();
    private static final DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder()
            .dynamoDbClient(dynamoDbClient)
            .build();
    private static final String tableName = System.getenv("VOCAB_TABLE_NAME");

    private final DynamoDbTable<TestResult> table;

    public TestResultRepository() {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(TestResult.class));
    }

    public TestResult save(TestResult testResult) {
        logger.info("Saving test result: userId={}, testId={}", testResult.getUserId(), testResult.getTestId());
        table.putItem(testResult);
        return testResult;
    }

    public Optional<TestResult> findByUserIdAndTestId(String userId, String timestamp) {
        Key key = Key.builder()
                .partitionValue("TEST#" + userId)
                .sortValue("RESULT#" + timestamp)
                .build();

        TestResult testResult = table.getItem(key);
        return Optional.ofNullable(testResult);
    }

    /**
     * 사용자의 시험 결과 조회 - 최신순, 페이지네이션
     */
    public PaginatedResult<TestResult> findByUserIdWithPagination(String userId, int limit, String cursor) {
        QueryConditional queryConditional = QueryConditional
                .sortBeginsWith(Key.builder()
                        .partitionValue("TEST#" + userId)
                        .sortValue("RESULT#")
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

        Page<TestResult> page = table.query(requestBuilder.build()).iterator().next();
        String nextCursor = CursorUtil.encode(page.lastEvaluatedKey());

        return new PaginatedResult<>(page.items(), nextCursor);
    }
}
