package com.mzc.secondproject.serverless.domain.stats.repository;

import com.mzc.secondproject.serverless.common.config.AwsClients;
import com.mzc.secondproject.serverless.common.config.EnvConfig;
import com.mzc.secondproject.serverless.common.dto.PaginatedResult;
import com.mzc.secondproject.serverless.common.util.CursorUtil;
import com.mzc.secondproject.serverless.domain.stats.constants.StatsKey;
import com.mzc.secondproject.serverless.domain.stats.model.UserStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.*;

/**
 * 사용자 학습 통계 Repository
 * Atomic Counter 패턴을 사용하여 Scan 없이 통계 업데이트
 */
public class UserStatsRepository {
	
	private static final Logger logger = LoggerFactory.getLogger(UserStatsRepository.class);
	private static final String TABLE_NAME = EnvConfig.getRequired("VOCAB_TABLE_NAME");
	
	private final DynamoDbTable<UserStats> table;
	
	/**
	 * 기본 생성자 (Lambda에서 사용)
	 */
	public UserStatsRepository() {
		this(AwsClients.dynamoDbEnhanced());
	}
	
	/**
	 * 의존성 주입 생성자 (테스트 용이성)
	 */
	public UserStatsRepository(DynamoDbEnhancedClient enhancedClient) {
		this.table = enhancedClient.table(TABLE_NAME, TableSchema.fromBean(UserStats.class));
	}
	
	/**
	 * 특정 기간의 통계 조회
	 */
	public Optional<UserStats> findByUserIdAndPeriod(String userId, String sk) {
		Key key = Key.builder()
				.partitionValue(StatsKey.userStatsPk(userId))
				.sortValue(sk)
				.build();
		
		UserStats stats = table.getItem(key);
		return Optional.ofNullable(stats);
	}
	
	/**
	 * 일별 통계 조회
	 */
	public Optional<UserStats> findDailyStats(String userId, String date) {
		return findByUserIdAndPeriod(userId, StatsKey.statsDailySk(date));
	}
	
	/**
	 * 주별 통계 조회
	 */
	public Optional<UserStats> findWeeklyStats(String userId, String yearWeek) {
		return findByUserIdAndPeriod(userId, StatsKey.statsWeeklySk(yearWeek));
	}
	
	/**
	 * 월별 통계 조회
	 */
	public Optional<UserStats> findMonthlyStats(String userId, String yearMonth) {
		return findByUserIdAndPeriod(userId, StatsKey.statsMonthlySk(yearMonth));
	}
	
	/**
	 * 전체 통계 조회
	 */
	public Optional<UserStats> findTotalStats(String userId) {
		return findByUserIdAndPeriod(userId, StatsKey.statsTotalSk());
	}
	
	/**
	 * 최근 N일 일별 통계 조회
	 */
	public PaginatedResult<UserStats> findRecentDailyStats(String userId, int limit, String cursor) {
		QueryConditional queryConditional = QueryConditional
				.sortBeginsWith(Key.builder()
						.partitionValue(StatsKey.userStatsPk(userId))
						.sortValue(StatsKey.STATS_DAILY)
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
		
		Page<UserStats> page = table.query(requestBuilder.build()).iterator().next();
		String nextCursor = CursorUtil.encode(page.lastEvaluatedKey());
		
		return new PaginatedResult<>(page.items(), nextCursor);
	}
	
	/**
	 * 테스트 결과 통계 Atomic 업데이트
	 * 일/주/월/전체 통계를 한 번에 업데이트
	 */
	public void incrementTestStats(String userId, int correctAnswers, int incorrectAnswers) {
		String today = LocalDate.now().toString();
		String yearWeek = getYearWeek();
		String yearMonth = getYearMonth();
		
		List<String> sortKeys = List.of(
				StatsKey.statsDailySk(today),
				StatsKey.statsWeeklySk(yearWeek),
				StatsKey.statsMonthlySk(yearMonth),
				StatsKey.statsTotalSk()
		);
		
		String pk = StatsKey.userStatsPk(userId);
		String now = Instant.now().toString();
		int totalQuestions = correctAnswers + incorrectAnswers;
		
		for (String sk : sortKeys) {
			updateTestStats(pk, sk, correctAnswers, incorrectAnswers, totalQuestions, now);
		}
		
		logger.info("Incremented test stats: userId={}, correct={}, incorrect={}",
				userId, correctAnswers, incorrectAnswers);
	}
	
	private void updateTestStats(String pk, String sk, int correct, int incorrect, int total, String now) {
		Map<String, AttributeValue> key = new HashMap<>();
		key.put("PK", AttributeValue.builder().s(pk).build());
		key.put("SK", AttributeValue.builder().s(sk).build());
		
		Map<String, AttributeValue> values = new HashMap<>();
		values.put(":correct", AttributeValue.builder().n(String.valueOf(correct)).build());
		values.put(":incorrect", AttributeValue.builder().n(String.valueOf(incorrect)).build());
		values.put(":total", AttributeValue.builder().n(String.valueOf(total)).build());
		values.put(":one", AttributeValue.builder().n("1").build());
		values.put(":zero", AttributeValue.builder().n("0").build());
		values.put(":now", AttributeValue.builder().s(now).build());
		
		String updateExpression = "SET " +
				"correctAnswers = if_not_exists(correctAnswers, :zero) + :correct, " +
				"incorrectAnswers = if_not_exists(incorrectAnswers, :zero) + :incorrect, " +
				"questionsAnswered = if_not_exists(questionsAnswered, :zero) + :total, " +
				"testsCompleted = if_not_exists(testsCompleted, :zero) + :one, " +
				"updatedAt = :now, " +
				"createdAt = if_not_exists(createdAt, :now)";
		
		UpdateItemRequest request = UpdateItemRequest.builder()
				.tableName(TABLE_NAME)
				.key(key)
				.updateExpression(updateExpression)
				.expressionAttributeValues(values)
				.build();
		
		AwsClients.dynamoDb().updateItem(request);
	}
	
	/**
	 * 학습 완료 단어 수 Atomic 업데이트
	 */
	public void incrementWordsLearned(String userId, int newWords, int reviewedWords) {
		String today = LocalDate.now().toString();
		String yearWeek = getYearWeek();
		String yearMonth = getYearMonth();
		
		List<String> sortKeys = List.of(
				StatsKey.statsDailySk(today),
				StatsKey.statsWeeklySk(yearWeek),
				StatsKey.statsMonthlySk(yearMonth),
				StatsKey.statsTotalSk()
		);
		
		String pk = StatsKey.userStatsPk(userId);
		String now = Instant.now().toString();
		
		for (String sk : sortKeys) {
			updateWordsLearned(pk, sk, newWords, reviewedWords, now);
		}
		
		logger.info("Incremented words learned: userId={}, new={}, reviewed={}",
				userId, newWords, reviewedWords);
	}
	
	private void updateWordsLearned(String pk, String sk, int newWords, int reviewedWords, String now) {
		Map<String, AttributeValue> key = new HashMap<>();
		key.put("PK", AttributeValue.builder().s(pk).build());
		key.put("SK", AttributeValue.builder().s(sk).build());
		
		Map<String, AttributeValue> values = new HashMap<>();
		values.put(":new", AttributeValue.builder().n(String.valueOf(newWords)).build());
		values.put(":reviewed", AttributeValue.builder().n(String.valueOf(reviewedWords)).build());
		values.put(":zero", AttributeValue.builder().n("0").build());
		values.put(":now", AttributeValue.builder().s(now).build());
		
		String updateExpression = "SET " +
				"newWordsLearned = if_not_exists(newWordsLearned, :zero) + :new, " +
				"wordsReviewed = if_not_exists(wordsReviewed, :zero) + :reviewed, " +
				"updatedAt = :now, " +
				"createdAt = if_not_exists(createdAt, :now)";
		
		UpdateItemRequest request = UpdateItemRequest.builder()
				.tableName(TABLE_NAME)
				.key(key)
				.updateExpression(updateExpression)
				.expressionAttributeValues(values)
				.build();
		
		AwsClients.dynamoDb().updateItem(request);
	}
	
	/**
	 * Streak(연속 학습일) 업데이트
	 */
	public void updateStreak(String userId, int currentStreak, int longestStreak, String lastStudyDate) {
		String pk = StatsKey.userStatsPk(userId);
		String sk = StatsKey.statsTotalSk();
		String now = Instant.now().toString();
		
		Map<String, AttributeValue> key = new HashMap<>();
		key.put("PK", AttributeValue.builder().s(pk).build());
		key.put("SK", AttributeValue.builder().s(sk).build());
		
		Map<String, AttributeValue> values = new HashMap<>();
		values.put(":current", AttributeValue.builder().n(String.valueOf(currentStreak)).build());
		values.put(":longest", AttributeValue.builder().n(String.valueOf(longestStreak)).build());
		values.put(":lastDate", AttributeValue.builder().s(lastStudyDate).build());
		values.put(":now", AttributeValue.builder().s(now).build());
		
		String updateExpression = "SET " +
				"currentStreak = :current, " +
				"longestStreak = :longest, " +
				"lastStudyDate = :lastDate, " +
				"updatedAt = :now, " +
				"createdAt = if_not_exists(createdAt, :now)";
		
		UpdateItemRequest request = UpdateItemRequest.builder()
				.tableName(TABLE_NAME)
				.key(key)
				.updateExpression(updateExpression)
				.expressionAttributeValues(values)
				.build();
		
		AwsClients.dynamoDb().updateItem(request);
		logger.info("Updated streak: userId={}, current={}, longest={}", userId, currentStreak, longestStreak);
	}
	
	/**
	 * 게임 통계 Atomic 업데이트
	 */
	public void incrementGameStats(String userId, int gamesPlayed, int gamesWon,
	                               int correctGuesses, int totalScore, int quickGuesses, int perfectDraws) {
		String pk = StatsKey.userStatsPk(userId);
		String sk = StatsKey.statsTotalSk();
		String now = Instant.now().toString();
		
		Map<String, AttributeValue> key = new HashMap<>();
		key.put("PK", AttributeValue.builder().s(pk).build());
		key.put("SK", AttributeValue.builder().s(sk).build());
		
		Map<String, AttributeValue> values = new HashMap<>();
		values.put(":gamesPlayed", AttributeValue.builder().n(String.valueOf(gamesPlayed)).build());
		values.put(":gamesWon", AttributeValue.builder().n(String.valueOf(gamesWon)).build());
		values.put(":correctGuesses", AttributeValue.builder().n(String.valueOf(correctGuesses)).build());
		values.put(":totalScore", AttributeValue.builder().n(String.valueOf(totalScore)).build());
		values.put(":quickGuesses", AttributeValue.builder().n(String.valueOf(quickGuesses)).build());
		values.put(":perfectDraws", AttributeValue.builder().n(String.valueOf(perfectDraws)).build());
		values.put(":zero", AttributeValue.builder().n("0").build());
		values.put(":now", AttributeValue.builder().s(now).build());
		
		String updateExpression = "SET " +
				"gamesPlayed = if_not_exists(gamesPlayed, :zero) + :gamesPlayed, " +
				"gamesWon = if_not_exists(gamesWon, :zero) + :gamesWon, " +
				"correctGuesses = if_not_exists(correctGuesses, :zero) + :correctGuesses, " +
				"totalGameScore = if_not_exists(totalGameScore, :zero) + :totalScore, " +
				"quickGuesses = if_not_exists(quickGuesses, :zero) + :quickGuesses, " +
				"perfectDraws = if_not_exists(perfectDraws, :zero) + :perfectDraws, " +
				"updatedAt = :now, " +
				"createdAt = if_not_exists(createdAt, :now)";
		
		UpdateItemRequest request = UpdateItemRequest.builder()
				.tableName(TABLE_NAME)
				.key(key)
				.updateExpression(updateExpression)
				.expressionAttributeValues(values)
				.build();
		
		AwsClients.dynamoDb().updateItem(request);
		logger.info("Incremented game stats: userId={}, gamesPlayed={}, gamesWon={}, correctGuesses={}",
				userId, gamesPlayed, gamesWon, correctGuesses);
	}
	
	/**
	 * 뉴스 읽기 통계 Atomic 업데이트 (TOTAL + DAILY)
	 */
	public UserStats incrementNewsReadStats(String userId) {
		String today = LocalDate.now().toString();
		String pk = StatsKey.userStatsPk(userId);
		String now = Instant.now().toString();
		
		// 먼저 현재 통계 조회 (streak 계산용)
		UserStats currentStats = findTotalStats(userId).orElse(null);
		String lastNewsReadDate = currentStats != null ? currentStats.getLastNewsReadDate() : null;
		
		// 연속 읽기 계산
		int currentStreak = 1;
		if (lastNewsReadDate != null) {
			LocalDate lastDate = LocalDate.parse(lastNewsReadDate);
			LocalDate todayDate = LocalDate.now();
			if (lastDate.equals(todayDate.minusDays(1))) {
				// 어제 읽었으면 streak 증가
				currentStreak = (currentStats.getNewsStreak() != null ? currentStats.getNewsStreak() : 0) + 1;
			} else if (lastDate.equals(todayDate)) {
				// 오늘 이미 읽었으면 streak 유지
				currentStreak = currentStats.getNewsStreak() != null ? currentStats.getNewsStreak() : 1;
			}
			// 그 외의 경우는 streak 1로 초기화
		}
		
		Map<String, AttributeValue> values = new HashMap<>();
		values.put(":one", AttributeValue.builder().n("1").build());
		values.put(":zero", AttributeValue.builder().n("0").build());
		values.put(":streak", AttributeValue.builder().n(String.valueOf(currentStreak)).build());
		values.put(":today", AttributeValue.builder().s(today).build());
		values.put(":now", AttributeValue.builder().s(now).build());
		
		// 1. TOTAL 통계 업데이트
		Map<String, AttributeValue> totalKey = new HashMap<>();
		totalKey.put("PK", AttributeValue.builder().s(pk).build());
		totalKey.put("SK", AttributeValue.builder().s(StatsKey.statsTotalSk()).build());
		
		String totalUpdateExpression = "SET " +
				"newsRead = if_not_exists(newsRead, :zero) + :one, " +
				"newsStreak = :streak, " +
				"lastNewsReadDate = :today, " +
				"updatedAt = :now, " +
				"createdAt = if_not_exists(createdAt, :now)";
		
		UpdateItemRequest totalRequest = UpdateItemRequest.builder()
				.tableName(TABLE_NAME)
				.key(totalKey)
				.updateExpression(totalUpdateExpression)
				.expressionAttributeValues(values)
				.returnValues(software.amazon.awssdk.services.dynamodb.model.ReturnValue.ALL_NEW)
				.build();
		
		AwsClients.dynamoDb().updateItem(totalRequest);
		
		// 2. DAILY 통계 업데이트
		Map<String, AttributeValue> dailyKey = new HashMap<>();
		dailyKey.put("PK", AttributeValue.builder().s(pk).build());
		dailyKey.put("SK", AttributeValue.builder().s(StatsKey.statsDailySk(today)).build());
		
		Map<String, AttributeValue> dailyValues = new HashMap<>();
		dailyValues.put(":one", AttributeValue.builder().n("1").build());
		dailyValues.put(":zero", AttributeValue.builder().n("0").build());
		dailyValues.put(":now", AttributeValue.builder().s(now).build());
		dailyValues.put(":today", AttributeValue.builder().s(today).build());
		
		String dailyUpdateExpression = "SET " +
				"newsRead = if_not_exists(newsRead, :zero) + :one, " +
				"updatedAt = :now, " +
				"createdAt = if_not_exists(createdAt, :now), " +
				"period = if_not_exists(period, :today)";
		
		UpdateItemRequest dailyRequest = UpdateItemRequest.builder()
				.tableName(TABLE_NAME)
				.key(dailyKey)
				.updateExpression(dailyUpdateExpression)
				.expressionAttributeValues(dailyValues)
				.build();
		
		AwsClients.dynamoDb().updateItem(dailyRequest);
		logger.info("Incremented news read stats (TOTAL + DAILY): userId={}, streak={}", userId, currentStreak);
		
		return findTotalStats(userId).orElse(null);
	}
	
	/**
	 * 뉴스 퀴즈 통계 Atomic 업데이트 (TOTAL + DAILY)
	 */
	public UserStats incrementNewsQuizStats(String userId, boolean isPerfect) {
		String today = LocalDate.now().toString();
		String pk = StatsKey.userStatsPk(userId);
		String now = Instant.now().toString();
		
		Map<String, AttributeValue> values = new HashMap<>();
		values.put(":one", AttributeValue.builder().n("1").build());
		values.put(":zero", AttributeValue.builder().n("0").build());
		values.put(":perfect", AttributeValue.builder().n(isPerfect ? "1" : "0").build());
		values.put(":now", AttributeValue.builder().s(now).build());
		
		// 1. TOTAL 통계 업데이트
		Map<String, AttributeValue> totalKey = new HashMap<>();
		totalKey.put("PK", AttributeValue.builder().s(pk).build());
		totalKey.put("SK", AttributeValue.builder().s(StatsKey.statsTotalSk()).build());
		
		String totalUpdateExpression = "SET " +
				"newsQuizCompleted = if_not_exists(newsQuizCompleted, :zero) + :one, " +
				"newsQuizPerfect = if_not_exists(newsQuizPerfect, :zero) + :perfect, " +
				"updatedAt = :now, " +
				"createdAt = if_not_exists(createdAt, :now)";
		
		UpdateItemRequest totalRequest = UpdateItemRequest.builder()
				.tableName(TABLE_NAME)
				.key(totalKey)
				.updateExpression(totalUpdateExpression)
				.expressionAttributeValues(values)
				.returnValues(software.amazon.awssdk.services.dynamodb.model.ReturnValue.ALL_NEW)
				.build();
		
		AwsClients.dynamoDb().updateItem(totalRequest);
		
		// 2. DAILY 통계 업데이트
		Map<String, AttributeValue> dailyKey = new HashMap<>();
		dailyKey.put("PK", AttributeValue.builder().s(pk).build());
		dailyKey.put("SK", AttributeValue.builder().s(StatsKey.statsDailySk(today)).build());
		
		Map<String, AttributeValue> dailyValues = new HashMap<>();
		dailyValues.put(":one", AttributeValue.builder().n("1").build());
		dailyValues.put(":zero", AttributeValue.builder().n("0").build());
		dailyValues.put(":perfect", AttributeValue.builder().n(isPerfect ? "1" : "0").build());
		dailyValues.put(":now", AttributeValue.builder().s(now).build());
		dailyValues.put(":today", AttributeValue.builder().s(today).build());
		
		String dailyUpdateExpression = "SET " +
				"newsQuizCompleted = if_not_exists(newsQuizCompleted, :zero) + :one, " +
				"newsQuizPerfect = if_not_exists(newsQuizPerfect, :zero) + :perfect, " +
				"updatedAt = :now, " +
				"createdAt = if_not_exists(createdAt, :now), " +
				"period = if_not_exists(period, :today)";
		
		UpdateItemRequest dailyRequest = UpdateItemRequest.builder()
				.tableName(TABLE_NAME)
				.key(dailyKey)
				.updateExpression(dailyUpdateExpression)
				.expressionAttributeValues(dailyValues)
				.build();
		
		AwsClients.dynamoDb().updateItem(dailyRequest);
		logger.info("Incremented news quiz stats (TOTAL + DAILY): userId={}, isPerfect={}", userId, isPerfect);
		
		return findTotalStats(userId).orElse(null);
	}
	
	/**
	 * 뉴스 단어 수집 통계 Atomic 업데이트 (TOTAL + DAILY)
	 */
	public UserStats incrementNewsWordStats(String userId, int wordCount) {
		String today = LocalDate.now().toString();
		String pk = StatsKey.userStatsPk(userId);
		String now = Instant.now().toString();
		
		Map<String, AttributeValue> values = new HashMap<>();
		values.put(":count", AttributeValue.builder().n(String.valueOf(wordCount)).build());
		values.put(":zero", AttributeValue.builder().n("0").build());
		values.put(":now", AttributeValue.builder().s(now).build());
		
		// 1. TOTAL 통계 업데이트
		Map<String, AttributeValue> totalKey = new HashMap<>();
		totalKey.put("PK", AttributeValue.builder().s(pk).build());
		totalKey.put("SK", AttributeValue.builder().s(StatsKey.statsTotalSk()).build());
		
		String totalUpdateExpression = "SET " +
				"newsWordsCollected = if_not_exists(newsWordsCollected, :zero) + :count, " +
				"updatedAt = :now, " +
				"createdAt = if_not_exists(createdAt, :now)";
		
		UpdateItemRequest totalRequest = UpdateItemRequest.builder()
				.tableName(TABLE_NAME)
				.key(totalKey)
				.updateExpression(totalUpdateExpression)
				.expressionAttributeValues(values)
				.returnValues(software.amazon.awssdk.services.dynamodb.model.ReturnValue.ALL_NEW)
				.build();
		
		AwsClients.dynamoDb().updateItem(totalRequest);
		
		// 2. DAILY 통계 업데이트
		Map<String, AttributeValue> dailyKey = new HashMap<>();
		dailyKey.put("PK", AttributeValue.builder().s(pk).build());
		dailyKey.put("SK", AttributeValue.builder().s(StatsKey.statsDailySk(today)).build());
		
		Map<String, AttributeValue> dailyValues = new HashMap<>();
		dailyValues.put(":count", AttributeValue.builder().n(String.valueOf(wordCount)).build());
		dailyValues.put(":zero", AttributeValue.builder().n("0").build());
		dailyValues.put(":now", AttributeValue.builder().s(now).build());
		dailyValues.put(":today", AttributeValue.builder().s(today).build());
		
		String dailyUpdateExpression = "SET " +
				"newsWordsCollected = if_not_exists(newsWordsCollected, :zero) + :count, " +
				"updatedAt = :now, " +
				"createdAt = if_not_exists(createdAt, :now), " +
				"period = if_not_exists(period, :today)";
		
		UpdateItemRequest dailyRequest = UpdateItemRequest.builder()
				.tableName(TABLE_NAME)
				.key(dailyKey)
				.updateExpression(dailyUpdateExpression)
				.expressionAttributeValues(dailyValues)
				.build();
		
		AwsClients.dynamoDb().updateItem(dailyRequest);
		logger.info("Incremented news word stats (TOTAL + DAILY): userId={}, wordCount={}", userId, wordCount);
		
		return findTotalStats(userId).orElse(null);
	}
	
	/**
	 * 연속 학습 중인 사용자 목록 조회 (streak >= 1)
	 * GSI1을 사용하여 TOTAL 통계만 조회 후 필터링
	 */
	public List<UserStats> findUsersWithActiveStreak() {
		QueryConditional queryConditional = QueryConditional
				.keyEqualTo(Key.builder()
						.partitionValue("STATS#ALL")
						.sortValue(StatsKey.statsTotalSk())
						.build());

		QueryEnhancedRequest request = QueryEnhancedRequest.builder()
				.queryConditional(queryConditional)
				.build();

		List<UserStats> results = new ArrayList<>();
		table.index("GSI1").query(request).forEach(page -> {
			page.items().stream()
					.filter(stats -> stats.getCurrentStreak() != null && stats.getCurrentStreak() >= 1)
					.forEach(results::add);
		});

		return results;
	}

	/**
	 * 현재 연도-주차 반환 (예: 2026-W02)
	 */
	private String getYearWeek() {
		LocalDate now = LocalDate.now();
		WeekFields weekFields = WeekFields.of(Locale.getDefault());
		int week = now.get(weekFields.weekOfWeekBasedYear());
		int year = now.get(weekFields.weekBasedYear());
		return String.format("%d-W%02d", year, week);
	}
	
	/**
	 * 현재 연도-월 반환 (예: 2026-01)
	 */
	private String getYearMonth() {
		LocalDate now = LocalDate.now();
		return String.format("%d-%02d", now.getYear(), now.getMonthValue());
	}
}
