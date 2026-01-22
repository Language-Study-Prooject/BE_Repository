package com.mzc.secondproject.serverless.domain.news.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 수집된 원본 뉴스 기사 DTO
 * NewsAPI, RSS 등에서 수집한 원본 데이터를 담는 객체
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RawNewsArticle {

	private String title;
	private String description;
	private String url;
	private String imageUrl;
	private String source;
	private String publishedAt;
	private String content;

	/**
	 * URL 기반 고유 식별자 생성
	 */
	public String generateId() {
		if (url == null) {
			return null;
		}
		return String.valueOf(url.hashCode());
	}

	/**
	 * 유효한 기사인지 검증
	 */
	public boolean isValid() {
		return title != null && !title.isBlank()
				&& url != null && !url.isBlank()
				&& source != null && !source.isBlank();
	}
}
