package com.mzc.secondproject.serverless.domain.opic.dto.response;

import com.mzc.secondproject.serverless.domain.opic.enums.SpeakingErrorType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpeakingError {
	private SpeakingErrorType type;
	private String original;
	private String corrected;
	private String explanation;
}
