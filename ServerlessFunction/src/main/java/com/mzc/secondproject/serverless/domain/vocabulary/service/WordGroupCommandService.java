package com.mzc.secondproject.serverless.domain.vocabulary.service;

import com.mzc.secondproject.serverless.domain.vocabulary.exception.VocabularyException;
import com.mzc.secondproject.serverless.domain.vocabulary.model.WordGroup;
import com.mzc.secondproject.serverless.domain.vocabulary.repository.WordGroupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * WordGroup 변경 전용 서비스 (CQRS Command)
 */
public class WordGroupCommandService {
	
	private static final Logger logger = LoggerFactory.getLogger(WordGroupCommandService.class);
	
	private final WordGroupRepository wordGroupRepository;
	
	public WordGroupCommandService() {
		this.wordGroupRepository = new WordGroupRepository();
	}
	
	public WordGroup createGroup(String userId, String groupName, String description) {
		String groupId = UUID.randomUUID().toString();
		String now = Instant.now().toString();
		
		WordGroup wordGroup = WordGroup.builder()
				.pk("USER#" + userId + "#GROUP")
				.sk("GROUP#" + groupId)
				.groupId(groupId)
				.userId(userId)
				.groupName(groupName)
				.description(description)
				.wordIds(new ArrayList<>())
				.wordCount(0)
				.createdAt(now)
				.updatedAt(now)
				.build();
		
		wordGroupRepository.save(wordGroup);
		logger.info("Created word group: userId={}, groupId={}, name={}", userId, groupId, groupName);
		
		return wordGroup;
	}
	
	public WordGroup updateGroup(String userId, String groupId, String groupName, String description) {
		Optional<WordGroup> optGroup = wordGroupRepository.findByUserIdAndGroupId(userId, groupId);
		if (optGroup.isEmpty()) {
			throw VocabularyException.groupNotFound(groupId);
		}
		
		WordGroup group = optGroup.get();
		if (groupName != null) {
			group.setGroupName(groupName);
		}
		if (description != null) {
			group.setDescription(description);
		}
		group.setUpdatedAt(Instant.now().toString());
		
		wordGroupRepository.save(group);
		logger.info("Updated word group: userId={}, groupId={}", userId, groupId);
		
		return group;
	}
	
	public void deleteGroup(String userId, String groupId) {
		Optional<WordGroup> optGroup = wordGroupRepository.findByUserIdAndGroupId(userId, groupId);
		if (optGroup.isEmpty()) {
			throw VocabularyException.groupNotFound(groupId);
		}
		
		wordGroupRepository.delete(userId, groupId);
		logger.info("Deleted word group: userId={}, groupId={}", userId, groupId);
	}
	
	public WordGroup addWordToGroup(String userId, String groupId, String wordId) {
		Optional<WordGroup> optGroup = wordGroupRepository.findByUserIdAndGroupId(userId, groupId);
		if (optGroup.isEmpty()) {
			throw VocabularyException.groupNotFound(groupId);
		}
		
		WordGroup group = optGroup.get();
		List<String> wordIds = group.getWordIds();
		if (wordIds == null) {
			wordIds = new ArrayList<>();
		}
		
		if (!wordIds.contains(wordId)) {
			wordIds.add(wordId);
			group.setWordIds(wordIds);
			group.setWordCount(wordIds.size());
			group.setUpdatedAt(Instant.now().toString());
			wordGroupRepository.save(group);
			logger.info("Added word to group: userId={}, groupId={}, wordId={}", userId, groupId, wordId);
		}
		
		return group;
	}
	
	public WordGroup removeWordFromGroup(String userId, String groupId, String wordId) {
		Optional<WordGroup> optGroup = wordGroupRepository.findByUserIdAndGroupId(userId, groupId);
		if (optGroup.isEmpty()) {
			throw VocabularyException.groupNotFound(groupId);
		}
		
		WordGroup group = optGroup.get();
		List<String> wordIds = group.getWordIds();
		if (wordIds != null && wordIds.remove(wordId)) {
			group.setWordIds(wordIds);
			group.setWordCount(wordIds.size());
			group.setUpdatedAt(Instant.now().toString());
			wordGroupRepository.save(group);
			logger.info("Removed word from group: userId={}, groupId={}, wordId={}", userId, groupId, wordId);
		}
		
		return group;
	}
}
