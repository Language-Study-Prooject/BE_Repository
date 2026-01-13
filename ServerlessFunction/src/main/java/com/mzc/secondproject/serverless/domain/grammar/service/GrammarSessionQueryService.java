package com.mzc.secondproject.serverless.domain.grammar.service;

import com.mzc.secondproject.serverless.common.dto.PaginatedResult;
import com.mzc.secondproject.serverless.domain.grammar.exception.GrammarException;
import com.mzc.secondproject.serverless.domain.grammar.model.GrammarMessage;
import com.mzc.secondproject.serverless.domain.grammar.model.GrammarSession;
import com.mzc.secondproject.serverless.domain.grammar.repository.GrammarSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class GrammarSessionQueryService {

	private static final Logger logger = LoggerFactory.getLogger(GrammarSessionQueryService.class);

	private final GrammarSessionRepository repository;

	public GrammarSessionQueryService() {
		this.repository = new GrammarSessionRepository();
	}

	public GrammarSessionQueryService(GrammarSessionRepository repository) {
		this.repository = repository;
	}

	public PaginatedResult<GrammarSession> getSessions(String userId, int limit, String cursor) {
		logger.info("Getting sessions for user: userId={}", userId);
		return repository.findSessionsByUserId(userId, limit, cursor);
	}

	public SessionDetail getSessionDetail(String userId, String sessionId, int messageLimit) {
		logger.info("Getting session detail: sessionId={}", sessionId);

		GrammarSession session = repository.findSessionById(userId, sessionId)
				.orElseThrow(() -> GrammarException.sessionNotFound(sessionId));

		List<GrammarMessage> messages = repository.findRecentMessagesBySessionId(sessionId, messageLimit);

		return new SessionDetail(session, messages);
	}

	public void deleteSession(String userId, String sessionId) {
		logger.info("Deleting session: sessionId={}", sessionId);

		repository.findSessionById(userId, sessionId)
				.orElseThrow(() -> GrammarException.sessionNotFound(sessionId));

		repository.deleteSession(userId, sessionId);
	}

	public record SessionDetail(GrammarSession session, List<GrammarMessage> messages) {}
}
