package com.mzc.secondproject.serverless.domain.opic.dto.response;

public record QuestionResponse (
        String questionId,
        String questionText,
        String audioUrl,
        int questionNumber,
        int totalQuestions
) {}
