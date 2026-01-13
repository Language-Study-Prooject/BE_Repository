package com.mzc.secondproject.serverless.domain.stats.service;

import com.mzc.secondproject.serverless.domain.stats.model.UserStats;
import com.mzc.secondproject.serverless.domain.stats.repository.UserStatsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 학습 통계 서비스
 * 테스트 결과 및 학습 이벤트를 통계로 집계
 */
public class StatsService {
	
	private static final Logger logger = LoggerFactory.getLogger(StatsService.class);
	
	private final UserStatsRepository userStatsRepository;
	
	public StatsService() {
		this.userStatsRepository = new UserStatsRepository();
	}
	
	/**
	 * 테스트 완료 시 통계 업데이트
	 */
	public void recordTestCompletion(String userId, int correctAnswers, int incorrectAnswers) {
		userStatsRepository.incrementTestStats(userId, correctAnswers, incorrectAnswers);
		updateStudyStreak(userId);
		logger.info("Recorded test completion: userId={}, correct={}, incorrect={}",
				userId, correctAnswers, incorrectAnswers);
	}
	
	/**
	 * 단어 학습 완료 시 통계 업데이트
	 */
	public void recordWordsLearned(String userId, int newWords, int reviewedWords) {
		userStatsRepository.incrementWordsLearned(userId, newWords, reviewedWords);
		updateStudyStreak(userId);
		logger.info("Recorded words learned: userId={}, new={}, reviewed={}",
				userId, newWords, reviewedWords);
	}
	
	/**
	 * 연속 학습일(Streak) 업데이트
	 */
	private void updateStudyStreak(String userId) {
		String today = LocalDate.now().toString();
		
		Optional<UserStats> totalStats = userStatsRepository.findTotalStats(userId);
		
		int currentStreak = 1;
		int longestStreak = 1;
		
		if (totalStats.isPresent()) {
			UserStats stats = totalStats.get();
			String lastStudyDate = stats.getLastStudyDate();
			
			if (lastStudyDate != null) {
				LocalDate lastDate = LocalDate.parse(lastStudyDate);
				LocalDate todayDate = LocalDate.now();
				
				long daysDiff = todayDate.toEpochDay() - lastDate.toEpochDay();
				
				if (daysDiff == 0) {
					// 오늘 이미 학습함 - streak 유지
					return;
				} else if (daysDiff == 1) {
					// 어제 학습함 - streak 증가
					currentStreak = (stats.getCurrentStreak() != null ? stats.getCurrentStreak() : 0) + 1;
				} else {
					// 연속 학습 끊김 - streak 리셋
					currentStreak = 1;
				}
			}
			
			longestStreak = stats.getLongestStreak() != null ?
					Math.max(stats.getLongestStreak(), currentStreak) : currentStreak;
		}
		
		userStatsRepository.updateStreak(userId, currentStreak, longestStreak, today);
		logger.info("Updated streak: userId={}, current={}, longest={}", userId, currentStreak, longestStreak);
	}
	
	/**
	 * 일별 통계 조회
	 */
	public Optional<UserStats> getDailyStats(String userId, String date) {
		return userStatsRepository.findDailyStats(userId, date);
	}
	
	/**
	 * 주별 통계 조회
	 */
	public Optional<UserStats> getWeeklyStats(String userId, String yearWeek) {
		return userStatsRepository.findWeeklyStats(userId, yearWeek);
	}
	
	/**
	 * 월별 통계 조회
	 */
	public Optional<UserStats> getMonthlyStats(String userId, String yearMonth) {
		return userStatsRepository.findMonthlyStats(userId, yearMonth);
	}
	
	/**
	 * 전체 통계 조회
	 */
	public Optional<UserStats> getTotalStats(String userId) {
		return userStatsRepository.findTotalStats(userId);
	}
}
