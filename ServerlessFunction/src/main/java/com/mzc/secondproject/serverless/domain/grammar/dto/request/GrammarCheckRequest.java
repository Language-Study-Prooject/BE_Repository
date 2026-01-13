package com.mzc.secondproject.serverless.domain.grammar.dto.request;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrammarCheckRequest {
	private String sentence;

	@Builder.Default
	private String level = "BEGINNER";
}
