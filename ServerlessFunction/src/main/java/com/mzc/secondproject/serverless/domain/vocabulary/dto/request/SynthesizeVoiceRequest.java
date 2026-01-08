package com.mzc.secondproject.serverless.domain.vocabulary.dto.request;

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
    private String voice = "FEMALE";  // MALE 또는 FEMALE
    @Builder.Default
    private String type = "WORD";     // WORD 또는 EXAMPLE
}
