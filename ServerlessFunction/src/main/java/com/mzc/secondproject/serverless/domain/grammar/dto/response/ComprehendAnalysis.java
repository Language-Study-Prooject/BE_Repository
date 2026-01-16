package com.mzc.secondproject.serverless.domain.grammar.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Comprehend 분석 결과 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ComprehendAnalysis {
	private String sentiment;
	private SentimentScore sentimentScore;
	private List<SyntaxToken> syntax;
	private List<String> keyPhrases;
	private String complexity;
	private String language;
	private Double languageScore;
	
	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class SentimentScore {
		private Double positive;
		private Double negative;
		private Double neutral;
		private Double mixed;
	}
	
	@Data
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class SyntaxToken {
		private String text;
		private String partOfSpeech;
	}
}
