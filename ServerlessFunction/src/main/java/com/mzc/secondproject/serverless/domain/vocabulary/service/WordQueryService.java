package com.mzc.secondproject.serverless.domain.vocabulary.service;

import com.mzc.secondproject.serverless.common.dto.PaginatedResult;
import com.mzc.secondproject.serverless.domain.vocabulary.model.Word;
import com.mzc.secondproject.serverless.domain.vocabulary.repository.WordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Word 조회 전용 서비스 (CQRS Query)
 */
public class WordQueryService {
	
	private static final Logger logger = LoggerFactory.getLogger(WordQueryService.class);
	
	private final WordRepository wordRepository;

	/**
	 * 기본 생성자 (Lambda에서 사용)
	 */
	public WordQueryService() {
		this(new WordRepository());
	}

	/**
	 * 의존성 주입 생성자 (테스트 용이성)
	 */
	public WordQueryService(WordRepository wordRepository) {
		this.wordRepository = wordRepository;
	}
	
	public Optional<Word> getWord(String wordId) {
		return wordRepository.findById(wordId);
	}
	
	public PaginatedResult<Word> getWords(String level, String category, int limit, String cursor) {
		if (level != null && !level.isEmpty()) {
			return wordRepository.findByLevelWithPagination(level, limit, cursor);
		} else if (category != null && !category.isEmpty()) {
			return wordRepository.findByCategoryWithPagination(category, limit, cursor);
		}
		return wordRepository.findByLevelWithPagination("BEGINNER", limit, cursor);
	}
	
	public PaginatedResult<Word> searchWords(String query, int limit, String cursor) {
		return wordRepository.searchByKeyword(query, limit, cursor);
	}
	
	public List<Word> getWordsByIds(List<String> wordIds) {
		return wordRepository.findByIds(wordIds);
	}
}
