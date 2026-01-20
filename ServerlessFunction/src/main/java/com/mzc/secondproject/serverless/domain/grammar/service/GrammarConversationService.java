package com.mzc.secondproject.serverless.domain.grammar.service;

import com.google.gson.Gson;
import com.mzc.secondproject.serverless.domain.grammar.constants.GrammarKey;
import com.mzc.secondproject.serverless.domain.grammar.dto.request.ConversationRequest;
import com.mzc.secondproject.serverless.domain.grammar.dto.response.ConversationResponse;
import com.mzc.secondproject.serverless.domain.grammar.dto.response.GrammarCheckResponse;
import com.mzc.secondproject.serverless.domain.grammar.enums.GrammarLevel;
import com.mzc.secondproject.serverless.domain.grammar.exception.GrammarException;
import com.mzc.secondproject.serverless.domain.grammar.factory.BedrockGrammarCheckFactory;
import com.mzc.secondproject.serverless.domain.grammar.model.GrammarMessage;
import com.mzc.secondproject.serverless.domain.grammar.model.GrammarSession;
import com.mzc.secondproject.serverless.domain.grammar.repository.GrammarSessionRepository;
import com.mzc.secondproject.serverless.domain.grammar.streaming.StreamingCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class GrammarConversationService {
	
	private static final Logger logger = LoggerFactory.getLogger(GrammarConversationService.class);
	private static final int SESSION_TTL_DAYS = 30;
	private static final int MAX_HISTORY_MESSAGES = 10;
	private static final int LAST_MESSAGE_MAX_LENGTH = 100;
	
	private final BedrockGrammarCheckFactory grammarFactory;
	private final GrammarSessionRepository repository;
	private final Gson gson;
	
	public GrammarConversationService() {
		this.grammarFactory = new BedrockGrammarCheckFactory();
		this.repository = new GrammarSessionRepository();
		this.gson = new Gson();
	}
	
	public GrammarConversationService(BedrockGrammarCheckFactory grammarFactory, GrammarSessionRepository repository) {
		this.grammarFactory = grammarFactory;
		this.repository = repository;
		this.gson = new Gson();
	}
	
	public ConversationResponse chat(ConversationRequest request) {
		logger.info("Conversation chat requested: sessionId={}, userId={}", request.getSessionId(), request.getUserId());
		
		validateRequest(request);
		
		String userId = request.getUserId();
		GrammarLevel level = parseLevel(request.getLevel());
		
		// 세션 가져오기 또는 새로 생성
		GrammarSession session = getOrCreateSession(request.getSessionId(), userId, level);
		
		// 이전 대화 히스토리 조회
		String conversationHistory = buildConversationHistory(session.getSessionId());
		
		// AI 응답 생성
		ConversationResponse response = grammarFactory.generateConversation(
				session.getSessionId(),
				request.getMessage(),
				level,
				conversationHistory
		);
		
		// 사용자 메시지 저장
		saveMessage("USER", session, request.getMessage(), response.getGrammarCheck());

		// AI 응답 메시지 저장
		saveMessage("ASSISTANT", session, response.getAiResponse(), null);
		
		// 세션 업데이트
		updateSession(session, request.getMessage());
		
		return response;
	}
	
	private void validateRequest(ConversationRequest request) {
		if (request.getMessage() == null || request.getMessage().trim().isEmpty()) {
			throw GrammarException.invalidSentence(request.getMessage());
		}
		if (request.getUserId() == null || request.getUserId().trim().isEmpty()) {
			throw GrammarException.invalidRequest("userId", "userId는 필수입니다");
		}
	}
	
	private GrammarSession getOrCreateSession(String sessionId, String userId, GrammarLevel level) {
		if (sessionId != null && !sessionId.trim().isEmpty()) {
			return repository.findSessionById(userId, sessionId)
					.orElseGet(() -> createNewSession(sessionId, userId, level));
		}
		return createNewSession(UUID.randomUUID().toString(), userId, level);
	}
	
	private GrammarSession createNewSession(String sessionId, String userId, GrammarLevel level) {
		String now = Instant.now().toString();
		long ttl = Instant.now().plus(SESSION_TTL_DAYS, ChronoUnit.DAYS).getEpochSecond();
		
		GrammarSession session = GrammarSession.builder()
				.pk(GrammarKey.sessionPk(userId))
				.sk(GrammarKey.sessionSk(sessionId))
				.gsi1pk(GrammarKey.ALL_SESSIONS)
				.gsi1sk(GrammarKey.updatedSk(now))
				.sessionId(sessionId)
				.userId(userId)
				.level(level.name())
				.topic("Conversation Practice")
				.messageCount(0)
				.createdAt(now)
				.updatedAt(now)
				.ttl(ttl)
				.build();
		
		repository.saveSession(session);
		logger.info("New session created: sessionId={}", sessionId);
		return session;
	}
	
	private String buildConversationHistory(String sessionId) {
		try {
			List<GrammarMessage> messages = repository.findRecentMessagesBySessionId(sessionId, MAX_HISTORY_MESSAGES);
			
			if (messages.isEmpty()) {
				return "";
			}
			
			StringBuilder history = new StringBuilder();
			// 역순으로 정렬 (오래된 것 먼저)
			for (int i = messages.size() - 1; i >= 0; i--) {
				GrammarMessage msg = messages.get(i);
				if ("USER".equals(msg.getRole())) {
					history.append("User: ").append(msg.getContent()).append("\n");
				} else {
					history.append("Assistant: ").append(msg.getContent()).append("\n");
				}
			}
			return history.toString();
		} catch (Exception e) {
			logger.warn("Failed to build conversation history: {}", e.getMessage());
			return "";
		}
	}
	
	private void saveMessage(String role, GrammarSession session, String content, GrammarCheckResponse grammarCheck) {
		String now = Instant.now().toString();
		String messageId = UUID.randomUUID().toString();
		long ttl = Instant.now().plus(SESSION_TTL_DAYS, ChronoUnit.DAYS).getEpochSecond();

		GrammarMessage message = GrammarMessage.builder()
				.pk(GrammarKey.sessionPk(session.getUserId()))
				.sk(GrammarKey.messageSk(now, messageId))
				.gsi1pk(GrammarKey.messageGsi1Pk(session.getSessionId()))
				.gsi1sk(GrammarKey.messageGsi1Sk(now))
				.messageId(messageId)
				.sessionId(session.getSessionId())
				.userId(session.getUserId())
				.role(role)
				.content(content)
				.correctedContent(grammarCheck != null ? grammarCheck.getCorrectedSentence() : null)
				.errorsJson(grammarCheck != null ? gson.toJson(grammarCheck.getErrors()) : null)
				.grammarScore(grammarCheck != null ? grammarCheck.getScore() : null)
				.feedback(grammarCheck != null ? grammarCheck.getFeedback() : null)
				.isCorrect(grammarCheck != null ? grammarCheck.getIsCorrect() : null)
				.createdAt(now)
				.ttl(ttl)
				.build();

		repository.saveMessage(message);
	}
	
	private void updateSession(GrammarSession session, String lastMessage) {
		String now = Instant.now().toString();
		session.setGsi1sk(GrammarKey.updatedSk(now));
		session.setMessageCount(session.getMessageCount() + 2); // user + assistant
		session.setLastMessage(truncateMessage(lastMessage, LAST_MESSAGE_MAX_LENGTH));
		session.setUpdatedAt(now);
		
		repository.saveSession(session);
	}
	
	private String truncateMessage(String message, int maxLength) {
		if (message == null) return null;
		if (message.length() <= maxLength) return message;
		return message.substring(0, maxLength - 3) + "...";
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
	
	public void clearSession(String userId, String sessionId) {
		repository.deleteSession(userId, sessionId);
		logger.info("Session cleared: sessionId={}", sessionId);
	}
	
	/**
	 * 스트리밍 방식의 대화 처리
	 * 세션 관리, 메시지 저장 등을 서비스에서 담당
	 */
	public void chatStreaming(
			String sessionId,
			String message,
			String userId,
			String levelStr,
			Consumer<String> onSessionCreated,
			StreamingCallback callback
	) {
		logger.info("Streaming chat requested: sessionId={}, userId={}", sessionId, userId);
		
		GrammarLevel level = parseLevel(levelStr);
		
		// 세션 가져오기 또는 새로 생성
		GrammarSession session = getOrCreateSession(sessionId, userId, level);
		onSessionCreated.accept(session.getSessionId());
		
		// 이전 대화 히스토리 조회
		String conversationHistory = buildConversationHistory(session.getSessionId());
		
		// 스트리밍 콜백 래핑 - 완료 시 메시지 저장
		grammarFactory.generateConversationStreaming(
				session.getSessionId(),
				message,
				level,
				conversationHistory,
				new StreamingCallback() {
					@Override
					public void onToken(String token) {
						callback.onToken(token);
					}
					
					@Override
					public void onComplete(ConversationResponse response) {
						// 사용자 메시지 저장
						saveMessage("USER", session, message, response.getGrammarCheck());
						// AI 응답 메시지 저장
						saveMessage("ASSISTANT", session, response.getAiResponse(), null);
						// 세션 업데이트
						updateSession(session, message);
						// 완료 콜백 호출
						callback.onComplete(response);
					}
					
					@Override
					public void onError(Throwable error) {
						callback.onError(error);
					}
				}
		);
	}
	
	// Getter for external access
	public GrammarSession findOrCreateSession(String sessionId, String userId, String levelStr) {
		GrammarLevel level = parseLevel(levelStr);
		return getOrCreateSession(sessionId, userId, level);
	}
}
