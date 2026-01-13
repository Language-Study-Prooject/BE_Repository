package com.mzc.secondproject.serverless.domain.grammar.dto.request;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationRequest {
	private String sessionId;
	private String message;

	@Builder.Default
	private String level = "BEGINNER";
}
