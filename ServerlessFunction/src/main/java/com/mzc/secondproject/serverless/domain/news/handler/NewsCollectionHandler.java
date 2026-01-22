package com.mzc.secondproject.serverless.domain.news.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.ScheduledEvent;
import com.mzc.secondproject.serverless.domain.news.service.NewsCollectorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 뉴스 수집 Lambda 핸들러
 * EventBridge 스케줄러에 의해 매일 18시에 트리거
 */
public class NewsCollectionHandler implements RequestHandler<ScheduledEvent, Map<String, Object>> {

	private static final Logger logger = LoggerFactory.getLogger(NewsCollectionHandler.class);

	private final NewsCollectorService collectorService;

	public NewsCollectionHandler() {
		this.collectorService = new NewsCollectorService();
	}

	public NewsCollectionHandler(NewsCollectorService collectorService) {
		this.collectorService = collectorService;
	}

	@Override
	public Map<String, Object> handleRequest(ScheduledEvent event, Context context) {
		logger.info("뉴스 수집 Lambda 시작 - requestId: {}", context.getAwsRequestId());

		try {
			NewsCollectorService.CollectionResult result = collectorService.collectNews();

			logger.info("뉴스 수집 완료 - 수집: {}, 저장: {}, 소요: {}ms",
					result.collectedCount(), result.savedCount(), result.elapsedMs());

			return Map.of(
					"statusCode", 200,
					"message", "News collection completed",
					"collectedCount", result.collectedCount(),
					"savedCount", result.savedCount(),
					"elapsedMs", result.elapsedMs()
			);

		} catch (Exception e) {
			logger.error("뉴스 수집 실패", e);

			return Map.of(
					"statusCode", 500,
					"message", "News collection failed: " + e.getMessage()
			);
		}
	}
}
