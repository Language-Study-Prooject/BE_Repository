package com.mzc.secondproject.serverless.common.dto.request.vocabulary;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SynthesizeVoiceRequest {
    private String wordId;
    @Builder.Default
    private String voice = "FEMALE";
}
