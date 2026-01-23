package com.mzc.secondproject.serverless.domain.news.repository;

import com.mzc.secondproject.serverless.common.config.AwsClients;
import com.mzc.secondproject.serverless.common.config.EnvConfig;
import com.mzc.secondproject.serverless.common.dto.PaginatedResult;
import com.mzc.secondproject.serverless.common.util.CursorUtil;
import com.mzc.secondproject.serverless.domain.news.constants.NewsKey;
import com.mzc.secondproject.serverless.domain.news.model.NewsArticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 뉴스 기사 Repository
 */
public class NewsArticleRepository {
	
	private static final Logger logger = LoggerFactory.getLogger(NewsArticleRepository.class);
	private static final String TABLE_NAME = EnvConfig.getRequired("NEWS_TABLE_NAME");
	
	private final DynamoDbEnhancedClient enhancedClient;
	private final DynamoDbTable<NewsArticle> table;
	
	/**
	 * 기본 생성자 (Lambda에서 사용)
	 */
	public NewsArticleRepository() {
		this(AwsClients.dynamoDbEnhanced());
	}
	
	/**
	 * 의존성 주입 생성자 (테스트 용이성)
	 */
	public NewsArticleRepository(DynamoDbEnhancedClient enhancedClient) {
		this.enhancedClient = enhancedClient;
		this.table = enhancedClient.table(TABLE_NAME, TableSchema.fromBean(NewsArticle.class));
	}
	
	/**
	 * 뉴스 기사 저장
	 */
	public NewsArticle save(NewsArticle article) {
		logger.info("Saving news article: {}", article.getArticleId());
		table.putItem(article);
		return article;
	}
	
	/**
	 * 뉴스 기사 조회 (날짜 + 기사ID)
	 */
	public Optional<NewsArticle> findByDateAndId(String date, String articleId) {
		Key key = Key.builder()
				.partitionValue(NewsKey.newsPk(date))
				.sortValue(NewsKey.articleSk(articleId))
				.build();
		
		NewsArticle article = table.getItem(key);
		return Optional.ofNullable(article);
	}
	
	/**
	 * 뉴스 기사 조회 (기사ID만으로 - GSI 활용 또는 Scan)
	 * 참고: 실제로는 articleId로 date를 알 수 있도록 설계하거나 GSI 추가 필요
	 */
	public Optional<NewsArticle> findById(String articleId) {
		Expression filterExpression = Expression.builder()
				.expression("articleId = :articleId AND begins_with(SK, :skPrefix)")
				.putExpressionValue(":articleId", AttributeValue.builder().s(articleId).build())
				.putExpressionValue(":skPrefix", AttributeValue.builder().s("ARTICLE#").build())
				.build();
		
		ScanEnhancedRequest request = ScanEnhancedRequest.builder()
				.filterExpression(filterExpression)
				.limit(1)
				.build();
		
		for (Page<NewsArticle> page : table.scan(request)) {
			List<NewsArticle> items = page.items();
			if (!items.isEmpty()) {
				return Optional.of(items.get(0));
			}
		}
		return Optional.empty();
	}
	
	/**
	 * 뉴스 기사 삭제
	 */
	public void delete(String date, String articleId) {
		Key key = Key.builder()
				.partitionValue(NewsKey.newsPk(date))
				.sortValue(NewsKey.articleSk(articleId))
				.build();
		
		table.deleteItem(key);
		logger.info("Deleted news article: {}", articleId);
	}
	
	/**
	 * 날짜별 뉴스 기사 조회 (페이지네이션)
	 */
	public PaginatedResult<NewsArticle> findByDate(String date, int limit, String cursor) {
		QueryConditional queryConditional = QueryConditional
				.keyEqualTo(Key.builder().partitionValue(NewsKey.newsPk(date)).build());
		
		QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
				.queryConditional(queryConditional)
				.scanIndexForward(false)  // 최신순 (SK 역순)
				.limit(limit);
		
		if (cursor != null && !cursor.isEmpty()) {
			Map<String, AttributeValue> exclusiveStartKey = CursorUtil.decode(cursor);
			if (exclusiveStartKey != null) {
				requestBuilder.exclusiveStartKey(exclusiveStartKey);
			}
		}
		
		Page<NewsArticle> page = table.query(requestBuilder.build()).iterator().next();
		String nextCursor = CursorUtil.encode(page.lastEvaluatedKey());
		
		return new PaginatedResult<>(page.items(), nextCursor);
	}
	
	/**
	 * 레벨별 뉴스 기사 조회 (GSI1 - 최신순)
	 */
	public PaginatedResult<NewsArticle> findByLevel(String level, int limit, String cursor) {
		QueryConditional queryConditional = QueryConditional
				.keyEqualTo(Key.builder().partitionValue(NewsKey.levelPk(level.toUpperCase())).build());
		
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
		
		DynamoDbIndex<NewsArticle> gsi1 = table.index("GSI1");
		Page<NewsArticle> page = gsi1.query(requestBuilder.build()).iterator().next();
		String nextCursor = CursorUtil.encode(page.lastEvaluatedKey());
		
		return new PaginatedResult<>(page.items(), nextCursor);
	}
	
	/**
	 * 카테고리별 뉴스 기사 조회 (GSI2 - 최신순)
	 */
	public PaginatedResult<NewsArticle> findByCategory(String category, int limit, String cursor) {
		QueryConditional queryConditional = QueryConditional
				.keyEqualTo(Key.builder().partitionValue(NewsKey.categoryPk(category.toUpperCase())).build());
		
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
		
		DynamoDbIndex<NewsArticle> gsi2 = table.index("GSI2");
		Page<NewsArticle> page = gsi2.query(requestBuilder.build()).iterator().next();
		String nextCursor = CursorUtil.encode(page.lastEvaluatedKey());
		
		return new PaginatedResult<>(page.items(), nextCursor);
	}
	
	/**
	 * 레벨 + 카테고리 필터 조회 (GSI1 쿼리 후 필터)
	 */
	public PaginatedResult<NewsArticle> findByLevelAndCategory(String level, String category, int limit, String cursor) {
		Expression filterExpression = Expression.builder()
				.expression("category = :category")
				.putExpressionValue(":category", AttributeValue.builder().s(category.toUpperCase()).build())
				.build();
		
		QueryConditional queryConditional = QueryConditional
				.keyEqualTo(Key.builder().partitionValue(NewsKey.levelPk(level.toUpperCase())).build());
		
		QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
				.queryConditional(queryConditional)
				.filterExpression(filterExpression)
				.scanIndexForward(false)
				.limit(limit * 2);  // 필터 적용되므로 넉넉히
		
		if (cursor != null && !cursor.isEmpty()) {
			Map<String, AttributeValue> exclusiveStartKey = CursorUtil.decode(cursor);
			if (exclusiveStartKey != null) {
				requestBuilder.exclusiveStartKey(exclusiveStartKey);
			}
		}
		
		DynamoDbIndex<NewsArticle> gsi1 = table.index("GSI1");
		List<NewsArticle> results = new ArrayList<>();
		Map<String, AttributeValue> lastKey = null;
		
		for (Page<NewsArticle> page : gsi1.query(requestBuilder.build())) {
			for (NewsArticle article : page.items()) {
				results.add(article);
				if (results.size() >= limit) break;
			}
			lastKey = page.lastEvaluatedKey();
			if (results.size() >= limit) break;
		}
		
		String nextCursor = results.size() >= limit ? CursorUtil.encode(lastKey) : null;
		return new PaginatedResult<>(results.subList(0, Math.min(results.size(), limit)), nextCursor);
	}
	
	/**
	 * 조회수 증가 (Atomic Update)
	 */
	public void incrementReadCount(String date, String articleId) {
		Map<String, AttributeValue> key = Map.of(
				"PK", AttributeValue.builder().s(NewsKey.newsPk(date)).build(),
				"SK", AttributeValue.builder().s(NewsKey.articleSk(articleId)).build()
		);
		
		Map<String, AttributeValue> values = Map.of(
				":zero", AttributeValue.builder().n("0").build(),
				":inc", AttributeValue.builder().n("1").build()
		);
		
		UpdateItemRequest request = UpdateItemRequest.builder()
				.tableName(TABLE_NAME)
				.key(key)
				.updateExpression("SET readCount = if_not_exists(readCount, :zero) + :inc")
				.expressionAttributeValues(values)
				.build();
		
		AwsClients.dynamoDb().updateItem(request);
		logger.debug("Incremented read count for article: {}", articleId);
	}
	
	/**
	 * 댓글수 증가 (Atomic Update)
	 */
	public void incrementCommentCount(String date, String articleId) {
		Map<String, AttributeValue> key = Map.of(
				"PK", AttributeValue.builder().s(NewsKey.newsPk(date)).build(),
				"SK", AttributeValue.builder().s(NewsKey.articleSk(articleId)).build()
		);
		
		Map<String, AttributeValue> values = Map.of(
				":zero", AttributeValue.builder().n("0").build(),
				":inc", AttributeValue.builder().n("1").build()
		);
		
		UpdateItemRequest request = UpdateItemRequest.builder()
				.tableName(TABLE_NAME)
				.key(key)
				.updateExpression("SET commentCount = if_not_exists(commentCount, :zero) + :inc")
				.expressionAttributeValues(values)
				.build();
		
		AwsClients.dynamoDb().updateItem(request);
	}
	
	/**
	 * 댓글수 감소 (Atomic Update)
	 */
	public void decrementCommentCount(String date, String articleId) {
		Map<String, AttributeValue> key = Map.of(
				"PK", AttributeValue.builder().s(NewsKey.newsPk(date)).build(),
				"SK", AttributeValue.builder().s(NewsKey.articleSk(articleId)).build()
		);
		
		Map<String, AttributeValue> values = Map.of(
				":one", AttributeValue.builder().n("1").build(),
				":dec", AttributeValue.builder().n("1").build()
		);
		
		UpdateItemRequest request = UpdateItemRequest.builder()
				.tableName(TABLE_NAME)
				.key(key)
				.updateExpression("SET commentCount = if_not_exists(commentCount, :one) - :dec")
				.expressionAttributeValues(values)
				.build();
		
		AwsClients.dynamoDb().updateItem(request);
	}
}
