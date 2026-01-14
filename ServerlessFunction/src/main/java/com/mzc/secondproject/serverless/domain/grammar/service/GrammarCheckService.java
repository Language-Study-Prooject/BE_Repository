package com.mzc.secondproject.serverless.domain.grammar.service;

import com.mzc.secondproject.serverless.domain.grammar.dto.request.GrammarCheckRequest;
import com.mzc.secondproject.serverless.domain.grammar.dto.response.GrammarCheckResponse;
import com.mzc.secondproject.serverless.domain.grammar.enums.GrammarLevel;
import com.mzc.secondproject.serverless.domain.grammar.exception.GrammarException;
import com.mzc.secondproject.serverless.domain.grammar.factory.BedrockGrammarCheckFactory;
import com.mzc.secondproject.serverless.domain.grammar.factory.GrammarCheckFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GrammarCheckService {

	private static final Logger logger = LoggerFactory.getLogger(GrammarCheckService.class);

	private final GrammarCheckFactory grammarCheckFactory;

	public GrammarCheckService() {
		this.grammarCheckFactory = new BedrockGrammarCheckFactory();
	}

	public GrammarCheckService(GrammarCheckFactory grammarCheckFactory) {
		this.grammarCheckFactory = grammarCheckFactory;
	}

	public GrammarCheckResponse checkGrammar(GrammarCheckRequest request) {
		logger.info("Grammar check requested: sentence={}", request.getSentence());

		validateRequest(request);

		GrammarLevel level = parseLevel(request.getLevel());

		return grammarCheckFactory.checkGrammar(request.getSentence(), level);
	}

	private void validateRequest(GrammarCheckRequest request) {
		if (request.getSentence() == null || request.getSentence().trim().isEmpty()) {
			throw GrammarException.invalidSentence(request.getSentence());
		}
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
}
