package com.mzc.secondproject.serverless.domain.opic.dto.response;

public record AnswerFeedbackResponse(
		String answerId,
		String transcript,
		FeedbackResponse feedback,
		boolean hasNextQuestion,
		Integer nextQustionNumber,
		int totalQuestions
) {
}
