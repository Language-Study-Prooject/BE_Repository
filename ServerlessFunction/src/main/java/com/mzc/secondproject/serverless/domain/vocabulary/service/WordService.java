package com.mzc.secondproject.serverless.domain.vocabulary.service;

import com.mzc.secondproject.serverless.common.dto.PaginatedResult;
import com.mzc.secondproject.serverless.domain.vocabulary.model.Word;
import com.mzc.secondproject.serverless.domain.vocabulary.repository.WordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

public class WordService {
	
	private static final Logger logger = LoggerFactory.getLogger(WordService.class);
	
	private final WordRepository wordRepository;
	
	public WordService() {
		this.wordRepository = new WordRepository();
	}
	
	public Word createWord(String english, String korean, String example, String level, String category) {
		String wordId = UUID.randomUUID().toString();
		String now = Instant.now().toString();
		
		Word word = Word.builder()
				.pk("WORD#" + wordId)
				.sk("METADATA")
				.gsi1pk("LEVEL#" + level)
				.gsi1sk("WORD#" + wordId)
				.gsi2pk("CATEGORY#" + category)
				.gsi2sk("WORD#" + wordId)
				.wordId(wordId)
				.english(english)
				.korean(korean)
				.example(example)
				.level(level)
				.category(category)
				.createdAt(now)
				.build();
		
		wordRepository.save(word);
		logger.info("Created word: {}", wordId);
		
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
		
		if (updates.containsKey("english")) {
			word.setEnglish((String) updates.get("english"));
		}
		if (updates.containsKey("korean")) {
			word.setKorean((String) updates.get("korean"));
		}
		if (updates.containsKey("example")) {
			word.setExample((String) updates.get("example"));
		}
		if (updates.containsKey("level")) {
			String newLevel = (String) updates.get("level");
			word.setLevel(newLevel);
			word.setGsi1pk("LEVEL#" + newLevel);
		}
		if (updates.containsKey("category")) {
			String newCategory = (String) updates.get("category");
			word.setCategory(newCategory);
			word.setGsi2pk("CATEGORY#" + newCategory);
		}
		
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
		String now = Instant.now().toString();
		List<Word> createdWords = new ArrayList<>();
		int successCount = 0;
		int failCount = 0;
		
		for (Map<String, Object> wordData : wordsList) {
			try {
				String english = (String) wordData.get("english");
				String korean = (String) wordData.get("korean");
				String example = (String) wordData.get("example");
				String level = (String) wordData.getOrDefault("level", "BEGINNER");
				String category = (String) wordData.getOrDefault("category", "DAILY");
				
				if (english == null || korean == null) {
					failCount++;
					continue;
				}
				
				String wordId = UUID.randomUUID().toString();
				
				Word word = Word.builder()
						.pk("WORD#" + wordId)
						.sk("METADATA")
						.gsi1pk("LEVEL#" + level)
						.gsi1sk("WORD#" + wordId)
						.gsi2pk("CATEGORY#" + category)
						.gsi2sk("WORD#" + wordId)
						.wordId(wordId)
						.english(english)
						.korean(korean)
						.example(example)
						.level(level)
						.category(category)
						.createdAt(now)
						.build();
				
				wordRepository.save(word);
				createdWords.add(word);
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
