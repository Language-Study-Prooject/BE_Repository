package com.mzc.secondproject.serverless.domain.notification.dto;

import com.mzc.secondproject.serverless.domain.notification.enums.NotificationType;

import java.time.Instant;
import java.util.Map;

/**
 * 알림 메시지 DTO
 * SNS로 발행되고 SSE로 클라이언트에 전달되는 메시지 구조
 */
public record NotificationMessage(
		String notificationId,
		NotificationType type,
		String userId,
		Map<String, Object> payload,
		String createdAt
) {
	/**
	 * Builder 패턴으로 알림 메시지 생성
	 */
	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private NotificationType type;
		private String userId;
		private Map<String, Object> payload;

		public Builder type(NotificationType type) {
			this.type = type;
			return this;
		}

		public Builder userId(String userId) {
			this.userId = userId;
			return this;
		}

		public Builder payload(Map<String, Object> payload) {
			this.payload = payload;
			return this;
		}

		public NotificationMessage build() {
			return new NotificationMessage(
					generateNotificationId(),
					type,
					userId,
					payload,
					Instant.now().toString()
			);
		}

		private String generateNotificationId() {
			return "notif-" + java.util.UUID.randomUUID().toString().substring(0, 8);
		}
	}
}
