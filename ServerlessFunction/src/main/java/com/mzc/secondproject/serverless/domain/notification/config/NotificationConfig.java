package com.mzc.secondproject.serverless.domain.notification.config;

import com.mzc.secondproject.serverless.common.config.EnvConfig;

/**
 * 알림 시스템 설정
 * SSE 스트리밍, 폴링 등 알림 관련 상수 정의
 */
public final class NotificationConfig {

	private NotificationConfig() {
	}

	// ========== Environment Variables ==========
	private static final String TOPIC_ARN = EnvConfig.get("NOTIFICATION_TOPIC_ARN");
	private static final String QUEUE_URL = EnvConfig.get("NOTIFICATION_QUEUE_URL");

	// ========== SSE Streaming ==========
	/** SSE 폴링 간격 (밀리초) */
	public static final int SSE_POLL_INTERVAL_MS = 1000;

	/** SSE 최대 스트림 지속 시간 (밀리초) - Lambda 15분 제한 고려 */
	public static final int SSE_MAX_DURATION_MS = 840_000; // 14분

	/** SSE 최대 메시지 수신 개수 */
	public static final int SSE_MAX_MESSAGES_PER_POLL = 10;

	/** SSE 롱 폴링 대기 시간 (초) */
	public static final int SSE_WAIT_TIME_SECONDS = 1;

	// ========== SSE Event Types ==========
	public static final String EVENT_HEARTBEAT = "HEARTBEAT";
	public static final String EVENT_STREAM_END = "STREAM_END";

	// ========== Getter Methods ==========
	public static String topicArn() {
		return TOPIC_ARN;
	}

	public static String queueUrl() {
		return QUEUE_URL;
	}

	public static boolean isTopicConfigured() {
		return TOPIC_ARN != null && !TOPIC_ARN.isBlank();
	}

	public static boolean isQueueConfigured() {
		return QUEUE_URL != null && !QUEUE_URL.isBlank();
	}
}
