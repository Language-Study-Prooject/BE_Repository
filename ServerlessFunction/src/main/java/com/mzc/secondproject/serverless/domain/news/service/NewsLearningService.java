package com.mzc.secondproject.serverless.domain.news.service;

import com.mzc.secondproject.serverless.common.service.PollyService;
import com.mzc.secondproject.serverless.domain.badge.model.UserBadge;
import com.mzc.secondproject.serverless.domain.badge.service.BadgeService;
import com.mzc.secondproject.serverless.domain.news.config.NewsConfig;
import com.mzc.secondproject.serverless.domain.news.constants.NewsKey;
import com.mzc.secondproject.serverless.domain.news.model.NewsArticle;
import com.mzc.secondproject.serverless.domain.news.model.UserNewsRecord;
import com.mzc.secondproject.serverless.domain.news.repository.NewsArticleRepository;
import com.mzc.secondproject.serverless.domain.news.repository.UserNewsRepository;
import com.mzc.secondproject.serverless.domain.stats.model.UserStats;
import com.mzc.secondproject.serverless.domain.stats.repository.UserStatsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 뉴스 학습 부가 기능 서비스
 */
public class NewsLearningService {

	private static final Logger logger = LoggerFactory.getLogger(NewsLearningService.class);

	private final NewsArticleRepository articleRepository;
	private final UserNewsRepository userNewsRepository;
	private final PollyService pollyService;
	private final UserStatsRepository userStatsRepository;
	private final BadgeService badgeService;

	public NewsLearningService() {
		this.articleRepository = new NewsArticleRepository();
		this.userNewsRepository = new UserNewsRepository();
		this.pollyService = new PollyService(NewsConfig.bucketName(), NewsConfig.TTS_AUDIO_PREFIX);
		this.userStatsRepository = new UserStatsRepository();
		this.badgeService = new BadgeService();
	}

	public NewsLearningService(NewsArticleRepository articleRepository,
							   UserNewsRepository userNewsRepository,
							   PollyService pollyService,
							   UserStatsRepository userStatsRepository,
							   BadgeService badgeService) {
		this.articleRepository = articleRepository;
		this.userNewsRepository = userNewsRepository;
		this.pollyService = pollyService;
		this.userStatsRepository = userStatsRepository;
		this.badgeService = badgeService;
	}

	/**
	 * 뉴스 읽기 완료 기록
	 *
	 * @return 새로 획득한 배지 목록
	 */
	public List<UserBadge> markAsRead(String userId, String articleId) {
		Optional<NewsArticle> articleOpt = articleRepository.findById(articleId);
		if (articleOpt.isEmpty()) {
			logger.warn("기사를 찾을 수 없음: {}", articleId);
			return List.of();
		}

		if (userNewsRepository.hasRead(userId, articleId)) {
			logger.debug("이미 읽은 기사: userId={}, articleId={}", userId, articleId);
			return List.of();
		}

		NewsArticle article = articleOpt.get();
		saveReadRecord(userId, article);
		incrementArticleReadCount(article);

		logger.info("읽기 완료 기록: userId={}, articleId={}", userId, articleId);

		return updateStatsAndCheckBadges(userId);
	}

	/**
	 * 북마크 토글
	 */
	public boolean toggleBookmark(String userId, String articleId) {
		if (userNewsRepository.isBookmarked(userId, articleId)) {
			userNewsRepository.deleteBookmark(userId, articleId);
			logger.info("북마크 해제: userId={}, articleId={}", userId, articleId);
			return false;
		}

		Optional<NewsArticle> articleOpt = articleRepository.findById(articleId);
		if (articleOpt.isEmpty()) {
			logger.warn("기사를 찾을 수 없음: {}", articleId);
			return false;
		}

		NewsArticle article = articleOpt.get();
		userNewsRepository.saveBookmark(userId, articleId, article.getTitle(), article.getLevel(), article.getCategory());
		logger.info("북마크 추가: userId={}, articleId={}", userId, articleId);
		return true;
	}

	/**
	 * 북마크 여부 확인
	 */
	public boolean isBookmarked(String userId, String articleId) {
		return userNewsRepository.isBookmarked(userId, articleId);
	}

	/**
	 * 읽기 여부 확인
	 */
	public boolean hasRead(String userId, String articleId) {
		return userNewsRepository.hasRead(userId, articleId);
	}

	/**
	 * 여러 기사의 북마크 여부 확인 (배치)
	 */
	public Set<String> getBookmarkedArticleIds(String userId, List<String> articleIds) {
		return userNewsRepository.getBookmarkedArticleIds(userId, articleIds);
	}

	/**
	 * 사용자 북마크 목록 조회 (기사 정보 포함)
	 */
	public List<Map<String, Object>> getUserBookmarks(String userId, int limit) {
		List<UserNewsRecord> bookmarks = userNewsRepository.getUserBookmarks(userId, limit);

		return bookmarks.stream()
				.map(bookmark -> articleRepository.findById(bookmark.getArticleId())
						.map(article -> buildBookmarkResponse(article, bookmark))
						.orElse(null))
				.filter(Objects::nonNull)
				.toList();
	}

	/**
	 * 뉴스 TTS 오디오 URL 생성
	 */
	public String getAudioUrl(String articleId, String voice) {
		Optional<NewsArticle> articleOpt = articleRepository.findById(articleId);
		if (articleOpt.isEmpty()) {
			logger.warn("기사를 찾을 수 없음: {}", articleId);
			return null;
		}

		NewsArticle article = articleOpt.get();
		String text = buildTtsText(article);

		PollyService.VoiceSynthesisResult result = pollyService.synthesizeSpeech(articleId, text, voice);
		return result.getAudioUrl();
	}

	/**
	 * 사용자 뉴스 학습 통계 조회
	 */
	public Map<String, Object> getUserStats(String userId) {
		UserNewsRepository.NewsStats stats = userNewsRepository.getUserStats(userId);

		return Map.of(
				"totalRead", stats.totalRead(),
				"thisWeekRead", stats.thisWeekRead(),
				"totalBookmarks", stats.totalBookmarks(),
				"byLevel", stats.byLevel(),
				"byCategory", stats.byCategory()
		);
	}

	// ========== Private Helper Methods ==========

	private void saveReadRecord(String userId, NewsArticle article) {
		userNewsRepository.saveReadRecord(
				userId,
				article.getArticleId(),
				article.getTitle(),
				article.getLevel(),
				article.getCategory()
		);
	}

	private void incrementArticleReadCount(NewsArticle article) {
		String date = NewsKey.extractDateFromPk(article.getPk());
		if (date != null) {
			articleRepository.incrementReadCount(date, article.getArticleId());
		}
	}

	private List<UserBadge> updateStatsAndCheckBadges(String userId) {
		try {
			UserStats updatedStats = userStatsRepository.incrementNewsReadStats(userId);
			if (updatedStats != null) {
				List<UserBadge> newBadges = badgeService.checkAndAwardBadges(userId, updatedStats);
				if (!newBadges.isEmpty()) {
					logger.info("새 배지 획득: userId={}, badges={}",
							userId, newBadges.stream().map(UserBadge::getBadgeType).toList());
				}
				return newBadges;
			}
		} catch (Exception e) {
			logger.error("통계/배지 업데이트 실패: userId={}, error={}", userId, e.getMessage());
		}
		return List.of();
	}

	private Map<String, Object> buildBookmarkResponse(NewsArticle article, UserNewsRecord bookmark) {
		Map<String, Object> response = new HashMap<>();
		response.put("articleId", article.getArticleId());
		response.put("title", article.getTitle());
		response.put("summary", article.getSummary());
		response.put("source", article.getSource());
		response.put("publishedAt", article.getPublishedAt());
		response.put("keywords", article.getKeywords());
		response.put("highlightWords", article.getHighlightWords());
		response.put("category", article.getCategory());
		response.put("level", article.getLevel());
		response.put("imageUrl", article.getImageUrl());
		response.put("bookmarkedAt", bookmark.getCreatedAt());
		return response;
	}

	private String buildTtsText(NewsArticle article) {
		String text = article.getTitle() + ". " + (article.getSummary() != null ? article.getSummary() : "");
		if (text.length() > NewsConfig.TTS_MAX_TEXT_LENGTH) {
			text = text.substring(0, NewsConfig.TTS_MAX_TEXT_LENGTH);
		}
		return text;
	}
}
