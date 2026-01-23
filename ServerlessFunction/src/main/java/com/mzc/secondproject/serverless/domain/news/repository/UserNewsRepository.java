package com.mzc.secondproject.serverless.domain.news.repository;

import com.mzc.secondproject.serverless.common.config.AwsClients;
import com.mzc.secondproject.serverless.common.config.EnvConfig;
import com.mzc.secondproject.serverless.domain.news.constants.NewsKey;
import com.mzc.secondproject.serverless.domain.news.model.UserNewsRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * 사용자 뉴스 학습 기록 Repository
 */
public class UserNewsRepository {

	private static final Logger logger = LoggerFactory.getLogger(UserNewsRepository.class);
	private static final String TABLE_NAME = EnvConfig.getRequired("NEWS_TABLE_NAME");

	private final DynamoDbTable<UserNewsRecord> table;

	public UserNewsRepository() {
		this(AwsClients.dynamoDbEnhanced());
	}

	public UserNewsRepository(DynamoDbEnhancedClient enhancedClient) {
		this.table = enhancedClient.table(TABLE_NAME, TableSchema.fromBean(UserNewsRecord.class));
	}

	/**
	 * 읽기 기록 저장
	 */
	public void saveReadRecord(String userId, String articleId, String title, String level, String category) {
		String now = Instant.now().toString();
		String today = LocalDate.now().toString();

		UserNewsRecord record = UserNewsRecord.builder()
				.pk(NewsKey.userNewsPk(userId))
				.sk(NewsKey.readSk(articleId))
				.gsi1pk(NewsKey.userNewsStatPk(userId))
				.gsi1sk(today + "#READ")
				.userId(userId)
				.articleId(articleId)
				.type("READ")
				.articleTitle(title)
				.articleLevel(level)
				.articleCategory(category)
				.createdAt(now)
				.build();

		table.putItem(record);
		logger.debug("읽기 기록 저장: userId={}, articleId={}", userId, articleId);
	}

	/**
	 * 북마크 저장
	 */
	public void saveBookmark(String userId, String articleId, String title, String level, String category) {
		String now = Instant.now().toString();
		String today = LocalDate.now().toString();

		UserNewsRecord record = UserNewsRecord.builder()
				.pk(NewsKey.userNewsPk(userId))
				.sk(NewsKey.bookmarkSk(articleId))
				.gsi1pk(NewsKey.userNewsStatPk(userId))
				.gsi1sk(today + "#BOOKMARK")
				.userId(userId)
				.articleId(articleId)
				.type("BOOKMARK")
				.articleTitle(title)
				.articleLevel(level)
				.articleCategory(category)
				.createdAt(now)
				.build();

		table.putItem(record);
		logger.debug("북마크 저장: userId={}, articleId={}", userId, articleId);
	}

	/**
	 * 북마크 삭제
	 */
	public void deleteBookmark(String userId, String articleId) {
		Key key = Key.builder()
				.partitionValue(NewsKey.userNewsPk(userId))
				.sortValue(NewsKey.bookmarkSk(articleId))
				.build();

		table.deleteItem(key);
		logger.debug("북마크 삭제: userId={}, articleId={}", userId, articleId);
	}

	/**
	 * 북마크 여부 확인
	 */
	public boolean isBookmarked(String userId, String articleId) {
		Key key = Key.builder()
				.partitionValue(NewsKey.userNewsPk(userId))
				.sortValue(NewsKey.bookmarkSk(articleId))
				.build();

		return table.getItem(key) != null;
	}

	/**
	 * 읽기 기록 여부 확인
	 */
	public boolean hasRead(String userId, String articleId) {
		Key key = Key.builder()
				.partitionValue(NewsKey.userNewsPk(userId))
				.sortValue(NewsKey.readSk(articleId))
				.build();

		return table.getItem(key) != null;
	}

	/**
	 * 사용자 북마크 목록 조회
	 */
	public List<UserNewsRecord> getUserBookmarks(String userId, int limit) {
		QueryConditional queryConditional = QueryConditional.sortBeginsWith(
				Key.builder()
						.partitionValue(NewsKey.userNewsPk(userId))
						.sortValue("BOOKMARK#")
						.build()
		);

		QueryEnhancedRequest request = QueryEnhancedRequest.builder()
				.queryConditional(queryConditional)
				.scanIndexForward(false)
				.limit(limit)
				.build();

		List<UserNewsRecord> results = new ArrayList<>();
		for (Page<UserNewsRecord> page : table.query(request)) {
			results.addAll(page.items());
			if (results.size() >= limit) break;
		}

		return results.subList(0, Math.min(results.size(), limit));
	}

	/**
	 * 여러 기사의 북마크 여부 확인 (배치)
	 */
	public Set<String> getBookmarkedArticleIds(String userId, List<String> articleIds) {
		Set<String> bookmarkedIds = new HashSet<>();
		for (String articleId : articleIds) {
			if (isBookmarked(userId, articleId)) {
				bookmarkedIds.add(articleId);
			}
		}
		return bookmarkedIds;
	}

	/**
	 * 사용자 뉴스 통계 조회
	 */
	public NewsStats getUserStats(String userId) {
		QueryConditional queryConditional = QueryConditional.keyEqualTo(
				Key.builder().partitionValue(NewsKey.userNewsPk(userId)).build()
		);

		int totalRead = 0;
		int thisWeekRead = 0;
		int totalBookmarks = 0;
		Map<String, Integer> byLevel = new HashMap<>();
		Map<String, Integer> byCategory = new HashMap<>();

		LocalDate weekAgo = LocalDate.now().minusDays(7);

		for (Page<UserNewsRecord> page : table.query(queryConditional)) {
			for (UserNewsRecord record : page.items()) {
				if ("READ".equals(record.getType())) {
					totalRead++;

					// 이번 주 읽은 것
					if (record.getCreatedAt() != null) {
						LocalDate readDate = Instant.parse(record.getCreatedAt())
								.atZone(java.time.ZoneId.systemDefault()).toLocalDate();
						if (readDate.isAfter(weekAgo)) {
							thisWeekRead++;
						}
					}

					// 레벨별 통계
					String level = record.getArticleLevel();
					if (level != null) {
						byLevel.merge(level, 1, Integer::sum);
					}

					// 카테고리별 통계
					String category = record.getArticleCategory();
					if (category != null) {
						byCategory.merge(category, 1, Integer::sum);
					}
				} else if ("BOOKMARK".equals(record.getType())) {
					totalBookmarks++;
				}
			}
		}

		return new NewsStats(totalRead, thisWeekRead, totalBookmarks, byLevel, byCategory);
	}

	/**
	 * 뉴스 통계 레코드
	 */
	public record NewsStats(
			int totalRead,
			int thisWeekRead,
			int totalBookmarks,
			Map<String, Integer> byLevel,
			Map<String, Integer> byCategory
	) {}
}
