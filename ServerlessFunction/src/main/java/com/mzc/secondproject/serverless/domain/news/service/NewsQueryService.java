package com.mzc.secondproject.serverless.domain.news.service;

import com.mzc.secondproject.serverless.common.dto.PaginatedResult;
import com.mzc.secondproject.serverless.domain.news.model.NewsArticle;
import com.mzc.secondproject.serverless.domain.news.repository.NewsArticleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 뉴스 조회 서비스
 */
public class NewsQueryService {

	private static final Logger logger = LoggerFactory.getLogger(NewsQueryService.class);

	private final NewsArticleRepository articleRepository;

	public NewsQueryService() {
		this.articleRepository = new NewsArticleRepository();
	}

	public NewsQueryService(NewsArticleRepository articleRepository) {
		this.articleRepository = articleRepository;
	}

	/**
	 * 뉴스 상세 조회
	 */
	public Optional<NewsArticle> getArticle(String articleId) {
		logger.debug("뉴스 상세 조회: {}", articleId);
		Optional<NewsArticle> article = articleRepository.findById(articleId);

		// 조회수 증가
		article.ifPresent(a -> {
			String date = extractDateFromPk(a.getPk());
			if (date != null) {
				articleRepository.incrementReadCount(date, articleId);
			}
		});

		return article;
	}

	/**
	 * 오늘의 뉴스 목록 조회
	 */
	public PaginatedResult<NewsArticle> getTodayNews(int limit, String cursor) {
		String today = LocalDate.now().toString();
		logger.debug("오늘의 뉴스 조회: date={}, limit={}", today, limit);
		return articleRepository.findByDate(today, limit, cursor);
	}

	/**
	 * 레벨별 뉴스 조회
	 */
	public PaginatedResult<NewsArticle> getNewsByLevel(String level, int limit, String cursor) {
		logger.debug("레벨별 뉴스 조회: level={}, limit={}", level, limit);
		return articleRepository.findByLevel(level, limit, cursor);
	}

	/**
	 * 카테고리별 뉴스 조회
	 */
	public PaginatedResult<NewsArticle> getNewsByCategory(String category, int limit, String cursor) {
		logger.debug("카테고리별 뉴스 조회: category={}, limit={}", category, limit);
		return articleRepository.findByCategory(category, limit, cursor);
	}

	/**
	 * 레벨 + 카테고리 복합 필터 조회
	 */
	public PaginatedResult<NewsArticle> getNewsByLevelAndCategory(String level, String category, int limit, String cursor) {
		logger.debug("레벨+카테고리 뉴스 조회: level={}, category={}, limit={}", level, category, limit);
		return articleRepository.findByLevelAndCategory(level, category, limit, cursor);
	}

	/**
	 * 사용자 레벨 맞춤 뉴스 추천
	 */
	public PaginatedResult<NewsArticle> getRecommendedNews(String userLevel, int limit, String cursor) {
		logger.debug("맞춤 뉴스 추천: userLevel={}, limit={}", userLevel, limit);
		// 사용자 레벨에 맞는 뉴스 조회
		return articleRepository.findByLevel(userLevel, limit, cursor);
	}

	/**
	 * PK에서 날짜 추출 (NEWS#2024-01-15 → 2024-01-15)
	 */
	private String extractDateFromPk(String pk) {
		if (pk == null || !pk.startsWith("NEWS#")) {
			return null;
		}
		return pk.substring(5);
	}
}
