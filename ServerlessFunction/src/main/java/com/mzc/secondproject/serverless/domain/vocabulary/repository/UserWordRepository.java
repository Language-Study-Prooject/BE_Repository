package com.mzc.secondproject.serverless.domain.vocabulary.repository;

import com.mzc.secondproject.serverless.common.config.AwsClients;
import com.mzc.secondproject.serverless.common.config.EnvConfig;
import com.mzc.secondproject.serverless.common.dto.PaginatedResult;
import com.mzc.secondproject.serverless.common.util.CursorUtil;
import com.mzc.secondproject.serverless.domain.vocabulary.model.UserWord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;
import java.util.Optional;

public class UserWordRepository {
	
	private static final Logger logger = LoggerFactory.getLogger(UserWordRepository.class);
	private static final String TABLE_NAME = EnvConfig.getRequired("VOCAB_TABLE_NAME");
	
	private final DynamoDbTable<UserWord> table;
	
	/**
	 * 기본 생성자 (Lambda에서 사용)
	 */
	public UserWordRepository() {
		this(AwsClients.dynamoDbEnhanced());
	}
	
	/**
	 * 의존성 주입 생성자 (테스트 용이성)
	 */
	public UserWordRepository(DynamoDbEnhancedClient enhancedClient) {
		this.table = enhancedClient.table(TABLE_NAME, TableSchema.fromBean(UserWord.class));
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
	public PaginatedResult<UserWord> findByUserIdWithPagination(String userId, int limit, String cursor) {
		QueryConditional queryConditional = QueryConditional
				.sortBeginsWith(Key.builder()
						.partitionValue("USER#" + userId)
						.sortValue("WORD#")
						.build());
		
		QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
				.queryConditional(queryConditional)
				.limit(limit);
		
		if (cursor != null && !cursor.isEmpty()) {
			Map<String, AttributeValue> exclusiveStartKey = CursorUtil.decode(cursor);
			if (exclusiveStartKey != null) {
				requestBuilder.exclusiveStartKey(exclusiveStartKey);
			}
		}
		
		Page<UserWord> page = table.query(requestBuilder.build()).iterator().next();
		String nextCursor = CursorUtil.encode(page.lastEvaluatedKey());
		
		return new PaginatedResult<>(page.items(), nextCursor);
	}
	
	/**
	 * 복습 예정 단어 조회 (오늘 이전 날짜) - 페이지네이션
	 */
	public PaginatedResult<UserWord> findReviewDueWords(String userId, String todayDate, int limit, String cursor) {
		QueryConditional queryConditional = QueryConditional
				.sortLessThanOrEqualTo(Key.builder()
						.partitionValue("USER#" + userId + "#REVIEW")
						.sortValue("DATE#" + todayDate)
						.build());
		
		QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
				.queryConditional(queryConditional)
				.limit(limit);
		
		if (cursor != null && !cursor.isEmpty()) {
			Map<String, AttributeValue> exclusiveStartKey = CursorUtil.decode(cursor);
			if (exclusiveStartKey != null) {
				requestBuilder.exclusiveStartKey(exclusiveStartKey);
			}
		}
		
		DynamoDbIndex<UserWord> gsi1 = table.index("GSI1");
		Page<UserWord> page = gsi1.query(requestBuilder.build()).iterator().next();
		String nextCursor = CursorUtil.encode(page.lastEvaluatedKey());
		
		return new PaginatedResult<>(page.items(), nextCursor);
	}
	
	/**
	 * 북마크된 단어만 조회 - GSI3 Sparse Index 활용
	 */
	public PaginatedResult<UserWord> findBookmarkedWords(String userId, int limit, String cursor) {
		QueryConditional queryConditional = QueryConditional
				.sortBeginsWith(Key.builder()
						.partitionValue("USER#" + userId + "#BOOKMARKED")
						.sortValue("WORD#")
						.build());
		
		QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
				.queryConditional(queryConditional)
				.limit(limit);
		
		if (cursor != null && !cursor.isEmpty()) {
			Map<String, AttributeValue> exclusiveStartKey = CursorUtil.decode(cursor);
			if (exclusiveStartKey != null) {
				requestBuilder.exclusiveStartKey(exclusiveStartKey);
			}
		}
		
		DynamoDbIndex<UserWord> gsi3 = table.index("GSI3");
		Page<UserWord> page = gsi3.query(requestBuilder.build()).iterator().next();
		String nextCursor = CursorUtil.encode(page.lastEvaluatedKey());
		
		return new PaginatedResult<>(page.items(), nextCursor);
	}
	
	/**
	 * 틀린 적 있는 단어만 조회 - FilterExpression 사용 (GSI 추가 없이 비용 최적화)
	 */
	public PaginatedResult<UserWord> findIncorrectWords(String userId, int limit, String cursor) {
		return findIncorrectWords(userId, 1, limit, cursor);
	}
	
	/**
	 * 최소 N회 이상 틀린 단어 조회
	 */
	public PaginatedResult<UserWord> findIncorrectWords(String userId, int minCount, int limit, String cursor) {
		QueryConditional queryConditional = QueryConditional
				.sortBeginsWith(Key.builder()
						.partitionValue("USER#" + userId)
						.sortValue("WORD#")
						.build());
		
		Expression filterExpression = Expression.builder()
				.expression("incorrectCount >= :minCount")
				.putExpressionValue(":minCount", AttributeValue.builder().n(String.valueOf(minCount)).build())
				.build();
		
		QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
				.queryConditional(queryConditional)
				.filterExpression(filterExpression)
				.limit(limit * 3);  // FilterExpression 적용되므로 넉넉히
		
		if (cursor != null && !cursor.isEmpty()) {
			Map<String, AttributeValue> exclusiveStartKey = CursorUtil.decode(cursor);
			if (exclusiveStartKey != null) {
				requestBuilder.exclusiveStartKey(exclusiveStartKey);
			}
		}
		
		Page<UserWord> page = table.query(requestBuilder.build()).iterator().next();
		String nextCursor = CursorUtil.encode(page.lastEvaluatedKey());
		
		return new PaginatedResult<>(page.items(), nextCursor);
	}
	
	/**
	 * 상태별 단어 조회 - 페이지네이션
	 */
	public PaginatedResult<UserWord> findByUserIdAndStatus(String userId, String status, int limit, String cursor) {
		QueryConditional queryConditional = QueryConditional
				.keyEqualTo(Key.builder()
						.partitionValue("USER#" + userId + "#STATUS")
						.sortValue("STATUS#" + status)
						.build());
		
		QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
				.queryConditional(queryConditional)
				.limit(limit);
		
		if (cursor != null && !cursor.isEmpty()) {
			Map<String, AttributeValue> exclusiveStartKey = CursorUtil.decode(cursor);
			if (exclusiveStartKey != null) {
				requestBuilder.exclusiveStartKey(exclusiveStartKey);
			}
		}
		
		DynamoDbIndex<UserWord> gsi2 = table.index("GSI2");
		Page<UserWord> page = gsi2.query(requestBuilder.build()).iterator().next();
		String nextCursor = CursorUtil.encode(page.lastEvaluatedKey());
		
		return new PaginatedResult<>(page.items(), nextCursor);
	}
}
