package com.mzc.secondproject.serverless.domain.grammar.service;

import com.mzc.secondproject.serverless.domain.grammar.dto.request.ConversationRequest;
import com.mzc.secondproject.serverless.domain.grammar.dto.response.ConversationResponse;
import com.mzc.secondproject.serverless.domain.grammar.enums.GrammarLevel;
import com.mzc.secondproject.serverless.domain.grammar.exception.GrammarException;
import com.mzc.secondproject.serverless.domain.grammar.factory.BedrockGrammarCheckFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GrammarConversationService {

	private static final Logger logger = LoggerFactory.getLogger(GrammarConversationService.class);

	private final BedrockGrammarCheckFactory grammarFactory;
	private final Map<String, StringBuilder> conversationHistories;

	public GrammarConversationService() {
		this.grammarFactory = new BedrockGrammarCheckFactory();
		this.conversationHistories = new ConcurrentHashMap<>();
	}

	public ConversationResponse chat(ConversationRequest request) {
		logger.info("Conversation chat requested: sessionId={}", request.getSessionId());

		validateRequest(request);

		String sessionId = getOrCreateSessionId(request.getSessionId());
		GrammarLevel level = parseLevel(request.getLevel());

		String conversationHistory = conversationHistories.getOrDefault(sessionId, new StringBuilder()).toString();

		ConversationResponse response = grammarFactory.generateConversation(
				sessionId,
				request.getMessage(),
				level,
				conversationHistory
		);

		updateConversationHistory(sessionId, request.getMessage(), response.getAiResponse());

		return response;
	}

	private void validateRequest(ConversationRequest request) {
		if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
			throw GrammarException.invalidSentence(request.getMessage());
		}
	}

	private String getOrCreateSessionId(String sessionId) {
		if (sessionId == null || sessionId.trim().isEmpty()) {
			return UUID.randomUUID().toString();
		}
		return sessionId;
	}

	private GrammarLevel parseLevel(String levelStr) {
		if (levelStr == null || levelStr.isEmpty()) {
			return GrammarLevel.BEGINNER;
		}

		if (!GrammarLevel.isValid(levelStr)) {
			throw GrammarException.invalidLevel(levelStr);
		}

		return GrammarLevel.fromString(levelStr);
	}

	private void updateConversationHistory(String sessionId, String userMessage, String aiResponse) {
		StringBuilder history = conversationHistories.computeIfAbsent(sessionId, k -> new StringBuilder());

		history.append("User: ").append(userMessage).append("\n");
		history.append("Assistant: ").append(aiResponse).append("\n\n");

		if (history.length() > 10000) {
			int cutIndex = history.indexOf("\n\n", history.length() - 8000);
			if (cutIndex > 0) {
				history.delete(0, cutIndex + 2);
			}
		}
	}

	public void clearSession(String sessionId) {
		conversationHistories.remove(sessionId);
		logger.info("Session cleared: sessionId={}", sessionId);
	}
}
