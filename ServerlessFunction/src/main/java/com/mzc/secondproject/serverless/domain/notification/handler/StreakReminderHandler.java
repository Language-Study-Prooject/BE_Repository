package com.mzc.secondproject.serverless.domain.notification.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import com.mzc.secondproject.serverless.domain.notification.enums.NotificationType;
import com.mzc.secondproject.serverless.domain.notification.service.NotificationPublisher;
import com.mzc.secondproject.serverless.domain.stats.model.UserStats;
import com.mzc.secondproject.serverless.domain.stats.repository.UserStatsRepository;
import com.mzc.secondproject.serverless.domain.vocabulary.model.DailyStudy;
import com.mzc.secondproject.serverless.domain.vocabulary.repository.DailyStudyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 연속 학습 리마인더 Lambda Handler
 * EventBridge 스케줄러에 의해 매일 21시(KST)에 트리거
 * 오늘 학습하지 않은 사용자 중 연속 학습 중인 사용자에게 알림 발송
 */
public class StreakReminderHandler implements RequestHandler<ScheduledEvent, Map<String, Object>> {

	private static final Logger logger = LoggerFactory.getLogger(StreakReminderHandler.class);

	private final DailyStudyRepository dailyStudyRepository;
	private final UserStatsRepository userStatsRepository;
	private final NotificationPublisher notificationPublisher;

	public StreakReminderHandler() {
		this.dailyStudyRepository = new DailyStudyRepository();
		this.userStatsRepository = new UserStatsRepository();
		this.notificationPublisher = NotificationPublisher.getInstance();
	}

	public StreakReminderHandler(DailyStudyRepository dailyStudyRepository,
								 UserStatsRepository userStatsRepository,
								 NotificationPublisher notificationPublisher) {
		this.dailyStudyRepository = dailyStudyRepository;
		this.userStatsRepository = userStatsRepository;
		this.notificationPublisher = notificationPublisher;
	}

	@Override
	public Map<String, Object> handleRequest(ScheduledEvent event, Context context) {
		logger.info("Streak reminder started - requestId: {}", context.getAwsRequestId());

		String today = LocalDate.now().toString();
		int remindersSent = 0;

		try {
			// 1. 오늘 학습한 사용자 목록 조회
			List<DailyStudy> todayStudies = dailyStudyRepository.findByDate(today);
			Set<String> studiedUserIds = todayStudies.stream()
					.filter(ds -> Boolean.TRUE.equals(ds.getIsCompleted()))
					.map(DailyStudy::getUserId)
					.collect(Collectors.toSet());

			// 2. 연속 학습 중인 사용자 목록 조회 (streak >= 1)
			List<UserStats> usersWithStreak = userStatsRepository.findUsersWithActiveStreak();

			// 3. 오늘 학습하지 않은 연속 학습 사용자에게 알림
			for (UserStats stats : usersWithStreak) {
				String userId = stats.getUserId();

				if (studiedUserIds.contains(userId)) {
					continue;
				}

				int currentStreak = stats.getCurrentStreak();
				if (currentStreak <= 0) {
					continue;
				}

				// 알림 발송
				notificationPublisher.publish(
						NotificationType.STREAK_REMINDER,
						userId,
						Map.of(
								"currentStreak", currentStreak,
								"message", String.format("%d일 연속 학습 중! 오늘도 학습해서 기록을 이어가세요.", currentStreak)
						)
				);

				remindersSent++;
				logger.debug("Streak reminder sent: userId={}, streak={}", userId, currentStreak);
			}

			logger.info("Streak reminder completed - sent: {}", remindersSent);

			return Map.of(
					"statusCode", 200,
					"message", "Streak reminders sent",
					"remindersSent", remindersSent
			);

		} catch (Exception e) {
			logger.error("Streak reminder failed", e);

			return Map.of(
					"statusCode", 500,
					"message", "Streak reminder failed: " + e.getMessage()
			);
		}
	}
}
