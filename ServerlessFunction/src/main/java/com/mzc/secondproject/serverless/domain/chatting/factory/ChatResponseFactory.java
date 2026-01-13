package com.mzc.secondproject.serverless.domain.chatting.factory;

import com.mzc.secondproject.serverless.domain.chatting.enums.ChatLevel;

/**
 * AI 채팅 응답 생성 Factory 인터페이스.
 * 다양한 AI 백엔드(Bedrock, OpenAI 등)를 추상화한다.
 */
public interface ChatResponseFactory {
	
	/**
	 * AI 응답 생성
	 *
	 * @param userMessage         사용자 메시지
	 * @param level               채팅 난이도 레벨
	 * @param conversationHistory 이전 대화 내역 (nullable)
	 * @return AI 응답
	 */
	ChatResponse create(String userMessage, ChatLevel level, String conversationHistory);
	
	/**
	 * AI 응답 생성 (대화 내역 없이)
	 *
	 * @param userMessage 사용자 메시지
	 * @param level       채팅 난이도 레벨
	 * @return AI 응답
	 */
	default ChatResponse create(String userMessage, ChatLevel level) {
		return create(userMessage, level, null);
	}
}
