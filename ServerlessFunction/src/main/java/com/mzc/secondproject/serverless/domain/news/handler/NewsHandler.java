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
import com.mzc.secondproject.serverless.domain.news.model.UserNewsRecord;
import com.mzc.secondproject.serverless.domain.news.service.NewsLearningService;
import com.mzc.secondproject.serverless.domain.news.service.NewsQueryService;
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

	private final NewsQueryService queryService;
	private final NewsLearningService learningService;
	private final HandlerRouter router;

	public NewsHandler() {
		this(new NewsQueryService(), new NewsLearningService());
	}

	public NewsHandler(NewsQueryService queryService, NewsLearningService learningService) {
		this.queryService = queryService;
		this.learningService = learningService;
		this.router = initRouter();
	}

	private HandlerRouter initRouter() {
		return new HandlerRouter().addRoutes(
				Route.get("/news/today", this::getTodayNews),
				Route.get("/news/recommended", this::getRecommendedNews),
				Route.get("/news/stats", this::getNewsStats),
				Route.get("/news/bookmarks", this::getBookmarks),
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
}
