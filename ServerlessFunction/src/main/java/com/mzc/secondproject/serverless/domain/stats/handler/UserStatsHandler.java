package com.mzc.secondproject.serverless.domain.stats.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.mzc.secondproject.serverless.common.dto.PaginatedResult;
import com.mzc.secondproject.serverless.common.router.HandlerRouter;
import com.mzc.secondproject.serverless.common.router.Route;
import com.mzc.secondproject.serverless.common.util.ResponseGenerator;
import com.mzc.secondproject.serverless.domain.stats.model.UserStats;
import com.mzc.secondproject.serverless.domain.stats.repository.UserStatsRepository;
import com.mzc.secondproject.serverless.domain.vocabulary.model.DailyStudy;
import com.mzc.secondproject.serverless.domain.vocabulary.repository.DailyStudyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 사용자 학습 통계 API Handler
 */
public class UserStatsHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {
	
	private static final Logger logger = LoggerFactory.getLogger(UserStatsHandler.class);
	
	private final UserStatsRepository statsRepository;
	private final DailyStudyRepository dailyStudyRepository;
	private final HandlerRouter router;
	
	public UserStatsHandler() {
		this.statsRepository = new UserStatsRepository();
		this.dailyStudyRepository = new DailyStudyRepository();
		this.router = initRouter();
	}
	
	private HandlerRouter initRouter() {
		return new HandlerRouter().addRoutes(
				Route.getAuth("/stats/daily", this::getDailyStats),
				Route.getAuth("/stats/weekly", this::getWeeklyStats),
				Route.getAuth("/stats/monthly", this::getMonthlyStats),
				Route.getAuth("/stats/total", this::getTotalStats),
				Route.getAuth("/stats/history", this::getStatsHistory)
		);
	}
	
	@Override
	public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
		logger.info("Received request: {} {}", request.getHttpMethod(), request.getPath());
		return router.route(request);
	}
	
	/**
	 * 오늘의 통계 조회
	 */
	private APIGatewayProxyResponseEvent getDailyStats(APIGatewayProxyRequestEvent request, String userId) {
		Map<String, String> queryParams = request.getQueryStringParameters();
		String date = queryParams != null && queryParams.get("date") != null ?
				queryParams.get("date") : LocalDate.now().toString();
		
		Optional<UserStats> stats = statsRepository.findDailyStats(userId, date);
		
		return ResponseGenerator.ok("Daily stats retrieved", buildStatsResponse(stats, "DAILY", date));
	}
	
	/**
	 * 이번 주 통계 조회
	 */
	private APIGatewayProxyResponseEvent getWeeklyStats(APIGatewayProxyRequestEvent request, String userId) {
		Map<String, String> queryParams = request.getQueryStringParameters();
		String yearWeek = queryParams != null && queryParams.get("week") != null ?
				queryParams.get("week") : getCurrentYearWeek();
		
		Optional<UserStats> stats = statsRepository.findWeeklyStats(userId, yearWeek);
		
		return ResponseGenerator.ok("Weekly stats retrieved", buildStatsResponse(stats, "WEEKLY", yearWeek));
	}
	
	/**
	 * 이번 달 통계 조회
	 */
	private APIGatewayProxyResponseEvent getMonthlyStats(APIGatewayProxyRequestEvent request, String userId) {
		Map<String, String> queryParams = request.getQueryStringParameters();
		String yearMonth = queryParams != null && queryParams.get("month") != null ?
				queryParams.get("month") : getCurrentYearMonth();
		
		Optional<UserStats> stats = statsRepository.findMonthlyStats(userId, yearMonth);
		
		return ResponseGenerator.ok("Monthly stats retrieved", buildStatsResponse(stats, "MONTHLY", yearMonth));
	}
	
	/**
	 * 전체 통계 조회
	 */
	private APIGatewayProxyResponseEvent getTotalStats(APIGatewayProxyRequestEvent request, String userId) {
		Optional<UserStats> stats = statsRepository.findTotalStats(userId);
		
		Map<String, Object> response = buildStatsResponse(stats, "TOTAL", "ALL");
		
		// 전체 통계에는 streak 정보 추가
		if (stats.isPresent()) {
			UserStats s = stats.get();
			response.put("currentStreak", s.getCurrentStreak() != null ? s.getCurrentStreak() : 0);
			response.put("longestStreak", s.getLongestStreak() != null ? s.getLongestStreak() : 0);
			response.put("lastStudyDate", s.getLastStudyDate());
		} else {
			response.put("currentStreak", 0);
			response.put("longestStreak", 0);
			response.put("lastStudyDate", null);
		}
		
		return ResponseGenerator.ok("Total stats retrieved", response);
	}
	
	/**
	 * 최근 일별 통계 히스토리 조회
	 */
	private APIGatewayProxyResponseEvent getStatsHistory(APIGatewayProxyRequestEvent request, String userId) {
		Map<String, String> queryParams = request.getQueryStringParameters();
		String cursor = queryParams != null ? queryParams.get("cursor") : null;
		
		int limit = 7;  // 기본 7일
		if (queryParams != null && queryParams.get("limit") != null) {
			limit = Math.min(Integer.parseInt(queryParams.get("limit")), 100);
		}
		
		PaginatedResult<UserStats> result = statsRepository.findRecentDailyStats(userId, limit, cursor);
		
		// 각 날짜별 isCompleted 정보 조회 및 응답 구성
		List<Map<String, Object>> historyWithCompletion = result.items().stream()
				.map(stats -> {
					Map<String, Object> item = new HashMap<>();
					item.put("period", stats.getPeriod());
					item.put("testsCompleted", stats.getTestsCompleted() != null ? stats.getTestsCompleted() : 0);
					item.put("questionsAnswered", stats.getQuestionsAnswered() != null ? stats.getQuestionsAnswered() : 0);
					item.put("correctAnswers", stats.getCorrectAnswers() != null ? stats.getCorrectAnswers() : 0);
					item.put("incorrectAnswers", stats.getIncorrectAnswers() != null ? stats.getIncorrectAnswers() : 0);
					item.put("successRate", calculateSuccessRate(stats));
					item.put("newWordsLearned", stats.getNewWordsLearned() != null ? stats.getNewWordsLearned() : 0);
					item.put("wordsReviewed", stats.getWordsReviewed() != null ? stats.getWordsReviewed() : 0);
					
					// DailyStudy에서 isCompleted 조회
					Optional<DailyStudy> dailyStudy = dailyStudyRepository.findByUserIdAndDate(userId, stats.getPeriod());
					item.put("isCompleted", dailyStudy.map(ds -> ds.getIsCompleted() != null && ds.getIsCompleted()).orElse(false));
					
					return item;
				})
				.collect(Collectors.toList());
		
		Map<String, Object> response = new HashMap<>();
		response.put("history", historyWithCompletion);
		response.put("nextCursor", result.nextCursor());
		response.put("hasMore", result.hasMore());
		
		return ResponseGenerator.ok("Stats history retrieved", response);
	}
	
	private Map<String, Object> buildStatsResponse(Optional<UserStats> stats, String periodType, String period) {
		Map<String, Object> response = new HashMap<>();
		response.put("periodType", periodType);
		response.put("period", period);
		
		if (stats.isPresent()) {
			UserStats s = stats.get();
			response.put("testsCompleted", s.getTestsCompleted() != null ? s.getTestsCompleted() : 0);
			response.put("questionsAnswered", s.getQuestionsAnswered() != null ? s.getQuestionsAnswered() : 0);
			response.put("correctAnswers", s.getCorrectAnswers() != null ? s.getCorrectAnswers() : 0);
			response.put("incorrectAnswers", s.getIncorrectAnswers() != null ? s.getIncorrectAnswers() : 0);
			response.put("successRate", calculateSuccessRate(s));
			response.put("newWordsLearned", s.getNewWordsLearned() != null ? s.getNewWordsLearned() : 0);
			response.put("wordsReviewed", s.getWordsReviewed() != null ? s.getWordsReviewed() : 0);
		} else {
			response.put("testsCompleted", 0);
			response.put("questionsAnswered", 0);
			response.put("correctAnswers", 0);
			response.put("incorrectAnswers", 0);
			response.put("successRate", 0.0);
			response.put("newWordsLearned", 0);
			response.put("wordsReviewed", 0);
		}
		
		return response;
	}
	
	private double calculateSuccessRate(UserStats stats) {
		int correct = stats.getCorrectAnswers() != null ? stats.getCorrectAnswers() : 0;
		int total = stats.getQuestionsAnswered() != null ? stats.getQuestionsAnswered() : 0;
		return total > 0 ? (correct * 100.0 / total) : 0.0;
	}
	
	private String getCurrentYearWeek() {
		LocalDate now = LocalDate.now();
		WeekFields weekFields = WeekFields.of(Locale.getDefault());
		int week = now.get(weekFields.weekOfWeekBasedYear());
		int year = now.get(weekFields.weekBasedYear());
		return String.format("%d-W%02d", year, week);
	}
	
	private String getCurrentYearMonth() {
		LocalDate now = LocalDate.now();
		return String.format("%d-%02d", now.getYear(), now.getMonthValue());
	}
}
