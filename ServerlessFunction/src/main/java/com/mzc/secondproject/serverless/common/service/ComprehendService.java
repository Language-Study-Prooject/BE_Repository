package com.mzc.secondproject.serverless.common.service;

import com.mzc.secondproject.serverless.common.config.AwsClients;
import com.mzc.secondproject.serverless.domain.grammar.dto.response.ComprehendAnalysis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.comprehend.ComprehendClient;
import software.amazon.awssdk.services.comprehend.model.*;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Amazon Comprehend 서비스
 */
public class ComprehendService {

	private static final Logger logger = LoggerFactory.getLogger(ComprehendService.class);

	private final ComprehendClient comprehendClient;

	public ComprehendService() {
		this.comprehendClient = AwsClients.comprehend();
	}

	public ComprehendService(ComprehendClient comprehendClient) {
		this.comprehendClient = comprehendClient;
	}

	/**
	 * 텍스트 종합 분석
	 */
	public ComprehendAnalysis analyze(String text) {
		try {
			DetectSentimentResponse sentimentResponse = detectSentiment(text);
			DetectSyntaxResponse syntaxResponse = detectSyntax(text);
			DetectKeyPhrasesResponse keyPhrasesResponse = detectKeyPhrases(text);

			List<ComprehendAnalysis.SyntaxToken> syntaxTokens = syntaxResponse.syntaxTokens().stream()
					.map(token -> ComprehendAnalysis.SyntaxToken.builder()
							.text(token.text())
							.partOfSpeech(token.partOfSpeech().tagAsString())
							.build())
					.collect(Collectors.toList());

			List<String> keyPhrases = keyPhrasesResponse.keyPhrases().stream()
					.map(KeyPhrase::text)
					.collect(Collectors.toList());

			String complexity = calculateComplexity(syntaxTokens);

			return ComprehendAnalysis.builder()
					.sentiment(sentimentResponse.sentimentAsString())
					.sentimentScore(ComprehendAnalysis.SentimentScore.builder()
							.positive((double) sentimentResponse.sentimentScore().positive())
							.negative((double) sentimentResponse.sentimentScore().negative())
							.neutral((double) sentimentResponse.sentimentScore().neutral())
							.mixed((double) sentimentResponse.sentimentScore().mixed())
							.build())
					.syntax(syntaxTokens)
					.keyPhrases(keyPhrases)
					.complexity(complexity)
					.language("en")
					.languageScore(0.99)
					.build();

		} catch (Exception e) {
			logger.error("Comprehend analysis failed: {}", e.getMessage());
			return null;
		}
	}

	private DetectSentimentResponse detectSentiment(String text) {
		return comprehendClient.detectSentiment(DetectSentimentRequest.builder()
				.text(text)
				.languageCode(LanguageCode.EN)
				.build());
	}

	private DetectSyntaxResponse detectSyntax(String text) {
		return comprehendClient.detectSyntax(DetectSyntaxRequest.builder()
				.text(text)
				.languageCode(SyntaxLanguageCode.EN)
				.build());
	}

	private DetectKeyPhrasesResponse detectKeyPhrases(String text) {
		return comprehendClient.detectKeyPhrases(DetectKeyPhrasesRequest.builder()
				.text(text)
				.languageCode(LanguageCode.EN)
				.build());
	}

	/**
	 * 문장 복잡도 계산
	 * - 품사 다양성 + 문장 길이 기반
	 */
	private String calculateComplexity(List<ComprehendAnalysis.SyntaxToken> syntax) {
		Set<String> uniquePOS = syntax.stream()
				.map(ComprehendAnalysis.SyntaxToken::getPartOfSpeech)
				.collect(Collectors.toSet());

		int posCount = uniquePOS.size();
		int sentenceLength = syntax.size();

		if (posCount <= 3 && sentenceLength <= 5) {
			return "BEGINNER";
		} else if (posCount <= 5 && sentenceLength <= 10) {
			return "INTERMEDIATE";
		} else {
			return "ADVANCED";
		}
	}
}
