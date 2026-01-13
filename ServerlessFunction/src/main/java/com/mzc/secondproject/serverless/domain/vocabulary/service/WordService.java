package com.mzc.secondproject.serverless.domain.vocabulary.service;

import com.mzc.secondproject.serverless.common.dto.PaginatedResult;
import com.mzc.secondproject.serverless.domain.vocabulary.factory.WordFactory;
import com.mzc.secondproject.serverless.domain.vocabulary.model.Word;
import com.mzc.secondproject.serverless.domain.vocabulary.repository.WordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class WordService {

	private static final Logger logger = LoggerFactory.getLogger(WordService.class);

	private final WordRepository wordRepository;
	private final WordFactory wordFactory;

	/**
	 * 기본 생성자 (Lambda에서 사용)
	 */
	public WordService() {
		this(new WordRepository(), new WordFactory());
	}

	/**
	 * 의존성 주입 생성자 (테스트 용이성)
	 */
	public WordService(WordRepository wordRepository, WordFactory wordFactory) {
		this.wordRepository = wordRepository;
		this.wordFactory = wordFactory;
	}
	
	public Word createWord(String english, String korean, String example, String level, String category) {
		Word word = wordFactory.create(english, korean, example, level, category);
		wordRepository.save(word);
		logger.info("Created word: {}", word.getWordId());
		return word;
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
	
	public Word updateWord(String wordId, Map<String, Object> updates) {
		Optional<Word> optWord = wordRepository.findById(wordId);
		if (optWord.isEmpty()) {
			throw new IllegalArgumentException("Word not found");
		}

		Word word = optWord.get();
		wordFactory.updateFields(
				word,
				(String) updates.get("english"),
				(String) updates.get("korean"),
				(String) updates.get("example"),
				(String) updates.get("level"),
				(String) updates.get("category")
		);

		wordRepository.save(word);
		logger.info("Updated word: {}", wordId);
		return word;
	}
	
	public void deleteWord(String wordId) {
		Optional<Word> optWord = wordRepository.findById(wordId);
		if (optWord.isEmpty()) {
			throw new IllegalArgumentException("Word not found");
		}
		
		wordRepository.delete(wordId);
		logger.info("Deleted word: {}", wordId);
	}
	
	public BatchResult createWordsBatch(List<Map<String, Object>> wordsList) {
		int successCount = 0;
		int failCount = 0;

		for (Map<String, Object> wordData : wordsList) {
			try {
				String english = (String) wordData.get("english");
				String korean = (String) wordData.get("korean");
				String example = (String) wordData.get("example");
				String level = (String) wordData.get("level");
				String category = (String) wordData.get("category");

				if (english == null || korean == null) {
					failCount++;
					continue;
				}

				Word word = wordFactory.create(english, korean, example, level, category);
				wordRepository.save(word);
				successCount++;
			} catch (Exception e) {
				logger.error("Failed to create word", e);
				failCount++;
			}
		}

		logger.info("Batch created {} words, failed {}", successCount, failCount);
		return new BatchResult(successCount, failCount, wordsList.size());
	}
	
	public PaginatedResult<Word> searchWords(String query, int limit, String cursor) {
		return wordRepository.searchByKeyword(query, limit, cursor);
	}
	
	public record BatchResult(int successCount, int failCount, int totalRequested) {
	}
}
