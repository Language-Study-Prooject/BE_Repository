package com.mzc.secondproject.serverless.domain.news.service;

import com.mzc.secondproject.serverless.common.config.AwsClients;
import com.mzc.secondproject.serverless.common.config.EnvConfig;
import com.mzc.secondproject.serverless.domain.news.constants.NewsKey;
import com.mzc.secondproject.serverless.domain.news.dto.RawNewsArticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.time.LocalDate;
import java.util.*;

/**
 * 뉴스 중복 검사 서비스
 * URL 기반으로 중복 뉴스 필터링
 */
public class NewsDuplicateChecker {
	
	private static final Logger logger = LoggerFactory.getLogger(NewsDuplicateChecker.class);
	private static final String TABLE_NAME = EnvConfig.getRequired("NEWS_TABLE_NAME");
	
	/**
	 * 중복 뉴스 필터링
	 */
	public List<RawNewsArticle> filterDuplicates(List<RawNewsArticle> articles) {
		if (articles.isEmpty()) {
			return articles;
		}
		
		Set<String> existingUrls = getExistingUrls();
		Set<String> seenUrls = new HashSet<>();
		List<RawNewsArticle> uniqueArticles = new ArrayList<>();
		
		for (RawNewsArticle article : articles) {
			String url = article.getUrl();
			if (url == null) {
				continue;
			}
			
			if (!existingUrls.contains(url) && !seenUrls.contains(url)) {
				uniqueArticles.add(article);
				seenUrls.add(url);
			}
		}
		
		int duplicateCount = articles.size() - uniqueArticles.size();
		if (duplicateCount > 0) {
			logger.info("{}개 중복 기사 필터링됨", duplicateCount);
		}
		
		return uniqueArticles;
	}
	
	/**
	 * 오늘 날짜의 기존 뉴스 URL 조회
	 */
	private Set<String> getExistingUrls() {
		Set<String> urls = new HashSet<>();
		String today = LocalDate.now().toString();
		
		try {
			QueryRequest request = QueryRequest.builder()
					.tableName(TABLE_NAME)
					.keyConditionExpression("PK = :pk")
					.expressionAttributeValues(Map.of(
							":pk", AttributeValue.builder().s(NewsKey.newsPk(today)).build()
					))
					.projectionExpression("originalUrl")
					.build();
			
			QueryResponse response = AwsClients.dynamoDb().query(request);
			
			for (Map<String, AttributeValue> item : response.items()) {
				if (item.containsKey("originalUrl")) {
					urls.add(item.get("originalUrl").s());
				}
			}
			
			logger.debug("기존 뉴스 {}개 URL 로드됨", urls.size());
			
		} catch (Exception e) {
			logger.error("기존 뉴스 URL 조회 실패", e);
		}
		
		return urls;
	}
}
