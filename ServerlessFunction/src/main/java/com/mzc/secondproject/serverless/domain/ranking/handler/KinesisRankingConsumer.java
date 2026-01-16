package com.mzc.secondproject.serverless.domain.ranking.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.KinesisEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mzc.secondproject.serverless.domain.ranking.model.RankingEvent;
import com.mzc.secondproject.serverless.domain.ranking.repository.RankingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Kinesis Stream에서 랭킹 이벤트를 소비하여 점수 업데이트
 */
public class KinesisRankingConsumer implements RequestHandler<KinesisEvent, Void> {

	private static final Logger logger = LoggerFactory.getLogger(KinesisRankingConsumer.class);
	private static final Gson gson = new GsonBuilder().create();

	private final RankingRepository rankingRepository;

	public KinesisRankingConsumer() {
		this.rankingRepository = new RankingRepository();
	}

	@Override
	public Void handleRequest(KinesisEvent event, Context context) {
		logger.info("Received {} Kinesis records", event.getRecords().size());

		Map<String, Integer> userScores = new HashMap<>();

		for (KinesisEvent.KinesisEventRecord record : event.getRecords()) {
			try {
				processRecord(record, userScores);
			} catch (Exception e) {
				logger.error("Failed to process record: {}", record.getEventID(), e);
			}
		}

		for (Map.Entry<String, Integer> entry : userScores.entrySet()) {
			try {
				rankingRepository.updateScore(entry.getKey(), entry.getValue());
				logger.info("Updated ranking: userId={}, totalScore={}", entry.getKey(), entry.getValue());
			} catch (Exception e) {
				logger.error("Failed to update ranking for userId={}: {}", entry.getKey(), e.getMessage(), e);
			}
		}

		logger.info("Processed {} users' rankings", userScores.size());
		return null;
	}

	private void processRecord(KinesisEvent.KinesisEventRecord record, Map<String, Integer> userScores) {
		String data = new String(record.getKinesis().getData().array(), StandardCharsets.UTF_8);
		RankingEvent event = gson.fromJson(data, RankingEvent.class);

		if (event == null || event.getUserId() == null) {
			logger.warn("Invalid event data: {}", data);
			return;
		}

		int score = event.getScore();
		String userId = event.getUserId();

		userScores.merge(userId, score, Integer::sum);

		logger.debug("Processed event: type={}, userId={}, score={}",
				event.getEventType(), userId, score);
	}
}
