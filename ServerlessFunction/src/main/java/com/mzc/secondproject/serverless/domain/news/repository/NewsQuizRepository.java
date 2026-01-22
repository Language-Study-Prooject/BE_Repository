package com.mzc.secondproject.serverless.domain.news.repository;

import com.mzc.secondproject.serverless.common.config.AwsClients;
import com.mzc.secondproject.serverless.common.config.EnvConfig;
import com.mzc.secondproject.serverless.domain.news.constants.NewsKey;
import com.mzc.secondproject.serverless.domain.news.model.NewsQuizResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 뉴스 퀴즈 결과 Repository
 */
public class NewsQuizRepository {

	private static final Logger logger = LoggerFactory.getLogger(NewsQuizRepository.class);
	private static final String TABLE_NAME = EnvConfig.getRequired("NEWS_TABLE_NAME");

	private final DynamoDbTable<NewsQuizResult> table;

	public NewsQuizRepository() {
		this(AwsClients.dynamoDbEnhanced());
	}

	public NewsQuizRepository(DynamoDbEnhancedClient enhancedClient) {
		this.table = enhancedClient.table(TABLE_NAME, TableSchema.fromBean(NewsQuizResult.class));
	}

	/**
	 * 퀴즈 결과 저장
	 */
	public void save(NewsQuizResult result) {
		table.putItem(result);
		logger.debug("퀴즈 결과 저장: userId={}, articleId={}, score={}",
				result.getUserId(), result.getArticleId(), result.getScore());
	}

	/**
	 * 퀴즈 결과 조회
	 */
	public Optional<NewsQuizResult> findByUserAndArticle(String userId, String articleId) {
		Key key = Key.builder()
				.partitionValue(NewsKey.userNewsPk(userId))
				.sortValue(NewsKey.quizSk(articleId))
				.build();

		NewsQuizResult result = table.getItem(key);
		return Optional.ofNullable(result);
	}

	/**
	 * 퀴즈 제출 여부 확인
	 */
	public boolean hasSubmitted(String userId, String articleId) {
		return findByUserAndArticle(userId, articleId).isPresent();
	}

	/**
	 * 사용자 퀴즈 결과 목록 조회
	 */
	public List<NewsQuizResult> getUserQuizResults(String userId, int limit) {
		QueryConditional queryConditional = QueryConditional.sortBeginsWith(
				Key.builder()
						.partitionValue(NewsKey.userNewsPk(userId))
						.sortValue("QUIZ#")
						.build()
		);

		QueryEnhancedRequest request = QueryEnhancedRequest.builder()
				.queryConditional(queryConditional)
				.scanIndexForward(false)
				.limit(limit)
				.build();

		List<NewsQuizResult> results = new ArrayList<>();
		for (Page<NewsQuizResult> page : table.query(request)) {
			results.addAll(page.items());
			if (results.size() >= limit) break;
		}

		return results.subList(0, Math.min(results.size(), limit));
	}

	/**
	 * 사용자 퀴즈 통계 조회
	 */
	public QuizStats getUserQuizStats(String userId) {
		QueryConditional queryConditional = QueryConditional.sortBeginsWith(
				Key.builder()
						.partitionValue(NewsKey.userNewsPk(userId))
						.sortValue("QUIZ#")
						.build()
		);

		int totalQuizzes = 0;
		int totalScore = 0;
		int perfectScores = 0;

		for (Page<NewsQuizResult> page : table.query(queryConditional)) {
			for (NewsQuizResult result : page.items()) {
				totalQuizzes++;
				totalScore += result.getScore();
				if (result.getScore() == 100) {
					perfectScores++;
				}
			}
		}

		int avgScore = totalQuizzes > 0 ? totalScore / totalQuizzes : 0;
		return new QuizStats(totalQuizzes, avgScore, perfectScores);
	}

	/**
	 * 퀴즈 통계 레코드
	 */
	public record QuizStats(
			int totalQuizzes,
			int avgScore,
			int perfectScores
	) {}
}
