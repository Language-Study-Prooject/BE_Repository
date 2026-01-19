package com.mzc.secondproject.serverless.domain.opic.dto.response;
import java.util.List;


public record FeedbackResponse (
        List<SpeakingError> errors,     // 오류/개선점 목록
        String correctedAnswer,          // 교정된 답변
        String sampleAnswer            // 모범 답변
){
    public static FeedbackResponse perfect(String answer, String sampleAnswer) {
        return new FeedbackResponse(List.of(), answer, sampleAnswer);
    }
}
가