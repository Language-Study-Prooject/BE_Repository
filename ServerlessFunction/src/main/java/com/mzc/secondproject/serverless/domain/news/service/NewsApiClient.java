package com.mzc.secondproject.serverless.domain.news.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mzc.secondproject.serverless.common.config.AwsClients;
import com.mzc.secondproject.serverless.domain.news.dto.RawNewsArticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * NewsAPI 연동 클라이언트
 * 무료 플랜: 100 requests/day, 최대 100 articles/request
 */
public class NewsApiClient {

	private static final Logger logger = LoggerFactory.getLogger(NewsApiClient.class);
	private static final String NEWS_API_BASE_URL = "https://newsapi.org/v2";
	private static final String API_KEY_PARAM_NAME = "/englishstudy/news/api-key";

	private static String cachedApiKey = null;

	private final HttpClient httpClient;

	public NewsApiClient() {
		this.httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(10))
				.build();
	}

	/**
	 * API Key 조회 (Parameter Store + 캐싱)
	 */
	private String getApiKey() {
		if (cachedApiKey != null) {
			return cachedApiKey;
		}

		try {
			logger.debug("Fetching NewsAPI Key from Parameter Store");
			var response = AwsClients.ssm().getParameter(
					GetParameterRequest.builder()
							.name(API_KEY_PARAM_NAME)
							.withDecryption(true)
							.build()
			);
			cachedApiKey = response.parameter().value();
			logger.info("NewsAPI Key loaded from Parameter Store");
			return cachedApiKey;
		} catch (Exception e) {
			logger.error("Failed to get NewsAPI Key from Parameter Store", e);
			throw new RuntimeException("NewsAPI Key 로드 실패", e);
		}
	}

	/**
	 * 헤드라인 뉴스 조회
	 */
	public List<RawNewsArticle> getTopHeadlines(String category, int pageSize) {
		String url = String.format("%s/top-headlines?language=en&category=%s&pageSize=%d&apiKey=%s",
				NEWS_API_BASE_URL, category, pageSize, getApiKey());

		return fetchArticles(url, "NewsAPI-Headlines");
	}

	/**
	 * 검색어 기반 뉴스 조회
	 */
	public List<RawNewsArticle> searchNews(String query, int pageSize) {
		String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
		String url = String.format("%s/everything?q=%s&language=en&sortBy=publishedAt&pageSize=%d&apiKey=%s",
				NEWS_API_BASE_URL, encodedQuery, pageSize, getApiKey());

		return fetchArticles(url, "NewsAPI-Search");
	}

	/**
	 * 뉴스 API 호출 및 파싱
	 */
	private List<RawNewsArticle> fetchArticles(String url, String source) {
		List<RawNewsArticle> articles = new ArrayList<>();

		try {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(url))
					.header("Accept", "application/json")
					.timeout(Duration.ofSeconds(30))
					.GET()
					.build();

			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

			if (response.statusCode() != 200) {
				logger.error("NewsAPI 요청 실패 - status: {}, body: {}", response.statusCode(), response.body());
				return articles;
			}

			JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
			String status = json.get("status").getAsString();

			if (!"ok".equals(status)) {
				logger.error("NewsAPI 응답 오류 - status: {}", status);
				return articles;
			}

			JsonArray articlesArray = json.getAsJsonArray("articles");
			for (JsonElement element : articlesArray) {
				JsonObject articleJson = element.getAsJsonObject();
				RawNewsArticle article = parseArticle(articleJson, source);
				if (article.isValid()) {
					articles.add(article);
				}
			}

			logger.info("NewsAPI에서 {}개 기사 수집 완료", articles.size());

		} catch (Exception e) {
			logger.error("NewsAPI 호출 중 오류 발생", e);
		}

		return articles;
	}

	/**
	 * JSON을 RawNewsArticle로 변환
	 */
	private RawNewsArticle parseArticle(JsonObject json, String defaultSource) {
		String sourceName = defaultSource;
		if (json.has("source") && json.get("source").isJsonObject()) {
			JsonObject sourceObj = json.getAsJsonObject("source");
			if (sourceObj.has("name") && !sourceObj.get("name").isJsonNull()) {
				sourceName = sourceObj.get("name").getAsString();
			}
		}

		return RawNewsArticle.builder()
				.title(getStringOrNull(json, "title"))
				.description(getStringOrNull(json, "description"))
				.url(getStringOrNull(json, "url"))
				.imageUrl(getStringOrNull(json, "urlToImage"))
				.source(sourceName)
				.publishedAt(getStringOrNull(json, "publishedAt"))
				.content(getStringOrNull(json, "content"))
				.build();
	}

	private String getStringOrNull(JsonObject json, String key) {
		if (json.has(key) && !json.get(key).isJsonNull()) {
			return json.get(key).getAsString();
		}
		return null;
	}
}
