package com.mzc.secondproject.serverless.domain.ranking.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RankingEvent {

	private String eventId;
	private RankingEventType eventType;
	private String userId;
	private String timestamp;
	private int score;
	private Map<String, Object> payload;
	private EventMetadata metadata;

	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class EventMetadata {
		private String source;
		private String version;
	}

	public static RankingEvent create(RankingEventType eventType, String userId, int score, Map<String, Object> payload, String source) {
		return RankingEvent.builder()
				.eventId(UUID.randomUUID().toString())
				.eventType(eventType)
				.userId(userId)
				.timestamp(Instant.now().toString())
				.score(score)
				.payload(payload)
				.metadata(EventMetadata.builder()
						.source(source)
						.version("1.0")
						.build())
				.build();
	}

	public static RankingEvent create(RankingEventType eventType, String userId, Map<String, Object> payload, String source) {
		return create(eventType, userId, eventType.getBaseScore(), payload, source);
	}
}
