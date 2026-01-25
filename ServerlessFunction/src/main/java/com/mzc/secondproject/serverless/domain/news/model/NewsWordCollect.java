package com.mzc.secondproject.serverless.domain.news.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

/**
 * 뉴스 단어 수집
 * PK: USER#{userId}#NEWS
 * SK: WORD#{word}#{articleId}
 * GSI1: USER#{userId}#NEWS_WORDS / {collectedAt}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class NewsWordCollect {

	private String pk;          // USER#{userId}#NEWS
	private String sk;          // WORD#{word}#{articleId}
	private String gsi1pk;      // USER#{userId}#NEWS_WORDS
	private String gsi1sk;      // {collectedAt}

	private String userId;
	private String word;
	private String meaning;
	private String pronunciation;
	private String context;         // 문맥 문장
	private String articleId;
	private String articleTitle;
	private String collectedAt;
	private Boolean syncedToVocab;  // Vocabulary 연동 여부
	private String vocabUserWordId; // 연동된 UserWord ID
	private Long ttl;

	@DynamoDbPartitionKey
	@DynamoDbAttribute("PK")
	public String getPk() {
		return pk;
	}

	@DynamoDbSortKey
	@DynamoDbAttribute("SK")
	public String getSk() {
		return sk;
	}

	@DynamoDbSecondaryPartitionKey(indexNames = "GSI1")
	@DynamoDbAttribute("GSI1PK")
	public String getGsi1pk() {
		return gsi1pk;
	}

	@DynamoDbSecondarySortKey(indexNames = "GSI1")
	@DynamoDbAttribute("GSI1SK")
	public String getGsi1sk() {
		return gsi1sk;
	}
}
