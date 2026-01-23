package com.mzc.secondproject.serverless.domain.news.service;

import com.mzc.secondproject.serverless.common.config.EnvConfig;
import com.mzc.secondproject.serverless.common.service.PollyService;
import com.mzc.secondproject.serverless.domain.news.model.NewsArticle;
import com.mzc.secondproject.serverless.domain.news.model.UserNewsRecord;
import com.mzc.secondproject.serverless.domain.news.repository.NewsArticleRepository;
import com.mzc.secondproject.serverless.domain.news.repository.UserNewsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 뉴스 학습 부가 기능 서비스
 */
public class NewsLearningService {

	private static final Logger logger = LoggerFactory.getLogger(NewsLearningService.class);
	private static final String BUCKET_NAME = EnvConfig.getOrDefault("NEWS_BUCKET_NAME", "group2-englishstudy");

	private final NewsArticleRepository articleRepository;
	private final UserNewsRepository userNewsRepository;
	private final PollyService pollyService;

	public NewsLearningService() {
		this.articleRepository = new NewsArticleRepository();
		this.userNewsRepository = new UserNewsRepository();
		this.pollyService = new PollyService(BUCKET_NAME, "news/audio/");
	}

	public NewsLearningService(NewsArticleRepository articleRepository,
							   UserNewsRepository userNewsRepository,
							   PollyService pollyService) {
		this.articleRepository = articleRepository;
		this.userNewsRepository = userNewsRepository;
		this.pollyService = pollyService;
	}

	/**
	 * 뉴스 읽기 완료 기록
	 */
	public void markAsRead(String userId, String articleId) {
		Optional<NewsArticle> article = articleRepository.findById(articleId);
		if (article.isEmpty()) {
			logger.warn("기사를 찾을 수 없음: {}", articleId);
			return;
		}

		NewsArticle a = article.get();
		userNewsRepository.saveReadRecord(
				userId,
				articleId,
				a.getTitle(),
				a.getLevel(),
				a.getCategory()
		);

		// 조회수 증가
		String date = extractDateFromPk(a.getPk());
		if (date != null) {
			articleRepository.incrementReadCount(date, articleId);
		}

		logger.info("읽기 완료 기록: userId={}, articleId={}", userId, articleId);
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
	 * 사용자 북마크 목록 조회
	 */
	public List<UserNewsRecord> getUserBookmarks(String userId, int limit) {
		return userNewsRepository.getUserBookmarks(userId, limit);
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
