package com.mzc.secondproject.serverless.domain.vocabulary.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.mzc.secondproject.serverless.common.util.ResponseGenerator;
import com.mzc.secondproject.serverless.domain.vocabulary.service.StatisticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * SQS에서 시험 결과 메시지를 받아 UserWord 통계를 업데이트하는 Lambda
 * SNS → SQS → Statistics Lambda 패턴
 */
public class StatisticsHandler implements RequestHandler<SQSEvent, Void> {

    private static final Logger logger = LoggerFactory.getLogger(StatisticsHandler.class);

    private final StatisticsService statisticsService;

    public StatisticsHandler() {
        this.statisticsService = new StatisticsService();
    }

    @Override
    public Void handleRequest(SQSEvent event, Context context) {
        logger.info("Received {} messages from SQS", event.getRecords().size());

        for (SQSEvent.SQSMessage message : event.getRecords()) {
            try {
                processMessage(message);
            } catch (Exception e) {
                logger.error("Failed to process message: {}", message.getMessageId(), e);
                throw new RuntimeException("Failed to process message", e);
            }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private void processMessage(SQSEvent.SQSMessage message) {
        String body = message.getBody();
        logger.info("Processing message: {}", body);

        Map<String, Object> testResult = ResponseGenerator.gson().fromJson(body, Map.class);

        String userId = (String) testResult.get("userId");
        List<Map<String, Object>> results = (List<Map<String, Object>>) testResult.get("results");

        if (userId == null || results == null) {
            logger.warn("Invalid message format: userId or results is null");
            return;
        }

        statisticsService.processTestResults(userId, results);
    }
}
