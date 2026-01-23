package com.mzc.secondproject.serverless.domain.news.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mzc.secondproject.serverless.common.config.AwsClients;
import com.mzc.secondproject.serverless.domain.news.model.KeywordInfo;
import com.mzc.secondproject.serverless.domain.news.model.NewsArticle;
import com.mzc.secondproject.serverless.domain.news.model.QuizQuestion;
import com.mzc.secondproject.serverless.domain.news.repository.NewsArticleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelRequest;
import software.amazon.awssdk.services.bedrockruntime.model.InvokeModelResponse;
import software.amazon.awssdk.services.comprehend.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 뉴스 AI 분석 서비스
 * - CEFR 난이도 분석 (Bedrock)
 * - 3줄 요약 생성 (Bedrock)
 * - 핵심 단어 추출 (Comprehend)
 * - 퀴즈 생성 (Bedrock)
 */
public class NewsAnalysisService {

	private static final Logger logger = LoggerFactory.getLogger(NewsAnalysisService.class);
	private static final Gson gson = new Gson();
	private static final String MODEL_ID = "anthropic.claude-3-haiku-20240307-v1:0";

	private final NewsArticleRepository articleRepository;

	public NewsAnalysisService() {
		this.articleRepository = new NewsArticleRepository();
	}

	public NewsAnalysisService(NewsArticleRepository articleRepository) {
		this.articleRepository = articleRepository;
	}

	/**
	 * 뉴스 기사 전체 분석
	 */
	public NewsArticle analyzeArticle(NewsArticle article) {
		logger.info("뉴스 분석 시작: {}", article.getArticleId());
		long startTime = System.currentTimeMillis();

		String content = article.getTitle() + ". " +
				(article.getSummary() != null ? article.getSummary() : "");

		try {
			// 1. CEFR 난이도 분석
			String cefrLevel = analyzeDifficulty(content);
			article.setCefrLevel(cefrLevel);
			article.setLevel(mapCefrToLevel(cefrLevel));

			// 2. 3줄 요약 + 퀴즈 + 카테고리 + 키워드 생성 (Bedrock - 한 번에 처리)
			AnalysisResult result = generateSummaryAndQuiz(content, cefrLevel);
			if (result.summary() != null) {
				article.setSummary(result.summary());
			}
			article.setQuiz(result.quiz());
			article.setHighlightWords(result.highlightWords());
			if (result.category() != null) {
				article.setCategory(result.category());
			}

			// 3. 키워드 설정 (Bedrock AI에서 추출한 키워드 사용)
			if (result.keywords() != null && !result.keywords().isEmpty()) {
				article.setKeywords(result.keywords());
			} else {
				// Bedrock 키워드 추출 실패 시 Comprehend 폴백
				List<KeywordInfo> fallbackKeywords = extractKeywords(content);
				article.setKeywords(fallbackKeywords);
			}

			// 4. GSI 키 설정
			article.setGsi1pk("LEVEL#" + article.getLevel());
			article.setGsi1sk(article.getPublishedAt());
			if (article.getCategory() != null) {
				article.setGsi2pk("CATEGORY#" + article.getCategory());
				article.setGsi2sk(article.getPublishedAt());
			}

			// 5. 저장
			articleRepository.save(article);

			long elapsed = System.currentTimeMillis() - startTime;
			logger.info("뉴스 분석 완료: {} ({}ms)", article.getArticleId(), elapsed);

		} catch (Exception e) {
			logger.error("뉴스 분석 실패: {}", article.getArticleId(), e);
			// 분석 실패해도 기본값으로 저장
			article.setLevel("INTERMEDIATE");
			article.setCefrLevel("B1");
			articleRepository.save(article);
		}

		return article;
	}

	/**
	 * CEFR 난이도 분석 (Bedrock)
	 */
	private String analyzeDifficulty(String content) {
		String systemPrompt = """
				You are an English language expert. Analyze the text and determine its CEFR level.
				Consider vocabulary complexity, sentence structure, and topic familiarity.

				Respond with ONLY the CEFR level code: A1, A2, B1, B2, C1, or C2
				No explanation, just the level code.
				""";

		String userPrompt = "Determine the CEFR level of this text:\n\n" + truncate(content, 1000);

		String response = invokeBedrock(systemPrompt, userPrompt);
		String level = response.trim().toUpperCase();

		// 유효한 레벨인지 확인
		if (List.of("A1", "A2", "B1", "B2", "C1", "C2").contains(level)) {
			return level;
		}

		// 레벨 추출 시도
		for (String validLevel : List.of("C2", "C1", "B2", "B1", "A2", "A1")) {
			if (response.toUpperCase().contains(validLevel)) {
				return validLevel;
			}
		}

		return "B1"; // 기본값
	}

	/**
	 * CEFR을 3단계 레벨로 매핑
	 */
	private String mapCefrToLevel(String cefrLevel) {
		return switch (cefrLevel) {
			case "A1", "A2" -> "BEGINNER";
			case "B1", "B2" -> "INTERMEDIATE";
			case "C1", "C2" -> "ADVANCED";
			default -> "INTERMEDIATE";
		};
	}

	/**
	 * 핵심 단어 추출 (Comprehend)
	 */
	private List<KeywordInfo> extractKeywords(String content) {
		try {
			DetectKeyPhrasesResponse response = AwsClients.comprehend().detectKeyPhrases(
					DetectKeyPhrasesRequest.builder()
							.text(truncate(content, 5000))
							.languageCode(LanguageCode.EN)
							.build()
			);

			List<KeywordInfo> keywords = new ArrayList<>();
			List<KeyPhrase> phrases = response.keyPhrases();

			for (int i = 0; i < Math.min(phrases.size(), 10); i++) {
				KeyPhrase phrase = phrases.get(i);
				if (phrase.score() > 0.8) {
					keywords.add(KeywordInfo.builder()
							.word(phrase.text())
							.position(i)
							.build());
				}
			}

			return keywords;

		} catch (Exception e) {
			logger.error("키워드 추출 실패", e);
			return new ArrayList<>();
		}
	}

	/**
	 * 요약 + 퀴즈 + 카테고리 + 키워드 생성 (Bedrock)
	 */
	private AnalysisResult generateSummaryAndQuiz(String content, String cefrLevel) {
		String systemPrompt = """
				You are an English learning assistant for Korean learners. Analyze the news article and create learning materials.

				Respond in this exact JSON format:
				{
				  "summary": "3-line summary in English (each line separated by newline)",
				  "keywords": [
				    {"word": "economy", "meaning": "the system of trade and industry", "meaningKo": "경제", "example": "The economy is growing steadily."},
				    {"word": "policy", "meaning": "a plan of action adopted by government", "meaningKo": "정책", "example": "The new policy affects all citizens."}
				  ],
				  "highlightWords": ["word1", "word2", "word3"],
				  "category": "WORLD",
				  "quiz": [
				    {
				      "questionId": "q1",
				      "type": "COMPREHENSION",
				      "question": "What is the main topic of this article?",
				      "options": ["Option A", "Option B", "Option C", "Option D"],
				      "correctAnswer": "Option A",
				      "points": 20
				    },
				    {
				      "questionId": "q2",
				      "type": "WORD_MATCH",
				      "question": "What does 'X' mean in this context?",
				      "options": ["meaning1", "meaning2", "meaning3", "meaning4"],
				      "correctAnswer": "meaning1",
				      "points": 15
				    },
				    {
				      "questionId": "q3",
				      "type": "FILL_BLANK",
				      "question": "The article mentions that _____ is important.",
				      "options": ["word1", "word2", "word3", "word4"],
				      "correctAnswer": "word1",
				      "points": 30
				    }
				  ]
				}

				IMPORTANT:
				- keywords: Extract 5-8 important vocabulary words from the article. Include:
				  - word: the English word
				  - meaning: simple English definition
				  - meaningKo: Korean translation of the word (한국어 뜻)
				  - example: example sentence from the article
				- highlightWords: 3-5 difficult words that learners should pay attention to (just the words, no definitions).
				- category: Choose EXACTLY ONE from: WORLD, POLITICS, BUSINESS, TECH, SCIENCE, HEALTH, SPORTS, ENTERTAINMENT, LIFESTYLE
				- Create exactly 3 quiz questions.
				- Adjust difficulty based on CEFR level: """ + cefrLevel;

		String userPrompt = "Create learning materials for this article:\n\n" + truncate(content, 1500);

		try {
			String response = invokeBedrock(systemPrompt, userPrompt);
			return parseAnalysisResult(response);
		} catch (Exception e) {
			logger.error("요약/퀴즈 생성 실패", e);
			return new AnalysisResult(null, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), null);
		}
	}

	/**
	 * Bedrock API 호출
	 */
	private String invokeBedrock(String systemPrompt, String userPrompt) {
		JsonObject requestBody = new JsonObject();
		requestBody.addProperty("anthropic_version", "bedrock-2023-05-31");
		requestBody.addProperty("max_tokens", 2000);
		requestBody.addProperty("system", systemPrompt);

		JsonArray messages = new JsonArray();
		JsonObject userMessage = new JsonObject();
		userMessage.addProperty("role", "user");
		userMessage.addProperty("content", userPrompt);
		messages.add(userMessage);
		requestBody.add("messages", messages);

		InvokeModelRequest request = InvokeModelRequest.builder()
				.modelId(MODEL_ID)
				.contentType("application/json")
				.accept("application/json")
				.body(SdkBytes.fromUtf8String(gson.toJson(requestBody)))
				.build();

		InvokeModelResponse response = AwsClients.bedrock().invokeModel(request);
		JsonObject jsonResponse = gson.fromJson(response.body().asUtf8String(), JsonObject.class);

		JsonArray contentArray = jsonResponse.getAsJsonArray("content");
		if (contentArray != null && !contentArray.isEmpty()) {
			return contentArray.get(0).getAsJsonObject().get("text").getAsString();
		}

		throw new RuntimeException("Empty response from Bedrock");
	}

	/**
	 * 분석 결과 파싱
	 */
	private AnalysisResult parseAnalysisResult(String response) {
		String jsonStr = extractJson(response);
		JsonObject json = gson.fromJson(jsonStr, JsonObject.class);

		String summary = json.has("summary") ? json.get("summary").getAsString() : null;
		String category = json.has("category") ? json.get("category").getAsString().toUpperCase() : "WORLD";

		// keywords 파싱
		List<KeywordInfo> keywords = new ArrayList<>();
		if (json.has("keywords")) {
			json.getAsJsonArray("keywords").forEach(e -> {
				JsonObject k = e.getAsJsonObject();
				keywords.add(KeywordInfo.builder()
						.word(k.has("word") ? k.get("word").getAsString() : "")
						.meaning(k.has("meaning") ? k.get("meaning").getAsString() : "")
						.meaningKo(k.has("meaningKo") ? k.get("meaningKo").getAsString() : "")
						.example(k.has("example") ? k.get("example").getAsString() : "")
						.build());
			});
		}

		List<String> highlightWords = new ArrayList<>();
		if (json.has("highlightWords")) {
			json.getAsJsonArray("highlightWords").forEach(e -> highlightWords.add(e.getAsString()));
		}

		List<QuizQuestion> quiz = new ArrayList<>();
		if (json.has("quiz")) {
			json.getAsJsonArray("quiz").forEach(e -> {
				JsonObject q = e.getAsJsonObject();
				List<String> options = new ArrayList<>();
				if (q.has("options")) {
					q.getAsJsonArray("options").forEach(opt -> options.add(opt.getAsString()));
				}
				quiz.add(QuizQuestion.builder()
						.questionId(q.has("questionId") ? q.get("questionId").getAsString() : null)
						.type(q.has("type") ? q.get("type").getAsString() : "COMPREHENSION")
						.question(q.has("question") ? q.get("question").getAsString() : "")
						.options(options)
						.correctAnswer(q.has("correctAnswer") ? q.get("correctAnswer").getAsString() : "")
						.points(q.has("points") ? q.get("points").getAsInt() : 20)
						.build());
			});
		}

		return new AnalysisResult(summary, keywords, highlightWords, quiz, category);
	}

	private String extractJson(String response) {
		int start = response.indexOf('{');
		int end = response.lastIndexOf('}');
		if (start != -1 && end != -1 && end > start) {
			return response.substring(start, end + 1);
		}
		return response;
	}

	private String truncate(String text, int maxLength) {
		if (text == null) return "";
		return text.length() > maxLength ? text.substring(0, maxLength) : text;
	}

	/**
	 * 분석 결과 레코드
	 */
	private record AnalysisResult(
			String summary,
			List<KeywordInfo> keywords,
			List<String> highlightWords,
			List<QuizQuestion> quiz,
			String category
	) {}
}
