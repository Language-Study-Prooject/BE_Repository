package com.mzc.secondproject.serverless.domain.news.service;

import com.mzc.secondproject.serverless.domain.news.constants.NewsKey;
import com.mzc.secondproject.serverless.domain.news.dto.RawNewsArticle;
import com.mzc.secondproject.serverless.domain.news.model.NewsArticle;
import com.mzc.secondproject.serverless.domain.news.repository.NewsArticleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * 뉴스 수집 서비스
 * RSS 피드에서 뉴스를 수집하고 저장 (BBC, VOA, NPR)
 */
public class NewsCollectorService {

	private static final Logger logger = LoggerFactory.getLogger(NewsCollectorService.class);

	private static final int RSS_LIMIT_PER_SOURCE = 7;
	private static final long TTL_DAYS = 30;

	private final RssFeedParser rssFeedParser;
	private final NewsDuplicateChecker duplicateChecker;
	private final NewsArticleRepository articleRepository;
	private final NewsAnalysisService analysisService;

	public NewsCollectorService() {
		this.rssFeedParser = new RssFeedParser();
		this.duplicateChecker = new NewsDuplicateChecker();
		this.articleRepository = new NewsArticleRepository();
		this.analysisService = new NewsAnalysisService();
	}

	public NewsCollectorService(RssFeedParser rssFeedParser,
								NewsDuplicateChecker duplicateChecker,
								NewsArticleRepository articleRepository,
								NewsAnalysisService analysisService) {
		this.rssFeedParser = rssFeedParser;
		this.duplicateChecker = duplicateChecker;
		this.articleRepository = articleRepository;
		this.analysisService = analysisService;
	}

	/**
	 * 뉴스 수집 실행
	 */
	public CollectionResult collectNews() {
		logger.info("뉴스 수집 시작");
		long startTime = System.currentTimeMillis();

		List<RawNewsArticle> rssArticles;
		try {
			rssArticles = rssFeedParser.fetchAllFeeds(RSS_LIMIT_PER_SOURCE);
			logger.info("RSS에서 {}개 수집", rssArticles.size());
		} catch (Exception e) {
			logger.error("RSS 수집 실패", e);
			return new CollectionResult(0, 0, System.currentTimeMillis() - startTime);
		}

		List<RawNewsArticle> uniqueArticles = duplicateChecker.filterDuplicates(rssArticles);
		logger.info("중복 제거 후 {}개 기사", uniqueArticles.size());

		int savedCount = 0;
		int analyzedCount = 0;
		for (RawNewsArticle rawArticle : uniqueArticles) {
			try {
				NewsArticle article = convertToNewsArticle(rawArticle);

				// AI 분석 수행 (난이도, 요약, 키워드, 퀴즈)
				analysisService.analyzeArticle(article);
				analyzedCount++;
				savedCount++;
			} catch (Exception e) {
				logger.error("기사 처리 실패: {}", rawArticle.getTitle(), e);
			}
		}

		long elapsed = System.currentTimeMillis() - startTime;
		logger.info("뉴스 수집/분석 완료 - 저장: {}, 분석: {}, 소요시간: {}ms", savedCount, analyzedCount, elapsed);

		return new CollectionResult(rssArticles.size(), savedCount, elapsed);
	}

	/**
	 * RawNewsArticle을 NewsArticle로 변환
	 * AI 분석은 별도 Story에서 처리
	 */
	private NewsArticle convertToNewsArticle(RawNewsArticle raw) {
		String today = LocalDate.now().toString();
		String articleId = UUID.randomUUID().toString().substring(0, 8);
		String now = Instant.now().toString();

		long ttlEpoch = Instant.now()
				.atOffset(ZoneOffset.UTC)
				.plusDays(TTL_DAYS)
				.toEpochSecond();

		return NewsArticle.builder()
				.pk(NewsKey.newsPk(today))
				.sk(NewsKey.articleSk(articleId))
				.articleId(articleId)
				.title(raw.getTitle())
				.summary(raw.getDescription())
				.originalUrl(raw.getUrl())
				.source(raw.getSource())
				.imageUrl(raw.getImageUrl())
				.publishedAt(raw.getPublishedAt() != null ? raw.getPublishedAt() : now)
				.collectedAt(now)
				.readCount(0L)
				.commentCount(0L)
				.ttl(ttlEpoch)
				.build();
	}

	/**
	 * 수집 결과 레코드
	 */
	public record CollectionResult(
			int collectedCount,
			int savedCount,
			long elapsedMs
	) {
	}
}
