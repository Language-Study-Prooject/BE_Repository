package com.mzc.secondproject.serverless.domain.grammar.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrammarCheckRequest {
	private String sentence;
	
	@Builder.Default
	private String level = "BEGINNER";
}
