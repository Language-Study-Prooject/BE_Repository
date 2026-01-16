package com.mzc.secondproject.serverless.domain.grammar.streaming;

import com.mzc.secondproject.serverless.domain.grammar.dto.response.ConversationResponse;
import com.mzc.secondproject.serverless.domain.grammar.dto.response.GrammarCheckResponse;

/**
 * WebSocket 스트리밍 이벤트를 위한 Sealed Interface
 * Java 17+ Sealed Class 활용
 */
public sealed interface StreamingEvent {
	
	String type();
	
	record StartEvent(String sessionId) implements StreamingEvent {
		@Override
		public String type() {
			return "start";
		}
	}
	
	record TokenEvent(String token) implements StreamingEvent {
		@Override
		public String type() {
			return "token";
		}
	}
	
	record CompleteEvent(
			String sessionId,
			GrammarCheckResponse grammarCheck,
			String aiResponse,
			String conversationTip
	) implements StreamingEvent {
		public static CompleteEvent from(ConversationResponse response) {
			return new CompleteEvent(
					response.getSessionId(),
					response.getGrammarCheck(),
					response.getAiResponse(),
					response.getConversationTip()
			);
		}
		
		@Override
		public String type() {
			return "complete";
		}
	}
	
	record ErrorEvent(String message) implements StreamingEvent {
		@Override
		public String type() {
			return "error";
		}
	}
}
