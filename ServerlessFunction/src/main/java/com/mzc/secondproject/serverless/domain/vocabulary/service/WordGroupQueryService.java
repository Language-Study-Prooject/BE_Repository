package com.mzc.secondproject.serverless.domain.vocabulary.service;

import com.mzc.secondproject.serverless.common.dto.PaginatedResult;
import com.mzc.secondproject.serverless.domain.vocabulary.model.Word;
import com.mzc.secondproject.serverless.domain.vocabulary.model.WordGroup;
import com.mzc.secondproject.serverless.domain.vocabulary.repository.WordGroupRepository;
import com.mzc.secondproject.serverless.domain.vocabulary.repository.WordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * WordGroup 조회 전용 서비스 (CQRS Query)
 */
public class WordGroupQueryService {
	
	private static final Logger logger = LoggerFactory.getLogger(WordGroupQueryService.class);
	
	private final WordGroupRepository wordGroupRepository;
	private final WordRepository wordRepository;

	/**
	 * 기본 생성자 (Lambda에서 사용)
	 */
	public WordGroupQueryService() {
		this(new WordGroupRepository(), new WordRepository());
	}

	/**
	 * 의존성 주입 생성자 (테스트 용이성)
	 */
	public WordGroupQueryService(WordGroupRepository wordGroupRepository, WordRepository wordRepository) {
		this.wordGroupRepository = wordGroupRepository;
		this.wordRepository = wordRepository;
	}
	
	public PaginatedResult<WordGroup> getGroups(String userId, int limit, String cursor) {
		return wordGroupRepository.findByUserId(userId, limit, cursor);
	}
	
	public Optional<WordGroup> getGroup(String userId, String groupId) {
		return wordGroupRepository.findByUserIdAndGroupId(userId, groupId);
	}
	
	public Optional<WordGroupDetail> getGroupDetail(String userId, String groupId) {
		Optional<WordGroup> optGroup = wordGroupRepository.findByUserIdAndGroupId(userId, groupId);
		if (optGroup.isEmpty()) {
			return Optional.empty();
		}
		
		WordGroup group = optGroup.get();
		List<Word> words = List.of();
		
		if (group.getWordIds() != null && !group.getWordIds().isEmpty()) {
			words = wordRepository.findByIds(group.getWordIds());
		}
		
		return Optional.of(new WordGroupDetail(group, words));
	}
	
	public record WordGroupDetail(WordGroup group, List<Word> words) {
	}
}
