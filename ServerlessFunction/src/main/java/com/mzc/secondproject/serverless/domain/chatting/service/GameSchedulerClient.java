package com.mzc.secondproject.serverless.domain.chatting.service;

import com.mzc.secondproject.serverless.common.config.EnvConfig;
import com.mzc.secondproject.serverless.domain.chatting.config.GameConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.scheduler.SchedulerClient;
import software.amazon.awssdk.services.scheduler.model.*;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * EventBridge Scheduler를 사용한 게임 자동 종료 스케줄링
 */
public class GameSchedulerClient {

	private static final Logger logger = LoggerFactory.getLogger(GameSchedulerClient.class);

	private static final String SCHEDULE_GROUP = "game-auto-close";
	private static final String SCHEDULE_NAME_PREFIX = "game-close-";

	private final SchedulerClient schedulerClient;
	private final String targetLambdaArn;
	private final String roleArn;

	/**
	 * 기본 생성자 (Lambda에서 사용)
	 */
	public GameSchedulerClient() {
		this(SchedulerClient.create(),
				EnvConfig.getOrDefault("GAME_AUTO_CLOSE_LAMBDA_ARN", null),
				EnvConfig.getOrDefault("SCHEDULER_ROLE_ARN", null));
	}

	/**
	 * 의존성 주입 생성자 (테스트 용이성)
	 */
	public GameSchedulerClient(SchedulerClient schedulerClient, String targetLambdaArn, String roleArn) {
		this.schedulerClient = schedulerClient;
		this.targetLambdaArn = targetLambdaArn;
		this.roleArn = roleArn;
	}

	/**
	 * 게임 자동 종료 스케줄 생성
	 *
	 * @param gameSessionId 게임 세션 ID
	 * @param roomId        방 ID
	 * @return 스케줄 ARN (실패 시 null)
	 */
	public ScheduleResult createGameEndSchedule(String gameSessionId, String roomId) {
		if (targetLambdaArn == null || roleArn == null) {
			logger.warn("Scheduler not configured: GAME_AUTO_CLOSE_LAMBDA_ARN or SCHEDULER_ROLE_ARN not set");
			return new ScheduleResult(null, 0L);
		}

		try {
			// 7분 후 시간 계산
			long scheduledAtMs = System.currentTimeMillis() + (GameConfig.gameTimeLimit() * 1000L);
			Instant scheduledAt = Instant.ofEpochMilli(scheduledAtMs);

			// at() 표현식: at(yyyy-mm-ddThh:mm:ss)
			String atExpression = "at(" + DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
					.withZone(ZoneOffset.UTC)
					.format(scheduledAt) + ")";

			String scheduleName = SCHEDULE_NAME_PREFIX + gameSessionId;

			// Lambda 호출 시 전달할 페이로드
			String payload = String.format("{\"gameSessionId\":\"%s\",\"roomId\":\"%s\"}", gameSessionId, roomId);

			CreateScheduleRequest request = CreateScheduleRequest.builder()
					.name(scheduleName)
					.groupName(SCHEDULE_GROUP)
					.scheduleExpression(atExpression)
					.scheduleExpressionTimezone("UTC")
					.flexibleTimeWindow(FlexibleTimeWindow.builder()
							.mode(FlexibleTimeWindowMode.OFF)
							.build())
					.target(Target.builder()
							.arn(targetLambdaArn)
							.roleArn(roleArn)
							.input(payload)
							.build())
					.actionAfterCompletion(ActionAfterCompletion.DELETE) // 실행 후 자동 삭제
					.build();

			CreateScheduleResponse response = schedulerClient.createSchedule(request);

			logger.info("Game end schedule created: gameSessionId={}, scheduledAt={}, arn={}",
					gameSessionId, scheduledAt, response.scheduleArn());

			return new ScheduleResult(response.scheduleArn(), scheduledAtMs);

		} catch (ConflictException e) {
			logger.warn("Schedule already exists: gameSessionId={}", gameSessionId);
			return new ScheduleResult(null, 0L);

		} catch (Exception e) {
			logger.error("Failed to create game end schedule: gameSessionId={}, error={}",
					gameSessionId, e.getMessage());
			return new ScheduleResult(null, 0L);
		}
	}

	/**
	 * 게임 자동 종료 스케줄 취소
	 *
	 * @param gameSessionId 게임 세션 ID
	 * @return 취소 성공 여부
	 */
	public boolean cancelGameEndSchedule(String gameSessionId) {
		if (targetLambdaArn == null) {
			return true; // 스케줄러 미설정 시 무시
		}

		try {
			String scheduleName = SCHEDULE_NAME_PREFIX + gameSessionId;

			DeleteScheduleRequest request = DeleteScheduleRequest.builder()
					.name(scheduleName)
					.groupName(SCHEDULE_GROUP)
					.build();

			schedulerClient.deleteSchedule(request);

			logger.info("Game end schedule cancelled: gameSessionId={}", gameSessionId);
			return true;

		} catch (ResourceNotFoundException e) {
			logger.debug("Schedule not found (may have already executed): gameSessionId={}", gameSessionId);
			return true; // 이미 삭제되었거나 없는 경우

		} catch (Exception e) {
			logger.error("Failed to cancel game end schedule: gameSessionId={}, error={}",
					gameSessionId, e.getMessage());
			return false;
		}
	}

	/**
	 * 스케줄 생성 결과
	 */
	public record ScheduleResult(String scheduleArn, long scheduledAtMs) {
		public boolean success() {
			return scheduleArn != null;
		}
	}
}
