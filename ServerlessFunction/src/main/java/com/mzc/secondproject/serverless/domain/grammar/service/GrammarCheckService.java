package com.mzc.secondproject.serverless.domain.grammar.service;

import com.mzc.secondproject.serverless.common.service.ComprehendService;
import com.mzc.secondproject.serverless.domain.grammar.dto.request.GrammarCheckRequest;
import com.mzc.secondproject.serverless.domain.grammar.dto.response.ComprehendAnalysis;
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
	private final ComprehendService comprehendService;

	public GrammarCheckService() {
		this.grammarCheckFactory = new BedrockGrammarCheckFactory();
		this.comprehendService = new ComprehendService();
	}

	public GrammarCheckService(GrammarCheckFactory grammarCheckFactory, ComprehendService comprehendService) {
		this.grammarCheckFactory = grammarCheckFactory;
		this.comprehendService = comprehendService;
	}

	public GrammarCheckResponse checkGrammar(GrammarCheckRequest request) {
		logger.info("Grammar check requested: sentence={}", request.getSentence());

		validateRequest(request);

		GrammarLevel level = parseLevel(request.getLevel());

		GrammarCheckResponse response = grammarCheckFactory.checkGrammar(request.getSentence(), level);

		try {
			ComprehendAnalysis analysis = comprehendService.analyze(request.getSentence());
			response.setAnalysis(analysis);
			logger.info("Comprehend analysis completed: complexity={}",
					analysis != null ? analysis.getComplexity() : "null");
		} catch (Exception e) {
			logger.warn("Comprehend analysis failed, continuing without analysis: {}", e.getMessage());
		}

		return response;
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
