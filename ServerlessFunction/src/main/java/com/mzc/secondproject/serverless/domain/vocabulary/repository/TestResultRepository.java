package com.mzc.secondproject.serverless.domain.vocabulary.repository;

import com.mzc.secondproject.serverless.common.config.AwsClients;
import com.mzc.secondproject.serverless.common.config.EnvConfig;
import com.mzc.secondproject.serverless.common.dto.PaginatedResult;
import com.mzc.secondproject.serverless.common.util.CursorUtil;
import com.mzc.secondproject.serverless.domain.vocabulary.model.TestResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;
import java.util.Optional;

public class TestResultRepository {
	
	private static final Logger logger = LoggerFactory.getLogger(TestResultRepository.class);
	private static final String TABLE_NAME = EnvConfig.getRequired("VOCAB_TABLE_NAME");
	
	private final DynamoDbTable<TestResult> table;

	/**
	 * 기본 생성자 (Lambda에서 사용)
	 */
	public TestResultRepository() {
		this(AwsClients.dynamoDbEnhanced());
	}

	/**
	 * 의존성 주입 생성자 (테스트 용이성)
	 */
	public TestResultRepository(DynamoDbEnhancedClient enhancedClient) {
		this.table = enhancedClient.table(TABLE_NAME, TableSchema.fromBean(TestResult.class));
	}
	
	public TestResult save(TestResult testResult) {
		logger.info("Saving test result: userId={}, testId={}", testResult.getUserId(), testResult.getTestId());
		table.putItem(testResult);
		return testResult;
	}
	
	public Optional<TestResult> findByUserIdAndTimestamp(String userId, String timestamp) {
		Key key = Key.builder()
				.partitionValue("TEST#" + userId)
				.sortValue("RESULT#" + timestamp)
				.build();
		
		TestResult testResult = table.getItem(key);
		return Optional.ofNullable(testResult);
	}
	
	public Optional<TestResult> findByUserIdAndTestId(String userId, String testId) {
		QueryConditional queryConditional = QueryConditional
				.sortBeginsWith(Key.builder()
						.partitionValue("TEST#" + userId)
						.sortValue("RESULT#")
						.build());
		
		Expression filterExpression = Expression.builder()
				.expression("testId = :testId")
				.putExpressionValue(":testId", AttributeValue.builder().s(testId).build())
				.build();
		
		QueryEnhancedRequest request = QueryEnhancedRequest.builder()
				.queryConditional(queryConditional)
				.filterExpression(filterExpression)
				.limit(1)
				.build();
		
		for (Page<TestResult> page : table.query(request)) {
			if (!page.items().isEmpty()) {
				return Optional.of(page.items().get(0));
			}
		}
		
		return Optional.empty();
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
