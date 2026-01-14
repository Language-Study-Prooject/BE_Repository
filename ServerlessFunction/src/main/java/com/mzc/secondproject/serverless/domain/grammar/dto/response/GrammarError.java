package com.mzc.secondproject.serverless.domain.grammar.dto.response;

import com.mzc.secondproject.serverless.domain.grammar.enums.GrammarErrorType;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrammarError {
	private GrammarErrorType type;
	private String original;
	private String corrected;
	private String explanation;
	private Integer startIndex;
	private Integer endIndex;
}
