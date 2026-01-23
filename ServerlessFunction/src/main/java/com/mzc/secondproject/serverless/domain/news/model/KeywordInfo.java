package com.mzc.secondproject.serverless.domain.news.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

/**
 * 뉴스 기사 내 키워드 정보
 * 단어, 뜻, 난이도, 위치 정보를 포함
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class KeywordInfo {

	private String word;        // 영어 단어
	private String meaning;     // 한국어 뜻
	private String level;       // 단어 난이도 (BEGINNER, INTERMEDIATE, ADVANCED)
	private Integer position;   // 기사 내 위치 (문장 번호 또는 단어 인덱스)
}
