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
public class StreakReminderHandler implements RequestHandler<ScheduledEvent, StreakReminderHandler.Response> {

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
	public Response handleRequest(ScheduledEvent event, Context context) {
		logger.info("Streak reminder started: requestId={}", context.getAwsRequestId());

		try {
			int remindersSent = processReminders();
			logger.info("Streak reminder completed: sent={}", remindersSent);
			return Response.success(remindersSent);
		} catch (Exception e) {
			logger.error("Streak reminder failed", e);
			return Response.error(e.getMessage());
		}
	}

	private int processReminders() {
		String today = LocalDate.now().toString();

		Set<String> studiedUserIds = findStudiedUserIds(today);
		List<UserStats> usersWithStreak = userStatsRepository.findUsersWithActiveStreak();

		int remindersSent = 0;
		for (UserStats stats : usersWithStreak) {
			if (shouldSendReminder(stats, studiedUserIds)) {
				sendReminder(stats);
				remindersSent++;
			}
		}

		return remindersSent;
	}

	private Set<String> findStudiedUserIds(String date) {
		return dailyStudyRepository.findByDate(date).stream()
				.filter(ds -> Boolean.TRUE.equals(ds.getIsCompleted()))
				.map(DailyStudy::getUserId)
				.collect(Collectors.toSet());
	}

	private boolean shouldSendReminder(UserStats stats, Set<String> studiedUserIds) {
		if (studiedUserIds.contains(stats.getUserId())) {
			return false;
		}
		Integer streak = stats.getCurrentStreak();
		return streak != null && streak > 0;
	}

	private void sendReminder(UserStats stats) {
		String userId = stats.getUserId();
		int streak = stats.getCurrentStreak();

		notificationPublisher.publish(
				NotificationType.STREAK_REMINDER,
				userId,
				Map.of(
						"currentStreak", streak,
						"message", String.format("%d일 연속 학습 중! 오늘도 학습해서 기록을 이어가세요.", streak)
				)
		);

		logger.debug("Streak reminder sent: userId={}, streak={}", userId, streak);
	}

	/**
	 * Lambda 응답 DTO
	 */
	public record Response(int statusCode, String message, int remindersSent) {

		public static Response success(int remindersSent) {
			return new Response(200, "Streak reminders sent", remindersSent);
		}

		public static Response error(String errorMessage) {
			return new Response(500, "Streak reminder failed: " + errorMessage, 0);
		}
	}
}
