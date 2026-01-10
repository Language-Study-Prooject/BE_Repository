package com.mzc.secondproject.serverless.domain.vocabulary.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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

    @NotBlank(message = "is required")
    private String testId;

    @Builder.Default
    private String testType = "DAILY";

    @NotEmpty(message = "is required")
    @Valid
    private List<TestAnswer> answers;

    private String startedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestAnswer {
        @NotBlank(message = "is required")
        private String wordId;

        private String answer;  // 빈 값 허용 (오답 처리)
    }
}
