package com.mzc.secondproject.serverless.domain.notification.service;

import com.mzc.secondproject.serverless.common.config.AwsClients;
import com.mzc.secondproject.serverless.common.config.EnvConfig;
import com.mzc.secondproject.serverless.common.util.JsonUtil;
import com.mzc.secondproject.serverless.domain.notification.dto.NotificationMessage;
import com.mzc.secondproject.serverless.domain.notification.enums.NotificationType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;

import java.util.Map;

/**
 * 알림 발행 서비스
 * SNS 토픽에 알림 메시지를 발행하는 역할
 *
 * 사용 예시:
 * <pre>
 * NotificationPublisher.getInstance().publish(
 *     NotificationType.BADGE_EARNED,
 *     userId,
 *     Map.of("badgeType", "STREAK_7", "badgeName", "7일 연속 학습")
 * );
 * </pre>
 */
public class NotificationPublisher {

	private static final Logger logger = LoggerFactory.getLogger(NotificationPublisher.class);
	private static final String TOPIC_ARN = EnvConfig.get("NOTIFICATION_TOPIC_ARN");

	private static volatile NotificationPublisher instance;
	private final SnsClient snsClient;

	private NotificationPublisher() {
		this.snsClient = AwsClients.sns();
	}

	private NotificationPublisher(SnsClient snsClient) {
		this.snsClient = snsClient;
	}

	/**
	 * 싱글톤 인스턴스 반환
	 */
	public static NotificationPublisher getInstance() {
		if (instance == null) {
			synchronized (NotificationPublisher.class) {
				if (instance == null) {
					instance = new NotificationPublisher();
				}
			}
		}
		return instance;
	}

	/**
	 * 테스트용 인스턴스 생성
	 */
	public static NotificationPublisher createForTest(SnsClient snsClient) {
		return new NotificationPublisher(snsClient);
	}

	/**
	 * 알림 발행 (비동기, non-blocking)
	 * 발행 실패 시에도 호출자의 비즈니스 로직에 영향을 주지 않음
	 *
	 * @param type    알림 타입
	 * @param userId  대상 사용자 ID
	 * @param payload 알림 페이로드
	 */
	public void publish(NotificationType type, String userId, Map<String, Object> payload) {
		if (TOPIC_ARN == null || TOPIC_ARN.isBlank()) {
			logger.warn("NOTIFICATION_TOPIC_ARN is not configured. Skipping notification.");
			return;
		}

		try {
			NotificationMessage message = NotificationMessage.builder()
					.type(type)
					.userId(userId)
					.payload(payload)
					.build();

			String messageJson = JsonUtil.toJson(message);

			PublishRequest request = PublishRequest.builder()
					.topicArn(TOPIC_ARN)
					.message(messageJson)
					.messageAttributes(Map.of(
							"type", MessageAttributeValue.builder()
									.dataType("String")
									.stringValue(type.name())
									.build(),
							"userId", MessageAttributeValue.builder()
									.dataType("String")
									.stringValue(userId)
									.build(),
							"category", MessageAttributeValue.builder()
									.dataType("String")
									.stringValue(type.getCategory())
									.build()
					))
					.build();

			PublishResponse response = snsClient.publish(request);
			logger.info("Notification published: type={}, userId={}, messageId={}",
					type, userId, response.messageId());

		} catch (Exception e) {
			// 알림 발행 실패는 비즈니스 로직에 영향을 주지 않도록 로깅만 수행
			logger.error("Failed to publish notification: type={}, userId={}, error={}",
					type, userId, e.getMessage());
		}
	}

	/**
	 * 배지 획득 알림 발행 헬퍼 메서드
	 */
	public void publishBadgeEarned(String userId, String badgeType, String badgeName,
								   String description, String iconUrl) {
		publish(NotificationType.BADGE_EARNED, userId, Map.of(
				"badgeType", badgeType,
				"badgeName", badgeName,
				"description", description,
				"iconUrl", iconUrl != null ? iconUrl : ""
		));
	}

	/**
	 * 일일 학습 완료 알림 발행 헬퍼 메서드
	 */
	public void publishDailyComplete(String userId, String date, int wordsLearned,
									 int totalWords, int currentStreak) {
		publish(NotificationType.DAILY_COMPLETE, userId, Map.of(
				"date", date,
				"wordsLearned", wordsLearned,
				"totalWords", totalWords,
				"currentStreak", currentStreak
		));
	}

	/**
	 * 테스트 완료 알림 발행 헬퍼 메서드
	 */
	public void publishTestComplete(String userId, String testId, int score,
									int correctCount, int totalCount, boolean isPerfect) {
		publish(NotificationType.TEST_COMPLETE, userId, Map.of(
				"testId", testId,
				"score", score,
				"correctCount", correctCount,
				"totalCount", totalCount,
				"isPerfect", isPerfect
		));
	}

	/**
	 * 뉴스 퀴즈 완료 알림 발행 헬퍼 메서드
	 */
	public void publishNewsQuizComplete(String userId, String articleId, String articleTitle,
										int score, int correctCount, int totalCount, boolean isPerfect) {
		publish(NotificationType.NEWS_QUIZ_COMPLETE, userId, Map.of(
				"articleId", articleId,
				"articleTitle", articleTitle,
				"score", score,
				"correctCount", correctCount,
				"totalCount", totalCount,
				"isPerfect", isPerfect
		));
	}

	/**
	 * 게임 종료 알림 발행 헬퍼 메서드
	 */
	public void publishGameEnd(String userId, String roomId, String gameSessionId,
							   int rank, int totalPlayers, int score, boolean isWinner) {
		publish(NotificationType.GAME_END, userId, Map.of(
				"roomId", roomId,
				"gameSessionId", gameSessionId,
				"rank", rank,
				"totalPlayers", totalPlayers,
				"score", score,
				"isWinner", isWinner
		));
	}

	/**
	 * OPIc 세션 완료 알림 발행 헬퍼 메서드
	 */
	public void publishOpicComplete(String userId, String sessionId, String estimatedLevel,
									int questionsAnswered, String feedbackSummary) {
		publish(NotificationType.OPIC_COMPLETE, userId, Map.of(
				"sessionId", sessionId,
				"estimatedLevel", estimatedLevel,
				"questionsAnswered", questionsAnswered,
				"feedbackSummary", feedbackSummary != null ? feedbackSummary : ""
		));
	}
}
