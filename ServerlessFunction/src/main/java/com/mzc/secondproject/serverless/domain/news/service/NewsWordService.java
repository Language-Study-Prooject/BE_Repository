package com.mzc.secondproject.serverless.domain.news.service;

import com.mzc.secondproject.serverless.domain.news.constants.NewsKey;
import com.mzc.secondproject.serverless.domain.news.model.NewsArticle;
import com.mzc.secondproject.serverless.domain.news.model.NewsWordCollect;
import com.mzc.secondproject.serverless.domain.news.repository.NewsArticleRepository;
import com.mzc.secondproject.serverless.domain.news.repository.NewsWordRepository;
import com.mzc.secondproject.serverless.domain.vocabulary.model.Word;
import com.mzc.secondproject.serverless.domain.vocabulary.repository.WordRepository;
import com.mzc.secondproject.serverless.domain.vocabulary.service.UserWordCommandService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 뉴스 단어 수집 서비스
 */
public class NewsWordService {

	private static final Logger logger = LoggerFactory.getLogger(NewsWordService.class);

	private final NewsWordRepository newsWordRepository;
	private final NewsArticleRepository articleRepository;
	private final WordRepository wordRepository;
	private final UserWordCommandService userWordCommandService;

	public NewsWordService() {
		this.newsWordRepository = new NewsWordRepository();
		this.articleRepository = new NewsArticleRepository();
		this.wordRepository = new WordRepository();
		this.userWordCommandService = new UserWordCommandService();
	}

	public NewsWordService(NewsWordRepository newsWordRepository,
						   NewsArticleRepository articleRepository,
						   WordRepository wordRepository,
						   UserWordCommandService userWordCommandService) {
		this.newsWordRepository = newsWordRepository;
		this.articleRepository = articleRepository;
		this.wordRepository = wordRepository;
		this.userWordCommandService = userWordCommandService;
	}

	/**
	 * 단어 수집
	 */
	public NewsWordCollect collectWord(String userId, String articleId, String word, String context) {
		// 이미 수집했는지 확인
		if (newsWordRepository.hasCollected(userId, word, articleId)) {
			logger.warn("이미 수집한 단어: userId={}, word={}", userId, word);
			return newsWordRepository.findByUserWordArticle(userId, word, articleId).orElse(null);
		}

		// 기사 조회
		Optional<NewsArticle> articleOpt = articleRepository.findById(articleId);
		String articleTitle = articleOpt.map(NewsArticle::getTitle).orElse("");

		// 단어 정보 조회 (Word 테이블에서)
		String wordId = word.toLowerCase().trim();
		Optional<Word> wordOpt = wordRepository.findById(wordId);
		String meaning = wordOpt.map(Word::getKorean).orElse("");
		String pronunciation = "";

		String now = Instant.now().toString();

		NewsWordCollect wordCollect = NewsWordCollect.builder()
				.pk(NewsKey.userNewsPk(userId))
				.sk(NewsKey.wordSk(word, articleId))
				.gsi1pk(NewsKey.userNewsWordsPk(userId))
				.gsi1sk(now)
				.userId(userId)
				.word(word)
				.meaning(meaning)
				.pronunciation(pronunciation)
				.context(context)
				.articleId(articleId)
				.articleTitle(articleTitle)
				.collectedAt(now)
				.syncedToVocab(false)
				.build();

		newsWordRepository.save(wordCollect);
		logger.info("단어 수집 완료: userId={}, word={}, articleId={}", userId, word, articleId);

		return wordCollect;
	}

	/**
	 * 수집한 단어 삭제
	 */
	public void deleteWord(String userId, String word, String articleId) {
		newsWordRepository.delete(userId, word, articleId);
		logger.info("단어 삭제: userId={}, word={}", userId, word);
	}

	/**
	 * 사용자 수집 단어 목록 조회
	 */
	public List<NewsWordCollect> getUserWords(String userId, int limit) {
		return newsWordRepository.getUserWords(userId, limit);
	}

	/**
	 * 사용자 수집 단어 수 조회
	 */
	public int countUserWords(String userId) {
		return newsWordRepository.countUserWords(userId);
	}

	/**
	 * 단어 상세 정보 조회
	 */
	public Optional<WordDetail> getWordDetail(String word) {
		String wordId = word.toLowerCase().trim();
		Optional<Word> wordOpt = wordRepository.findById(wordId);

		if (wordOpt.isEmpty()) {
			return Optional.empty();
		}

		Word w = wordOpt.get();
		return Optional.of(WordDetail.builder()
				.word(w.getEnglish())
				.meaning(w.getKorean())
				.pronunciation("")
				.example(w.getExample())
				.level(w.getLevel())
				.build());
	}

	/**
	 * Vocabulary 도메인으로 단어 연동
	 */
	public boolean syncToVocabulary(String userId, String word, String articleId) {
		Optional<NewsWordCollect> wordOpt = newsWordRepository.findByUserWordArticle(userId, word, articleId);
		if (wordOpt.isEmpty()) {
			logger.warn("수집한 단어를 찾을 수 없음: userId={}, word={}", userId, word);
			return false;
		}

		NewsWordCollect wordCollect = wordOpt.get();

		// 이미 연동됐는지 확인
		if (Boolean.TRUE.equals(wordCollect.getSyncedToVocab())) {
			logger.info("이미 Vocabulary에 연동됨: userId={}, word={}", userId, word);
			return true;
		}

		// Word 테이블에서 단어 조회
		String wordId = word.toLowerCase().trim();
		Optional<Word> vocabWord = wordRepository.findById(wordId);

		if (vocabWord.isEmpty()) {
			logger.warn("Vocabulary에 없는 단어: {}", word);
			return false;
		}

		// UserWord 생성 (NEW 상태로)
		userWordCommandService.updateWordStatus(userId, wordId, "NEW");

		// 연동 상태 업데이트
		newsWordRepository.updateSyncStatus(userId, word, articleId, wordId);

		logger.info("Vocabulary 연동 완료: userId={}, word={}", userId, word);
		return true;
	}

	/**
	 * 사용자 단어 수집 통계
	 */
	public Map<String, Object> getUserWordStats(String userId) {
		int totalWords = newsWordRepository.countUserWords(userId);
		List<NewsWordCollect> recentWords = newsWordRepository.getUserWords(userId, 5);
		long syncedCount = recentWords.stream()
				.filter(w -> Boolean.TRUE.equals(w.getSyncedToVocab()))
				.count();

		return Map.of(
				"totalCollected", totalWords,
				"recentWords", recentWords,
				"syncedToVocab", syncedCount
		);
	}

	/**
	 * 단어 상세 정보
	 */
	@lombok.Data
	@lombok.Builder
	@lombok.NoArgsConstructor
	@lombok.AllArgsConstructor
	public static class WordDetail {
		private String word;
		private String meaning;
		private String pronunciation;
		private String example;
		private String level;
	}
}
