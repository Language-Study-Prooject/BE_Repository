package com.mzc.secondproject.serverless.domain.news.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.mzc.secondproject.serverless.common.dto.PaginatedResult;
import com.mzc.secondproject.serverless.common.router.HandlerRouter;
import com.mzc.secondproject.serverless.common.router.Route;
import com.mzc.secondproject.serverless.common.util.CognitoUtil;
import com.mzc.secondproject.serverless.common.util.ResponseGenerator;
import com.mzc.secondproject.serverless.domain.news.exception.NewsErrorCode;
import com.mzc.secondproject.serverless.domain.news.model.NewsArticle;
import com.mzc.secondproject.serverless.domain.news.model.NewsQuizResult;
import com.mzc.secondproject.serverless.domain.news.model.NewsWordCollect;
import com.mzc.secondproject.serverless.domain.news.model.UserNewsRecord;
import com.mzc.secondproject.serverless.domain.news.service.NewsLearningService;
import com.mzc.secondproject.serverless.domain.news.service.NewsQueryService;
import com.mzc.secondproject.serverless.domain.news.service.NewsQuizService;
import com.mzc.secondproject.serverless.domain.news.service.NewsWordService;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 뉴스 학습 API 핸들러
 */
public class NewsHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

	private static final Logger logger = LoggerFactory.getLogger(NewsHandler.class);
	private static final int DEFAULT_LIMIT = 10;
	private static final int MAX_LIMIT = 50;
	private static final Gson gson = new Gson();

	private final NewsQueryService queryService;
	private final NewsLearningService learningService;
	private final NewsQuizService quizService;
	private final NewsWordService wordService;
	private final HandlerRouter router;

	public NewsHandler() {
		this(new NewsQueryService(), new NewsLearningService(), new NewsQuizService(), new NewsWordService());
	}

	public NewsHandler(NewsQueryService queryService, NewsLearningService learningService,
					   NewsQuizService quizService, NewsWordService wordService) {
		this.queryService = queryService;
		this.learningService = learningService;
		this.quizService = quizService;
		this.wordService = wordService;
		this.router = initRouter();
	}

	private HandlerRouter initRouter() {
		return new HandlerRouter().addRoutes(
				Route.get("/news/today", this::getTodayNews),
				Route.get("/news/recommended", this::getRecommendedNews),
				Route.get("/news/stats", this::getNewsStats),
				Route.get("/news/bookmarks", this::getBookmarks),
				Route.get("/news/words", this::getUserWords),
				Route.get("/news/quiz/history", this::getQuizHistory),
				Route.get("/news/{articleId}/words/{word}", this::getWordDetail),
				Route.post("/news/{articleId}/words", this::collectWord),
				Route.delete("/news/{articleId}/words/{word}", this::deleteWord),
				Route.post("/news/words/{word}/sync", this::syncWordToVocab),
				Route.get("/news/{articleId}/quiz", this::getQuiz),
				Route.post("/news/{articleId}/quiz", this::submitQuiz),
				Route.post("/news/{articleId}/read", this::markAsRead),
				Route.post("/news/{articleId}/bookmark", this::toggleBookmark),
				Route.get("/news/{articleId}/audio", this::getAudio),
				Route.get("/news/{articleId}", this::getNewsDetail),
				Route.get("/news", this::getNewsList)
		);
	}

	@Override
	public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
		logger.info("News API 요청: {} {}", request.getHttpMethod(), request.getPath());
		return router.route(request);
	}

	/**
	 * 뉴스 목록 조회 (필터링 지원)
	 * GET /news?level=INTERMEDIATE&category=TECH&limit=10&cursor=xxx
	 */
	private APIGatewayProxyResponseEvent getNewsList(APIGatewayProxyRequestEvent request) {
		Map<String, String> params = request.getQueryStringParameters();
		if (params == null) params = new HashMap<>();

		String level = params.get("level");
		String category = params.get("category");
		String cursor = params.get("cursor");
		int limit = parseLimit(params.get("limit"));

		PaginatedResult<NewsArticle> result;

		if (level != null && category != null) {
			result = queryService.getNewsByLevelAndCategory(level.toUpperCase(), category.toUpperCase(), limit, cursor);
		} else if (level != null) {
			result = queryService.getNewsByLevel(level.toUpperCase(), limit, cursor);
		} else if (category != null) {
			result = queryService.getNewsByCategory(category.toUpperCase(), limit, cursor);
		} else {
			result = queryService.getTodayNews(limit, cursor);
		}

		return buildPaginatedResponse(result);
	}

	/**
	 * 오늘의 뉴스 조회
	 * GET /news/today?limit=10&cursor=xxx
	 */
	private APIGatewayProxyResponseEvent getTodayNews(APIGatewayProxyRequestEvent request) {
		Map<String, String> params = request.getQueryStringParameters();
		if (params == null) params = new HashMap<>();

		String cursor = params.get("cursor");
		int limit = parseLimit(params.get("limit"));

		PaginatedResult<NewsArticle> result = queryService.getTodayNews(limit, cursor);
		return buildPaginatedResponse(result);
	}

	/**
	 * 내 레벨 맞춤 뉴스 추천
	 * GET /news/recommended?limit=10&cursor=xxx
	 */
	private APIGatewayProxyResponseEvent getRecommendedNews(APIGatewayProxyRequestEvent request) {
		Map<String, String> params = request.getQueryStringParameters();
		if (params == null) params = new HashMap<>();

		// 사용자 레벨 조회 (Cognito 토큰에서)
		String userLevel = getUserLevel(request);
		String cursor = params.get("cursor");
		int limit = parseLimit(params.get("limit"));

		PaginatedResult<NewsArticle> result = queryService.getRecommendedNews(userLevel, limit, cursor);
		return buildPaginatedResponse(result);
	}

	/**
	 * 뉴스 상세 조회
	 * GET /news/{articleId}
	 */
	private APIGatewayProxyResponseEvent getNewsDetail(APIGatewayProxyRequestEvent request) {
		String articleId = request.getPathParameters().get("articleId");

		Optional<NewsArticle> article = queryService.getArticle(articleId);
		if (article.isEmpty()) {
			return ResponseGenerator.fail(NewsErrorCode.ARTICLE_NOT_FOUND);
		}

		return ResponseGenerator.ok("뉴스 조회 성공", article.get());
	}

	/**
	 * 페이지네이션 응답 생성
	 */
	private APIGatewayProxyResponseEvent buildPaginatedResponse(PaginatedResult<NewsArticle> result) {
		Map<String, Object> response = new HashMap<>();
		response.put("articles", result.items());
		response.put("nextCursor", result.nextCursor());
		response.put("hasMore", result.hasMore());
		response.put("count", result.items().size());

		return ResponseGenerator.ok("뉴스 목록 조회 성공", response);
	}

	/**
	 * limit 파싱
	 */
	private int parseLimit(String limitStr) {
		if (limitStr == null) return DEFAULT_LIMIT;
		try {
			int limit = Integer.parseInt(limitStr);
			return Math.min(Math.max(limit, 1), MAX_LIMIT);
		} catch (NumberFormatException e) {
			return DEFAULT_LIMIT;
		}
	}

	/**
	 * 사용자 레벨 조회
	 */
	private String getUserLevel(APIGatewayProxyRequestEvent request) {
		return CognitoUtil.extractClaim(request, "custom:level")
				.orElse("INTERMEDIATE");
	}

	/**
	 * 사용자 ID 추출
	 */
	private String getUserId(APIGatewayProxyRequestEvent request) {
		return CognitoUtil.extractClaim(request, "sub")
				.orElse(null);
	}

	/**
	 * 뉴스 학습 통계 조회
	 * GET /news/stats
	 */
	private APIGatewayProxyResponseEvent getNewsStats(APIGatewayProxyRequestEvent request) {
		String userId = getUserId(request);
		if (userId == null) {
			return ResponseGenerator.fail(NewsErrorCode.UNAUTHORIZED);
		}

		Map<String, Object> stats = learningService.getUserStats(userId);
		return ResponseGenerator.ok("뉴스 학습 통계 조회 성공", stats);
	}

	/**
	 * 북마크 목록 조회
	 * GET /news/bookmarks?limit=10
	 */
	private APIGatewayProxyResponseEvent getBookmarks(APIGatewayProxyRequestEvent request) {
		String userId = getUserId(request);
		if (userId == null) {
			return ResponseGenerator.fail(NewsErrorCode.UNAUTHORIZED);
		}

		Map<String, String> params = request.getQueryStringParameters();
		if (params == null) params = new HashMap<>();

		int limit = parseLimit(params.get("limit"));
		List<UserNewsRecord> bookmarks = learningService.getUserBookmarks(userId, limit);

		Map<String, Object> response = new HashMap<>();
		response.put("bookmarks", bookmarks);
		response.put("count", bookmarks.size());

		return ResponseGenerator.ok("북마크 목록 조회 성공", response);
	}

	/**
	 * 뉴스 읽기 완료 기록
	 * POST /news/{articleId}/read
	 */
	private APIGatewayProxyResponseEvent markAsRead(APIGatewayProxyRequestEvent request) {
		String userId = getUserId(request);
		if (userId == null) {
			return ResponseGenerator.fail(NewsErrorCode.UNAUTHORIZED);
		}

		String articleId = request.getPathParameters().get("articleId");
		learningService.markAsRead(userId, articleId);

		return ResponseGenerator.ok("읽기 완료 기록 성공", Map.of("articleId", articleId));
	}

	/**
	 * 북마크 토글
	 * POST /news/{articleId}/bookmark
	 */
	private APIGatewayProxyResponseEvent toggleBookmark(APIGatewayProxyRequestEvent request) {
		String userId = getUserId(request);
		if (userId == null) {
			return ResponseGenerator.fail(NewsErrorCode.UNAUTHORIZED);
		}

		String articleId = request.getPathParameters().get("articleId");
		boolean isBookmarked = learningService.toggleBookmark(userId, articleId);

		return ResponseGenerator.ok(
				isBookmarked ? "북마크 추가 성공" : "북마크 해제 성공",
				Map.of("articleId", articleId, "bookmarked", isBookmarked)
		);
	}

	/**
	 * 뉴스 TTS 오디오 URL 조회
	 * GET /news/{articleId}/audio?voice=Joanna
	 */
	private APIGatewayProxyResponseEvent getAudio(APIGatewayProxyRequestEvent request) {
		String articleId = request.getPathParameters().get("articleId");

		Map<String, String> params = request.getQueryStringParameters();
		String voice = (params != null) ? params.getOrDefault("voice", "Joanna") : "Joanna";

		String audioUrl = learningService.getAudioUrl(articleId, voice);
		if (audioUrl == null) {
			return ResponseGenerator.fail(NewsErrorCode.ARTICLE_NOT_FOUND);
		}

		return ResponseGenerator.ok("TTS 오디오 URL 조회 성공", Map.of("audioUrl", audioUrl));
	}

	/**
	 * 퀴즈 조회
	 * GET /news/{articleId}/quiz
	 */
	private APIGatewayProxyResponseEvent getQuiz(APIGatewayProxyRequestEvent request) {
		String userId = getUserId(request);
		if (userId == null) {
			return ResponseGenerator.fail(NewsErrorCode.UNAUTHORIZED);
		}

		String articleId = request.getPathParameters().get("articleId");
		Optional<NewsQuizService.QuizData> quizData = quizService.getQuiz(articleId, userId);

		if (quizData.isEmpty()) {
			return ResponseGenerator.fail(NewsErrorCode.QUIZ_NOT_FOUND);
		}

		return ResponseGenerator.ok("퀴즈 조회 성공", quizData.get());
	}

	/**
	 * 퀴즈 제출
	 * POST /news/{articleId}/quiz
	 */
	private APIGatewayProxyResponseEvent submitQuiz(APIGatewayProxyRequestEvent request) {
		String userId = getUserId(request);
		if (userId == null) {
			return ResponseGenerator.fail(NewsErrorCode.UNAUTHORIZED);
		}

		String articleId = request.getPathParameters().get("articleId");

		// 요청 바디 파싱
		JsonObject body = gson.fromJson(request.getBody(), JsonObject.class);
		JsonArray answersArray = body.getAsJsonArray("answers");
		Integer timeTaken = body.has("timeTaken") ? body.get("timeTaken").getAsInt() : null;

		List<NewsQuizService.QuizAnswer> answers = new java.util.ArrayList<>();
		if (answersArray != null) {
			answersArray.forEach(e -> {
				JsonObject a = e.getAsJsonObject();
				answers.add(new NewsQuizService.QuizAnswer(
						a.get("questionId").getAsString(),
						a.get("answer").getAsString()
				));
			});
		}

		NewsQuizService.QuizSubmitResult result = quizService.submitQuiz(userId, articleId, answers, timeTaken);

		if (result == null) {
			return ResponseGenerator.fail(NewsErrorCode.QUIZ_ALREADY_SUBMITTED);
		}

		return ResponseGenerator.ok("퀴즈 제출 성공", result);
	}

	/**
	 * 퀴즈 기록 조회
	 * GET /news/quiz/history?limit=10
	 */
	private APIGatewayProxyResponseEvent getQuizHistory(APIGatewayProxyRequestEvent request) {
		String userId = getUserId(request);
		if (userId == null) {
			return ResponseGenerator.fail(NewsErrorCode.UNAUTHORIZED);
		}

		Map<String, String> params = request.getQueryStringParameters();
		if (params == null) params = new HashMap<>();

		int limit = parseLimit(params.get("limit"));
		List<NewsQuizResult> history = quizService.getUserQuizHistory(userId, limit);
		Map<String, Object> quizStats = quizService.getUserQuizStats(userId);

		Map<String, Object> response = new HashMap<>();
		response.put("history", history);
		response.put("stats", quizStats);
		response.put("count", history.size());

		return ResponseGenerator.ok("퀴즈 기록 조회 성공", response);
	}

	/**
	 * 수집 단어 목록 조회
	 * GET /news/words?limit=10
	 */
	private APIGatewayProxyResponseEvent getUserWords(APIGatewayProxyRequestEvent request) {
		String userId = getUserId(request);
		if (userId == null) {
			return ResponseGenerator.fail(NewsErrorCode.UNAUTHORIZED);
		}

		Map<String, String> params = request.getQueryStringParameters();
		if (params == null) params = new HashMap<>();

		int limit = parseLimit(params.get("limit"));
		List<NewsWordCollect> words = wordService.getUserWords(userId, limit);
		Map<String, Object> stats = wordService.getUserWordStats(userId);

		Map<String, Object> response = new HashMap<>();
		response.put("words", words);
		response.put("stats", stats);
		response.put("count", words.size());

		return ResponseGenerator.ok("수집 단어 목록 조회 성공", response);
	}

	/**
	 * 단어 수집
	 * POST /news/{articleId}/words
	 */
	private APIGatewayProxyResponseEvent collectWord(APIGatewayProxyRequestEvent request) {
		String userId = getUserId(request);
		if (userId == null) {
			return ResponseGenerator.fail(NewsErrorCode.UNAUTHORIZED);
		}

		String articleId = request.getPathParameters().get("articleId");

		JsonObject body = gson.fromJson(request.getBody(), JsonObject.class);
		String word = body.get("word").getAsString();
		String context = body.has("context") ? body.get("context").getAsString() : "";

		NewsWordCollect collected = wordService.collectWord(userId, articleId, word, context);

		if (collected == null) {
			return ResponseGenerator.fail(NewsErrorCode.WORD_ALREADY_COLLECTED);
		}

		return ResponseGenerator.ok("단어 수집 성공", collected);
	}

	/**
	 * 단어 삭제
	 * DELETE /news/{articleId}/words/{word}
	 */
	private APIGatewayProxyResponseEvent deleteWord(APIGatewayProxyRequestEvent request) {
		String userId = getUserId(request);
		if (userId == null) {
			return ResponseGenerator.fail(NewsErrorCode.UNAUTHORIZED);
		}

		String articleId = request.getPathParameters().get("articleId");
		String word = request.getPathParameters().get("word");

		wordService.deleteWord(userId, word, articleId);

		return ResponseGenerator.ok("단어 삭제 성공", Map.of("word", word));
	}

	/**
	 * 단어 상세 정보 조회
	 * GET /news/{articleId}/words/{word}
	 */
	private APIGatewayProxyResponseEvent getWordDetail(APIGatewayProxyRequestEvent request) {
		String word = request.getPathParameters().get("word");

		Optional<NewsWordService.WordDetail> detail = wordService.getWordDetail(word);

		if (detail.isEmpty()) {
			return ResponseGenerator.fail(NewsErrorCode.WORD_NOT_COLLECTED);
		}

		return ResponseGenerator.ok("단어 상세 조회 성공", detail.get());
	}

	/**
	 * 단어 Vocabulary 연동
	 * POST /news/words/{word}/sync
	 */
	private APIGatewayProxyResponseEvent syncWordToVocab(APIGatewayProxyRequestEvent request) {
		String userId = getUserId(request);
		if (userId == null) {
			return ResponseGenerator.fail(NewsErrorCode.UNAUTHORIZED);
		}

		String word = request.getPathParameters().get("word");

		JsonObject body = gson.fromJson(request.getBody(), JsonObject.class);
		String articleId = body.get("articleId").getAsString();

		boolean synced = wordService.syncToVocabulary(userId, word, articleId);

		if (!synced) {
			return ResponseGenerator.fail(NewsErrorCode.WORD_NOT_COLLECTED);
		}

		return ResponseGenerator.ok("Vocabulary 연동 성공", Map.of("word", word, "synced", true));
	}
}
