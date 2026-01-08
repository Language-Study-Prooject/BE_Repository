package com.mzc.secondproject.serverless.common.dto.request.vocabulary;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitTestRequest {
    private String testId;
    @Builder.Default
    private String testType = "DAILY";
    private List<TestAnswer> answers;
    private String startedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestAnswer {
        private String wordId;
        private String answer;
    }
}
