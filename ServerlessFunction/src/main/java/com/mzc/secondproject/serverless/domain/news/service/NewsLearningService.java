package com.mzc.secondproject.serverless.domain.news.service;

import com.mzc.secondproject.serverless.common.config.EnvConfig;
import com.mzc.secondproject.serverless.common.service.PollyService;
import com.mzc.secondproject.serverless.domain.badge.model.UserBadge;
import com.mzc.secondproject.serverless.domain.badge.service.BadgeService;
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
	private static final String BUCKET_NAME = EnvConfig.getOrDefault("NEWS_BUCKET_NAME", "group2-englishstudy");
	
	private final NewsArticleRepository articleRepository;
	private final UserNewsRepository userNewsRepository;
	private final PollyService pollyService;
	private final UserStatsRepository userStatsRepository;
	private final BadgeService badgeService;
	
	public NewsLearningService() {
		this.articleRepository = new NewsArticleRepository();
		this.userNewsRepository = new UserNewsRepository();
		this.pollyService = new PollyService(BUCKET_NAME, "news/audio/");
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
		Optional<NewsArticle> article = articleRepository.findById(articleId);
		if (article.isEmpty()) {
			logger.warn("기사를 찾을 수 없음: {}", articleId);
			return new ArrayList<>();
		}
		
		// 이미 읽은 기사인지 확인 (중복 조회수 증가 방지)
		if (userNewsRepository.hasRead(userId, articleId)) {
			logger.debug("이미 읽은 기사: userId={}, articleId={}", userId, articleId);
			return new ArrayList<>();
		}
		
		NewsArticle a = article.get();
		userNewsRepository.saveReadRecord(
				userId,
				articleId,
				a.getTitle(),
				a.getLevel(),
				a.getCategory()
		);
		
		// 조회수 증가 (새로운 읽기만)
		String date = extractDateFromPk(a.getPk());
		if (date != null) {
			articleRepository.incrementReadCount(date, articleId);
		}
		
		logger.info("읽기 완료 기록: userId={}, articleId={}", userId, articleId);
		
		// 통계 업데이트 및 배지 체크
		List<UserBadge> newBadges = new ArrayList<>();
		try {
			UserStats updatedStats = userStatsRepository.incrementNewsReadStats(userId);
			if (updatedStats != null) {
				newBadges = badgeService.checkAndAwardBadges(userId, updatedStats);
				if (!newBadges.isEmpty()) {
					logger.info("새 배지 획득: userId={}, badges={}", userId,
							newBadges.stream().map(UserBadge::getBadgeType).toList());
				}
			}
		} catch (Exception e) {
			logger.error("통계/배지 업데이트 실패: userId={}, error={}", userId, e.getMessage());
		}
		
		return newBadges;
	}
	
	/**
	 * 북마크 토글
	 */
	public boolean toggleBookmark(String userId, String articleId) {
		boolean isBookmarked = userNewsRepository.isBookmarked(userId, articleId);
		
		if (isBookmarked) {
			userNewsRepository.deleteBookmark(userId, articleId);
			logger.info("북마크 해제: userId={}, articleId={}", userId, articleId);
			return false;
		} else {
			Optional<NewsArticle> article = articleRepository.findById(articleId);
			if (article.isEmpty()) {
				logger.warn("기사를 찾을 수 없음: {}", articleId);
				return false;
			}
			
			NewsArticle a = article.get();
			userNewsRepository.saveBookmark(
					userId,
					articleId,
					a.getTitle(),
					a.getLevel(),
					a.getCategory()
			);
			logger.info("북마크 추가: userId={}, articleId={}", userId, articleId);
			return true;
		}
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
		List<Map<String, Object>> result = new ArrayList<>();
		
		for (UserNewsRecord bookmark : bookmarks) {
			Optional<NewsArticle> articleOpt = articleRepository.findById(bookmark.getArticleId());
			if (articleOpt.isPresent()) {
				NewsArticle article = articleOpt.get();
				Map<String, Object> bookmarkWithArticle = new HashMap<>();
				bookmarkWithArticle.put("articleId", article.getArticleId());
				bookmarkWithArticle.put("title", article.getTitle());
				bookmarkWithArticle.put("summary", article.getSummary());
				bookmarkWithArticle.put("source", article.getSource());
				bookmarkWithArticle.put("publishedAt", article.getPublishedAt());
				bookmarkWithArticle.put("keywords", article.getKeywords());
				bookmarkWithArticle.put("highlightWords", article.getHighlightWords());
				bookmarkWithArticle.put("category", article.getCategory());
				bookmarkWithArticle.put("level", article.getLevel());
				bookmarkWithArticle.put("imageUrl", article.getImageUrl());
				bookmarkWithArticle.put("bookmarkedAt", bookmark.getCreatedAt());
				result.add(bookmarkWithArticle);
			}
		}
		return result;
	}
	
	/**
	 * 뉴스 TTS 오디오 URL 생성
	 */
	public String getAudioUrl(String articleId, String voice) {
		Optional<NewsArticle> article = articleRepository.findById(articleId);
		if (article.isEmpty()) {
			logger.warn("기사를 찾을 수 없음: {}", articleId);
			return null;
		}
		
		NewsArticle a = article.get();
		String text = a.getTitle() + ". " + (a.getSummary() != null ? a.getSummary() : "");
		
		// 텍스트가 너무 길면 제한
		if (text.length() > 3000) {
			text = text.substring(0, 3000);
		}
		
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
	
	/**
	 * PK에서 날짜 추출
	 */
	private String extractDateFromPk(String pk) {
		if (pk == null || !pk.startsWith("NEWS#")) {
			return null;
		}
		return pk.substring(5);
	}
}
