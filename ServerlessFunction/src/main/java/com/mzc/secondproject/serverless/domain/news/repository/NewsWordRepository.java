package com.mzc.secondproject.serverless.domain.news.repository;

import com.mzc.secondproject.serverless.common.config.AwsClients;
import com.mzc.secondproject.serverless.common.config.EnvConfig;
import com.mzc.secondproject.serverless.domain.news.constants.NewsKey;
import com.mzc.secondproject.serverless.domain.news.model.NewsWordCollect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 뉴스 단어 수집 Repository
 */
public class NewsWordRepository {
	
	private static final Logger logger = LoggerFactory.getLogger(NewsWordRepository.class);
	private static final String TABLE_NAME = EnvConfig.getRequired("NEWS_TABLE_NAME");
	
	private final DynamoDbTable<NewsWordCollect> table;
	private final DynamoDbIndex<NewsWordCollect> gsi1Index;
	
	public NewsWordRepository() {
		this(AwsClients.dynamoDbEnhanced());
	}
	
	public NewsWordRepository(DynamoDbEnhancedClient enhancedClient) {
		this.table = enhancedClient.table(TABLE_NAME, TableSchema.fromBean(NewsWordCollect.class));
		this.gsi1Index = table.index("GSI1");
	}
	
	/**
	 * 단어 수집 저장
	 */
	public void save(NewsWordCollect wordCollect) {
		table.putItem(wordCollect);
		logger.debug("단어 수집 저장: userId={}, word={}", wordCollect.getUserId(), wordCollect.getWord());
	}
	
	/**
	 * 단어 수집 조회
	 */
	public Optional<NewsWordCollect> findByUserWordArticle(String userId, String word, String articleId) {
		Key key = Key.builder()
				.partitionValue(NewsKey.userNewsPk(userId))
				.sortValue(NewsKey.wordSk(word, articleId))
				.build();
		
		NewsWordCollect result = table.getItem(key);
		return Optional.ofNullable(result);
	}
	
	/**
	 * 이미 수집했는지 확인
	 */
	public boolean hasCollected(String userId, String word, String articleId) {
		return findByUserWordArticle(userId, word, articleId).isPresent();
	}
	
	/**
	 * 단어 수집 삭제
	 */
	public void delete(String userId, String word, String articleId) {
		Key key = Key.builder()
				.partitionValue(NewsKey.userNewsPk(userId))
				.sortValue(NewsKey.wordSk(word, articleId))
				.build();
		
		table.deleteItem(key);
		logger.debug("단어 수집 삭제: userId={}, word={}", userId, word);
	}
	
	/**
	 * 사용자 수집 단어 목록 조회 (최신순)
	 */
	public List<NewsWordCollect> getUserWords(String userId, int limit) {
		QueryConditional queryConditional = QueryConditional.keyEqualTo(
				Key.builder()
						.partitionValue(NewsKey.userNewsWordsPk(userId))
						.build()
		);
		
		QueryEnhancedRequest request = QueryEnhancedRequest.builder()
				.queryConditional(queryConditional)
				.scanIndexForward(false)
				.limit(limit)
				.build();
		
		List<NewsWordCollect> results = new ArrayList<>();
		for (Page<NewsWordCollect> page : gsi1Index.query(request)) {
			results.addAll(page.items());
			if (results.size() >= limit) break;
		}
		
		return results.subList(0, Math.min(results.size(), limit));
	}
	
	/**
	 * 사용자 수집 단어 수 조회
	 */
	public int countUserWords(String userId) {
		QueryConditional queryConditional = QueryConditional.sortBeginsWith(
				Key.builder()
						.partitionValue(NewsKey.userNewsPk(userId))
						.sortValue("WORD#")
						.build()
		);
		
		int count = 0;
		for (Page<NewsWordCollect> page : table.query(queryConditional)) {
			count += page.items().size();
		}
		return count;
	}
	
	/**
	 * Vocabulary 연동 상태 업데이트
	 */
	public void updateSyncStatus(String userId, String word, String articleId, String vocabUserWordId) {
		Optional<NewsWordCollect> wordOpt = findByUserWordArticle(userId, word, articleId);
		if (wordOpt.isPresent()) {
			NewsWordCollect wordCollect = wordOpt.get();
			wordCollect.setSyncedToVocab(true);
			wordCollect.setVocabUserWordId(vocabUserWordId);
			table.putItem(wordCollect);
			logger.debug("Vocabulary 연동 완료: userId={}, word={}", userId, word);
		}
	}
}
