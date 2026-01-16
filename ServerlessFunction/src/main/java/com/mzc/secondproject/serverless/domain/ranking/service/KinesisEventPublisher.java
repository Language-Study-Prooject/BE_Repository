package com.mzc.secondproject.serverless.domain.ranking.service;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mzc.secondproject.serverless.domain.ranking.model.RankingEvent;
import com.mzc.secondproject.serverless.domain.ranking.model.RankingEventType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.kinesis.KinesisClient;
import software.amazon.awssdk.services.kinesis.model.PutRecordRequest;
import software.amazon.awssdk.services.kinesis.model.PutRecordResponse;

import java.util.Map;

public class KinesisEventPublisher {

	private static final Logger logger = LoggerFactory.getLogger(KinesisEventPublisher.class);
	private static final Gson gson = new GsonBuilder().create();

	private final KinesisClient kinesisClient;
	private final String streamName;

	public KinesisEventPublisher() {
		this.streamName = System.getenv("RANKING_STREAM_NAME");
		this.kinesisClient = KinesisClient.builder()
				.region(Region.of(System.getenv("AWS_REGION_NAME")))
				.httpClient(UrlConnectionHttpClient.builder().build())
				.build();
	}

	public void publish(RankingEvent event) {
		if (streamName == null || streamName.isEmpty()) {
			logger.warn("RANKING_STREAM_NAME not configured, skipping event publish");
			return;
		}

		try {
			String eventJson = gson.toJson(event);

			PutRecordRequest request = PutRecordRequest.builder()
					.streamName(streamName)
					.partitionKey(event.getUserId())
					.data(SdkBytes.fromUtf8String(eventJson))
					.build();

			PutRecordResponse response = kinesisClient.putRecord(request);

			logger.info("Published ranking event: type={}, userId={}, score={}, shardId={}, sequenceNumber={}",
					event.getEventType(), event.getUserId(), event.getScore(),
					response.shardId(), response.sequenceNumber());

		} catch (Exception e) {
			logger.error("Failed to publish ranking event: type={}, userId={}, error={}",
					event.getEventType(), event.getUserId(), e.getMessage(), e);
		}
	}

	public void publishTestCompleted(String userId, int correctAnswers, int totalQuestions, double successRate, String testId) {
		int bonusScore = (int) (successRate * 10);
		int totalScore = RankingEventType.TEST_COMPLETED.getBaseScore() + bonusScore;

		RankingEvent event = RankingEvent.create(
				RankingEventType.TEST_COMPLETED,
				userId,
				totalScore,
				Map.of(
						"testId", testId,
						"correctAnswers", correctAnswers,
						"totalQuestions", totalQuestions,
						"successRate", successRate
				),
				"TestHandler"
		);
		publish(event);
	}

	public void publishWordLearned(String userId, String wordId) {
		RankingEvent event = RankingEvent.create(
				RankingEventType.WORD_LEARNED,
				userId,
				Map.of("wordId", wordId),
				"DailyStudyHandler"
		);
		publish(event);
	}

	public void publishWordMastered(String userId, String wordId) {
		RankingEvent event = RankingEvent.create(
				RankingEventType.WORD_MASTERED,
				userId,
				Map.of("wordId", wordId),
				"UserWordHandler"
		);
		publish(event);
	}

	public void publishGamePlayed(String userId, String roomId, int score) {
		RankingEvent event = RankingEvent.create(
				RankingEventType.GAME_PLAYED,
				userId,
				RankingEventType.GAME_PLAYED.getBaseScore() + score,
				Map.of("roomId", roomId, "gameScore", score),
				"GameHandler"
		);
		publish(event);
	}

	public void publishGameWon(String userId, String roomId, int finalScore) {
		RankingEvent event = RankingEvent.create(
				RankingEventType.GAME_WON,
				userId,
				Map.of("roomId", roomId, "finalScore", finalScore),
				"GameHandler"
		);
		publish(event);
	}

	public void publishGrammarCheck(String userId, String sessionId) {
		RankingEvent event = RankingEvent.create(
				RankingEventType.GRAMMAR_CHECK,
				userId,
				Map.of("sessionId", sessionId != null ? sessionId : "single-check"),
				"GrammarHandler"
		);
		publish(event);
	}

	public void publishAttendance(String userId) {
		RankingEvent event = RankingEvent.create(
				RankingEventType.ATTENDANCE,
				userId,
				Map.of(),
				"UserStatsHandler"
		);
		publish(event);
	}

	public void publishStreakBonus(String userId, int streakDays) {
		int bonusScore = RankingEventType.STREAK_BONUS.getBaseScore() * streakDays;
		RankingEvent event = RankingEvent.create(
				RankingEventType.STREAK_BONUS,
				userId,
				bonusScore,
				Map.of("streakDays", streakDays),
				"StatsStreamHandler"
		);
		publish(event);
	}
}
