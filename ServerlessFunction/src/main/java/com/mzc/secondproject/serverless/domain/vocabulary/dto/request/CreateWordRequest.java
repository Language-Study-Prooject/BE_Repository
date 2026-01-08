package com.mzc.secondproject.serverless.domain.vocabulary.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateWordRequest {
    private String english;
    private String korean;
    private String example;
    @Builder.Default
    private String level = "BEGINNER";
    @Builder.Default
    private String category = "DAILY";
}
