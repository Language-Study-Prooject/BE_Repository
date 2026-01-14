package com.mzc.secondproject.serverless.domain.vocabulary.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SynthesizeVoiceRequest {
	
	@NotBlank(message = "is required")
	private String wordId;
	
	@Builder.Default
	private String voice = "FEMALE";  // MALE 또는 FEMALE
	
	@Builder.Default
	private String type = "WORD";     // WORD 또는 EXAMPLE
}
