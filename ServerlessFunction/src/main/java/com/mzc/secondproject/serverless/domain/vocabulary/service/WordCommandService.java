package com.mzc.secondproject.serverless.domain.vocabulary.service;

import com.mzc.secondproject.serverless.common.constants.DynamoDbKey;
import com.mzc.secondproject.serverless.domain.vocabulary.constants.VocabKey;
import com.mzc.secondproject.serverless.domain.vocabulary.dto.request.CreateWordRequest;
import com.mzc.secondproject.serverless.domain.vocabulary.model.Word;
import com.mzc.secondproject.serverless.domain.vocabulary.repository.WordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

/**
 * Word 변경 전용 서비스 (CQRS Command)
 */
public class WordCommandService {
	
	private static final Logger logger = LoggerFactory.getLogger(WordCommandService.class);
	
	private final WordRepository wordRepository;
	
	public WordCommandService() {
		this.wordRepository = new WordRepository();
	}
	
	public Word createWord(String english, String korean, String example, String level, String category) {
		String wordId = UUID.randomUUID().toString();
		String now = Instant.now().toString();
		
		Word word = Word.builder()
				.pk(VocabKey.wordPk(wordId))
				.sk(DynamoDbKey.METADATA)
				.gsi1pk(VocabKey.levelPk(level))
				.gsi1sk(VocabKey.wordSk(wordId))
				.gsi2pk(VocabKey.categoryPk(category))
				.gsi2sk(VocabKey.wordSk(wordId))
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
			word.setGsi1pk(VocabKey.levelPk(newLevel));
		}
		if (updates.containsKey("category")) {
			String newCategory = (String) updates.get("category");
			word.setCategory(newCategory);
			word.setGsi2pk(VocabKey.categoryPk(newCategory));
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
	
	public BatchResult createWordsBatch(List<CreateWordRequest> wordsList) {
		String now = Instant.now().toString();
		List<Word> createdWords = new ArrayList<>();
		int successCount = 0;
		int failCount = 0;
		
		for (CreateWordRequest wordData : wordsList) {
			try {
				String english = wordData.getEnglish();
				String korean = wordData.getKorean();
				String example = wordData.getExample();
				String level = wordData.getLevel() != null ? wordData.getLevel() : "BEGINNER";
				String category = wordData.getCategory() != null ? wordData.getCategory() : "DAILY";
				
				if (english == null || korean == null) {
					failCount++;
					continue;
				}
				
				String wordId = UUID.randomUUID().toString();
				
				Word word = Word.builder()
						.pk(VocabKey.wordPk(wordId))
						.sk(DynamoDbKey.METADATA)
						.gsi1pk(VocabKey.levelPk(level))
						.gsi1sk(VocabKey.wordSk(wordId))
						.gsi2pk(VocabKey.categoryPk(category))
						.gsi2sk(VocabKey.wordSk(wordId))
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
	
	public record BatchResult(int successCount, int failCount, int totalRequested) {
	}
}
