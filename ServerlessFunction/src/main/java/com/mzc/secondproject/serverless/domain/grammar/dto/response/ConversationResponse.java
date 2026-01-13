package com.mzc.secondproject.serverless.domain.grammar.dto.response;

import lombok.*;

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
