package com.mzc.secondproject.serverless.domain.news.service;

import com.mzc.secondproject.serverless.domain.news.dto.RawNewsArticle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RSS 피드 파싱 서비스
 * BBC, VOA, NPR 등의 RSS 피드에서 뉴스 수집
 */
public class RssFeedParser {
	
	private static final Logger logger = LoggerFactory.getLogger(RssFeedParser.class);
	
	private static final Map<String, String> RSS_FEEDS = Map.of(
			"BBC", "https://feeds.bbci.co.uk/news/world/rss.xml",
			"VOA", "https://www.voanews.com/api/ziqpoe-mqm",
			"NPR", "https://feeds.npr.org/1001/rss.xml"
	);
	
	private final HttpClient httpClient;
	
	public RssFeedParser() {
		this.httpClient = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(10))
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build();
	}
	
	/**
	 * 모든 RSS 피드에서 뉴스 수집
	 */
	public List<RawNewsArticle> fetchAllFeeds(int maxPerSource) {
		List<RawNewsArticle> allArticles = new ArrayList<>();
		
		for (Map.Entry<String, String> entry : RSS_FEEDS.entrySet()) {
			String source = entry.getKey();
			String feedUrl = entry.getValue();
			
			try {
				List<RawNewsArticle> articles = fetchFeed(feedUrl, source, maxPerSource);
				allArticles.addAll(articles);
				logger.info("{}에서 {}개 기사 수집", source, articles.size());
			} catch (Exception e) {
				logger.error("{} RSS 피드 수집 실패: {}", source, e.getMessage());
			}
		}
		
		return allArticles;
	}
	
	/**
	 * 특정 RSS 피드에서 뉴스 수집
	 */
	public List<RawNewsArticle> fetchFeed(String feedUrl, String source, int maxItems) {
		List<RawNewsArticle> articles = new ArrayList<>();
		
		try {
			HttpRequest request = HttpRequest.newBuilder()
					.uri(URI.create(feedUrl))
					.header("User-Agent", "Mozilla/5.0 (compatible; NewsBot/1.0)")
					.timeout(Duration.ofSeconds(30))
					.GET()
					.build();
			
			HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
			
			if (response.statusCode() != 200) {
				logger.error("RSS 피드 요청 실패 - url: {}, status: {}", feedUrl, response.statusCode());
				return articles;
			}
			
			DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
			factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
			DocumentBuilder builder = factory.newDocumentBuilder();
			Document document = builder.parse(response.body());
			
			NodeList items = document.getElementsByTagName("item");
			int count = Math.min(items.getLength(), maxItems);
			
			for (int i = 0; i < count; i++) {
				Element item = (Element) items.item(i);
				RawNewsArticle article = parseRssItem(item, source);
				if (article.isValid()) {
					articles.add(article);
				}
			}
			
		} catch (Exception e) {
			logger.error("RSS 피드 파싱 중 오류 발생 - url: {}", feedUrl, e);
		}
		
		return articles;
	}
	
	/**
	 * RSS item 요소를 RawNewsArticle로 변환
	 */
	private RawNewsArticle parseRssItem(Element item, String source) {
		return RawNewsArticle.builder()
				.title(getElementText(item, "title"))
				.description(cleanHtml(getElementText(item, "description")))
				.url(getElementText(item, "link"))
				.imageUrl(extractImageUrl(item))
				.source(source)
				.publishedAt(parsePublishedDate(getElementText(item, "pubDate")))
				.build();
	}
	
	/**
	 * 요소에서 텍스트 추출
	 */
	private String getElementText(Element parent, String tagName) {
		NodeList nodes = parent.getElementsByTagName(tagName);
		if (nodes.getLength() > 0) {
			return nodes.item(0).getTextContent().trim();
		}
		return null;
	}
	
	/**
	 * 이미지 URL 추출 (media:content, enclosure 등)
	 */
	private String extractImageUrl(Element item) {
		NodeList mediaContent = item.getElementsByTagName("media:content");
		if (mediaContent.getLength() > 0) {
			Element media = (Element) mediaContent.item(0);
			return media.getAttribute("url");
		}
		
		NodeList enclosure = item.getElementsByTagName("enclosure");
		if (enclosure.getLength() > 0) {
			Element enc = (Element) enclosure.item(0);
			String type = enc.getAttribute("type");
			if (type != null && type.startsWith("image/")) {
				return enc.getAttribute("url");
			}
		}
		
		NodeList mediaThumbnail = item.getElementsByTagName("media:thumbnail");
		if (mediaThumbnail.getLength() > 0) {
			Element thumbnail = (Element) mediaThumbnail.item(0);
			return thumbnail.getAttribute("url");
		}
		
		return null;
	}
	
	/**
	 * RSS pubDate를 ISO 8601 형식으로 변환
	 */
	private String parsePublishedDate(String pubDate) {
		if (pubDate == null || pubDate.isBlank()) {
			return null;
		}
		return pubDate;
	}
	
	/**
	 * HTML 태그 제거
	 */
	private String cleanHtml(String html) {
		if (html == null) {
			return null;
		}
		return html.replaceAll("<[^>]*>", "").trim();
	}
}
