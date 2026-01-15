package com.mzc.secondproject.serverless.domain.grammar.dto.response;

import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrammarCheckResponse {
	private String originalSentence;
	private String correctedSentence;
	private Integer score;
	private List<GrammarError> errors;
	private String feedback;
	private Boolean isCorrect;
	private ComprehendAnalysis analysis;
}
