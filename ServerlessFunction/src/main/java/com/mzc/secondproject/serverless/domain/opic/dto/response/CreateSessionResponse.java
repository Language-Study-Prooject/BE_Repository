package com.mzc.secondproject.serverless.domain.opic.dto.response;

public record CreateSessionResponse(
		String sessionId,
		QuestionResponse firstQuestion,
		int totalQuestions
) {
}
