package com.mzc.secondproject.serverless.domain.grammar.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationResponse {
	private String sessionId;
	private GrammarCheckResponse grammarCheck;
	private String aiResponse;
	private String conversationTip;
}
