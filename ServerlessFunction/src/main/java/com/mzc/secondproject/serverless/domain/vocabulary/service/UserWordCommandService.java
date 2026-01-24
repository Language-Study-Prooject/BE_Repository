package com.mzc.secondproject.serverless.domain.vocabulary.service;

import com.mzc.secondproject.serverless.common.config.StudyConfig;
import com.mzc.secondproject.serverless.common.constants.DynamoDbKey;
import com.mzc.secondproject.serverless.common.enums.Difficulty;
import com.mzc.secondproject.serverless.domain.vocabulary.constants.VocabKey;
import com.mzc.secondproject.serverless.domain.vocabulary.enums.WordStatus;
import com.mzc.secondproject.serverless.domain.vocabulary.model.UserWord;
import com.mzc.secondproject.serverless.domain.vocabulary.repository.UserWordRepository;
import com.mzc.secondproject.serverless.domain.vocabulary.state.SpacedRepetitionContext;
import com.mzc.secondproject.serverless.domain.vocabulary.state.WordState;
import com.mzc.secondproject.serverless.domain.vocabulary.state.WordStateFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

/**
 * UserWord 변경 전용 서비스 (CQRS Command)
 */
public class UserWordCommandService {
	
	private static final Logger logger = LoggerFactory.getLogger(UserWordCommandService.class);
	
	private final UserWordRepository userWordRepository;
	
	/**
	 * 기본 생성자 (Lambda에서 사용)
	 */
	public UserWordCommandService() {
		this(new UserWordRepository());
	}
	
	/**
	 * 의존성 주입 생성자 (테스트 용이성)
	 */
	public UserWordCommandService(UserWordRepository userWordRepository) {
		this.userWordRepository = userWordRepository;
	}
	
	public UserWord updateUserWord(String userId, String wordId, boolean isCorrect) {
		Optional<UserWord> optUserWord = userWordRepository.findByUserIdAndWordId(userId, wordId);
		UserWord userWord;
		String now = Instant.now().toString();
		
		if (optUserWord.isEmpty()) {
			userWord = UserWord.builder()
					.pk(DynamoDbKey.userPk(userId))
					.sk(VocabKey.wordSk(wordId))
					.gsi1pk(VocabKey.userReviewPk(userId))
					.gsi2pk(VocabKey.userStatusPk(userId))
					.userId(userId)
					.wordId(wordId)
					.status(WordStatus.NEW.name())
					.interval(StudyConfig.INITIAL_INTERVAL_DAYS)
					.easeFactor(StudyConfig.DEFAULT_EASE_FACTOR)
					.repetitions(StudyConfig.INITIAL_REPETITIONS)
					.correctCount(StudyConfig.INITIAL_CORRECT_COUNT)
					.incorrectCount(StudyConfig.INITIAL_INCORRECT_COUNT)
					.createdAt(now)
					.build();
		} else {
			userWord = optUserWord.get();
		}
		
		applySpacedRepetition(userWord, isCorrect);
		userWord.setUpdatedAt(now);
		userWord.setLastReviewedAt(now);
		
		userWord.setGsi1sk(VocabKey.dateSk(userWord.getNextReviewAt()));
		userWord.setGsi2sk(VocabKey.statusSk(userWord.getStatus()));
		
		userWordRepository.save(userWord);
		
		logger.info("Updated user word: userId={}, wordId={}, isCorrect={}", userId, wordId, isCorrect);
		return userWord;
	}
	
	public UserWord updateUserWordTag(String userId, String wordId, Boolean bookmarked,
	                                  Boolean favorite, String difficulty) {
		Optional<UserWord> optUserWord = userWordRepository.findByUserIdAndWordId(userId, wordId);
		UserWord userWord;
		String now = Instant.now().toString();
		
		if (optUserWord.isEmpty()) {
			userWord = UserWord.builder()
					.pk(DynamoDbKey.userPk(userId))
					.sk(VocabKey.wordSk(wordId))
					.gsi1pk(VocabKey.userReviewPk(userId))
					.gsi2pk(VocabKey.userStatusPk(userId))
					.gsi2sk(VocabKey.statusSk(WordStatus.NEW.name()))
					.userId(userId)
					.wordId(wordId)
					.status(WordStatus.NEW.name())
					.interval(StudyConfig.INITIAL_INTERVAL_DAYS)
					.easeFactor(StudyConfig.DEFAULT_EASE_FACTOR)
					.repetitions(StudyConfig.INITIAL_REPETITIONS)
					.correctCount(StudyConfig.INITIAL_CORRECT_COUNT)
					.incorrectCount(StudyConfig.INITIAL_INCORRECT_COUNT)
					.bookmarked(false)
					.favorite(false)
					.createdAt(now)
					.build();
		} else {
			userWord = optUserWord.get();
		}
		
		if (bookmarked != null) {
			userWord.setBookmarked(bookmarked);
			if (bookmarked) {
				userWord.setGsi3pk(VocabKey.userBookmarkedPk(userId));
				userWord.setGsi3sk(VocabKey.wordSk(wordId));
			} else {
				userWord.setGsi3pk(null);
				userWord.setGsi3sk(null);
			}
		}
		if (favorite != null) {
			userWord.setFavorite(favorite);
		}
		if (difficulty != null) {
			if (!Difficulty.isValid(difficulty)) {
				throw new IllegalArgumentException("difficulty must be EASY, NORMAL, or HARD");
			}
			userWord.setDifficulty(difficulty);
		}
		
		userWord.setUpdatedAt(now);
		userWordRepository.save(userWord);
		
		logger.info("Updated user word tag: userId={}, wordId={}", userId, wordId);
		return userWord;
	}
	
	/**
	 * 단어 상태 수동 변경
	 */
	public UserWord updateWordStatus(String userId, String wordId, String newStatus) {
		if (!WordStatus.isValid(newStatus)) {
			throw new IllegalArgumentException("Invalid status: " + newStatus);
		}
		
		Optional<UserWord> optUserWord = userWordRepository.findByUserIdAndWordId(userId, wordId);
		UserWord userWord;
		String now = Instant.now().toString();
		
		if (optUserWord.isEmpty()) {
			userWord = UserWord.builder()
					.pk(DynamoDbKey.userPk(userId))
					.sk(VocabKey.wordSk(wordId))
					.gsi1pk(VocabKey.userReviewPk(userId))
					.gsi2pk(VocabKey.userStatusPk(userId))
					.userId(userId)
					.wordId(wordId)
					.interval(StudyConfig.INITIAL_INTERVAL_DAYS)
					.easeFactor(StudyConfig.DEFAULT_EASE_FACTOR)
					.repetitions(StudyConfig.INITIAL_REPETITIONS)
					.correctCount(StudyConfig.INITIAL_CORRECT_COUNT)
					.incorrectCount(StudyConfig.INITIAL_INCORRECT_COUNT)
					.createdAt(now)
					.build();
		} else {
			userWord = optUserWord.get();
		}
		
		userWord.setStatus(newStatus.toUpperCase());
		userWord.setGsi2sk(VocabKey.statusSk(newStatus.toUpperCase()));
		userWord.setUpdatedAt(now);
		
		userWordRepository.save(userWord);
		
		logger.info("Updated word status: userId={}, wordId={}, status={}", userId, wordId, newStatus);
		return userWord;
	}
	
	private void applySpacedRepetition(UserWord userWord, boolean isCorrect) {
		SpacedRepetitionContext context = new SpacedRepetitionContext(
				userWord.getRepetitions(),
				userWord.getInterval(),
				userWord.getEaseFactor(),
				userWord.getCorrectCount(),
				userWord.getIncorrectCount()
		);
		
		WordState currentState = WordStateFactory.fromString(userWord.getStatus());
		WordState nextState = isCorrect
				? currentState.onCorrectAnswer(context)
				: currentState.onWrongAnswer(context);
		
		userWord.setRepetitions(context.getRepetitions());
		userWord.setInterval(context.getInterval());
		userWord.setEaseFactor(context.getEaseFactor());
		userWord.setCorrectCount(context.getCorrectCount());
		userWord.setIncorrectCount(context.getIncorrectCount());
		userWord.setStatus(nextState.getStateName());
		
		LocalDate nextReview = LocalDate.now().plusDays(context.getInterval());
		userWord.setNextReviewAt(nextReview.toString());
	}
}
