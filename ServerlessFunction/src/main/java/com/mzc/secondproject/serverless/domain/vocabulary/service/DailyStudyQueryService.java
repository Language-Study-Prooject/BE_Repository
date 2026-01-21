package com.mzc.secondproject.serverless.domain.vocabulary.service;

import com.mzc.secondproject.serverless.domain.vocabulary.model.DailyStudy;
import com.mzc.secondproject.serverless.domain.vocabulary.model.Word;
import com.mzc.secondproject.serverless.domain.vocabulary.repository.DailyStudyRepository;
import com.mzc.secondproject.serverless.domain.vocabulary.repository.WordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.*;

/**
 * DailyStudy 조회 전용 서비스 (CQRS Query)
 */
public class DailyStudyQueryService {
	
	private static final Logger logger = LoggerFactory.getLogger(DailyStudyQueryService.class);
	
	private final DailyStudyRepository dailyStudyRepository;
	private final WordRepository wordRepository;

	/**
	 * 기본 생성자 (Lambda에서 사용)
	 */
	public DailyStudyQueryService() {
		this(new DailyStudyRepository(), new WordRepository());
	}

	/**
	 * 의존성 주입 생성자 (테스트 용이성)
	 */
	public DailyStudyQueryService(DailyStudyRepository dailyStudyRepository, WordRepository wordRepository) {
		this.dailyStudyRepository = dailyStudyRepository;
		this.wordRepository = wordRepository;
	}
	
	public Optional<DailyStudy> getDailyStudy(String userId, String date) {
		return dailyStudyRepository.findByUserIdAndDate(userId, date);
	}
	
	public Optional<DailyStudy> getTodayDailyStudy(String userId) {
		String today = LocalDate.now().toString();
		return dailyStudyRepository.findByUserIdAndDate(userId, today);
	}
	
	public List<Word> getWordDetails(List<String> wordIds) {
		if (wordIds == null || wordIds.isEmpty()) {
			return new ArrayList<>();
		}
		return wordRepository.findByIds(wordIds);
	}
	
	public Map<String, Object> calculateProgress(DailyStudy dailyStudy) {
		Map<String, Object> progress = new HashMap<>();
		int total = dailyStudy.getTotalWords();
		int learned = dailyStudy.getLearnedCount();
		
		progress.put("total", total);
		progress.put("learned", learned);
		progress.put("remaining", total - learned);
		progress.put("percentage", total > 0 ? (learned * 100.0 / total) : 0);
		progress.put("isCompleted", dailyStudy.getIsCompleted());
		
		return progress;
	}
}
