package com.mzc.secondproject.serverless.domain.opic.dto.request;

public record CreateSessionRequest(
		String topic,
		String subTopic,
		String targetLevel
) {
}
