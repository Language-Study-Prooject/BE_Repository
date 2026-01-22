package com.mzc.secondproject.serverless.domain.vocabulary.service;

import com.mzc.secondproject.serverless.common.config.AwsClients;
import com.mzc.secondproject.serverless.common.config.EnvConfig;
import com.mzc.secondproject.serverless.common.dto.PaginatedResult;
import com.mzc.secondproject.serverless.common.util.ResponseGenerator;
import com.mzc.secondproject.serverless.domain.vocabulary.dto.request.SubmitTestRequest;
import com.mzc.secondproject.serverless.domain.vocabulary.exception.VocabularyException;
import com.mzc.secondproject.serverless.domain.vocabulary.model.DailyStudy;
import com.mzc.secondproject.serverless.domain.vocabulary.model.TestResult;
import com.mzc.secondproject.serverless.domain.vocabulary.model.Word;
import com.mzc.secondproject.serverless.domain.vocabulary.repository.DailyStudyRepository;
import com.mzc.secondproject.serverless.domain.vocabulary.repository.TestResultRepository;
import com.mzc.secondproject.serverless.domain.vocabulary.repository.WordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Test 변경 전용 서비스 (CQRS Command)
 */
public class TestCommandService {
	
	private static final Logger logger = LoggerFactory.getLogger(TestCommandService.class);
	private static final String TEST_RESULT_TOPIC_ARN = EnvConfig.getRequired("TEST_RESULT_TOPIC_ARN");
	
	private final TestResultRepository testResultRepository;
	private final DailyStudyRepository dailyStudyRepository;
	private final WordRepository wordRepository;
	private final UserWordCommandService userWordCommandService;

	/**
	 * 기본 생성자 (Lambda에서 사용)
	 */
	public TestCommandService() {
		this(new TestResultRepository(), new DailyStudyRepository(),
				new WordRepository(), new UserWordCommandService());
	}

	/**
	 * 의존성 주입 생성자 (테스트 용이성)
	 */
	public TestCommandService(TestResultRepository testResultRepository,
	                          DailyStudyRepository dailyStudyRepository,
	                          WordRepository wordRepository,
	                          UserWordCommandService userWordCommandService) {
		this.testResultRepository = testResultRepository;
		this.dailyStudyRepository = dailyStudyRepository;
		this.wordRepository = wordRepository;
		this.userWordCommandService = userWordCommandService;
	}
	
	public StartTestResult startTest(String userId, String testType) {
		String today = LocalDate.now().toString();
		
		Optional<DailyStudy> optDailyStudy = dailyStudyRepository.findByUserIdAndDate(userId, today);
		if (optDailyStudy.isEmpty()) {
			throw VocabularyException.dailyStudyNotFound(userId, today);
		}
		
		DailyStudy dailyStudy = optDailyStudy.get();
		List<String> allWordIds = new ArrayList<>();
		if (dailyStudy.getNewWordIds() != null) allWordIds.addAll(dailyStudy.getNewWordIds());
		if (dailyStudy.getReviewWordIds() != null) allWordIds.addAll(dailyStudy.getReviewWordIds());
		
		if (allWordIds.isEmpty()) {
			throw VocabularyException.noWordsToTest();
		}
		
		List<Word> words = wordRepository.findByIds(allWordIds);
		
		Map<String, List<Word>> wordsByLevel = words.stream()
				.collect(Collectors.groupingBy(Word::getLevel));
		
		Map<String, List<String>> distractorsByLevel = new HashMap<>();
		for (String level : wordsByLevel.keySet()) {
			List<String> distractors = getDistractorsForLevel(level, allWordIds);
			distractorsByLevel.put(level, distractors);
		}
		
		Random random = new Random();
		List<Map<String, Object>> questions = new ArrayList<>();
		for (Word word : words) {
			Map<String, Object> question = new HashMap<>();
			question.put("wordId", word.getWordId());
			question.put("english", word.getEnglish());
			question.put("example", word.getExample());
			
			List<String> options = generateOptions(word, wordsByLevel, distractorsByLevel, random);
			question.put("options", options);
			
			questions.add(question);
		}
		
		String testId = UUID.randomUUID().toString();
		String now = Instant.now().toString();
		
		logger.info("Started test: userId={}, testId={}, questions={}", userId, testId, questions.size());
		
		return new StartTestResult(testId, testType, questions, questions.size(), now);
	}
	
	public SubmitTestResult submitTest(String userId, String testId, String testType,
	                                   List<SubmitTestRequest.TestAnswer> answers, String startedAt) {
		// 1. 답안 채점
		GradingResult gradingResult = gradeAnswers(answers);
		
		// 2. 테스트 결과 저장
		TestResult testResult = saveTestResult(userId, testId, testType, gradingResult, startedAt);
		
		// 3. 오답 단어 자동 북마크
		bookmarkIncorrectWords(userId, gradingResult.incorrectWordIds());
		
		// 4. SNS 알림 발행
		publishTestResultToSns(userId, gradingResult.results());
		
		logger.info("Test submitted: userId={}, testId={}, successRate={}%",
				userId, testId, gradingResult.successRate());
		
		return new SubmitTestResult(
				testId, testType, gradingResult.totalQuestions(),
				gradingResult.correctCount(), gradingResult.incorrectCount(),
				gradingResult.successRate(), gradingResult.results()
		);
	}
	
	private GradingResult gradeAnswers(List<SubmitTestRequest.TestAnswer> answers) {
		List<String> wordIds = answers.stream()
				.map(SubmitTestRequest.TestAnswer::getWordId)
				.collect(Collectors.toList());
		
		Map<String, Word> wordMap = wordRepository.findByIds(wordIds).stream()
				.collect(Collectors.toMap(Word::getWordId, w -> w));
		
		int correctCount = 0;
		int incorrectCount = 0;
		List<String> incorrectWordIds = new ArrayList<>();
		List<Map<String, Object>> results = new ArrayList<>();
		
		for (SubmitTestRequest.TestAnswer answer : answers) {
			String wordId = answer.getWordId();
			String userAnswer = answer.getAnswer();
			Word word = wordMap.get(wordId);
			
			if (word == null) continue;
			
			boolean isCorrect = isAnswerCorrect(userAnswer, word.getKorean());
			results.add(buildResultItem(word, userAnswer, isCorrect));
			
			if (isCorrect) {
				correctCount++;
			} else {
				incorrectCount++;
				incorrectWordIds.add(wordId);
			}
		}
		
		int totalQuestions = answers.size();
		double successRate = totalQuestions > 0 ? (correctCount * 100.0 / totalQuestions) : 0;
		
		return new GradingResult(wordIds, correctCount, incorrectCount, incorrectWordIds,
				totalQuestions, successRate, results);
	}
	
	private boolean isAnswerCorrect(String userAnswer, String correctAnswer) {
		return userAnswer != null
				&& !userAnswer.isBlank()
				&& correctAnswer.trim().equalsIgnoreCase(userAnswer.trim());
	}
	
	private Map<String, Object> buildResultItem(Word word, String userAnswer, boolean isCorrect) {
		Map<String, Object> resultItem = new HashMap<>();
		resultItem.put("wordId", word.getWordId());
		resultItem.put("english", word.getEnglish());
		resultItem.put("correctAnswer", word.getKorean());
		resultItem.put("userAnswer", userAnswer != null ? userAnswer : "");
		resultItem.put("isCorrect", isCorrect);
		return resultItem;
	}
	
	private TestResult saveTestResult(String userId, String testId, String testType,
	                                  GradingResult gradingResult, String startedAt) {
		String now = Instant.now().toString();
		String today = LocalDate.now().toString();
		
		TestResult testResult = TestResult.builder()
				.pk("TEST#" + userId)
				.sk("RESULT#" + now)
				.gsi1pk("TEST#ALL")
				.gsi1sk("DATE#" + today)
				.testId(testId)
				.userId(userId)
				.testType(testType)
				.totalQuestions(gradingResult.totalQuestions())
				.correctAnswers(gradingResult.correctCount())
				.incorrectAnswers(gradingResult.incorrectCount())
				.successRate(gradingResult.successRate())
				.testedWordIds(gradingResult.wordIds())
				.incorrectWordIds(gradingResult.incorrectWordIds())
				.startedAt(startedAt)
				.completedAt(now)
				.build();
		
		testResultRepository.save(testResult);
		return testResult;
	}
	
	private void bookmarkIncorrectWords(String userId, List<String> incorrectWordIds) {
		if (incorrectWordIds == null || incorrectWordIds.isEmpty()) {
			return;
		}
		
		int bookmarkedCount = 0;
		for (String wordId : incorrectWordIds) {
			try {
				userWordCommandService.updateUserWordTag(userId, wordId, true, null, null);
				bookmarkedCount++;
			} catch (Exception e) {
				logger.warn("Failed to bookmark word: userId={}, wordId={}", userId, wordId, e);
			}
		}
		logger.info("Auto-bookmarked {} incorrect words for user: {}", bookmarkedCount, userId);
	}
	
	private List<String> getDistractorsForLevel(String level, List<String> excludeWordIds) {
		Set<String> excludeSet = new HashSet<>(excludeWordIds);
		PaginatedResult<Word> wordPage = wordRepository.findByLevelWithPagination(level, 50, null);
		return wordPage.items().stream()
				.filter(w -> !excludeSet.contains(w.getWordId()))
				.map(Word::getKorean)
				.collect(Collectors.toList());
	}
	
	private List<String> generateOptions(Word correctWord, Map<String, List<Word>> wordsByLevel,
	                                     Map<String, List<String>> distractorsByLevel, Random random) {
		List<String> options = new ArrayList<>();
		String correctAnswer = correctWord.getKorean();
		options.add(correctAnswer);
		
		String level = correctWord.getLevel();
		
		List<String> sameLevelOptions = wordsByLevel.getOrDefault(level, new ArrayList<>()).stream()
				.filter(w -> !w.getWordId().equals(correctWord.getWordId()))
				.map(Word::getKorean)
				.collect(Collectors.toList());
		
		List<String> additionalDistractors = distractorsByLevel.getOrDefault(level, new ArrayList<>());
		
		List<String> allDistractors = new ArrayList<>();
		allDistractors.addAll(sameLevelOptions);
		allDistractors.addAll(additionalDistractors);
		
		allDistractors = allDistractors.stream()
				.filter(d -> !d.equals(correctAnswer))
				.distinct()
				.collect(Collectors.toList());
		
		Collections.shuffle(allDistractors, random);
		int distractorCount = Math.min(3, allDistractors.size());
		for (int i = 0; i < distractorCount; i++) {
			options.add(allDistractors.get(i));
		}
		
		Collections.shuffle(options, random);
		return options;
	}
	
	private void publishTestResultToSns(String userId, List<Map<String, Object>> results) {
		if (TEST_RESULT_TOPIC_ARN == null || TEST_RESULT_TOPIC_ARN.isEmpty()) {
			logger.warn("TEST_RESULT_TOPIC_ARN is not configured, skipping SNS publish");
			return;
		}
		
		try {
			Map<String, Object> message = new HashMap<>();
			message.put("userId", userId);
			message.put("results", results);
			
			String messageJson = ResponseGenerator.gson().toJson(message);
			
			PublishRequest publishRequest = PublishRequest.builder()
					.topicArn(TEST_RESULT_TOPIC_ARN)
					.message(messageJson)
					.build();
			
			AwsClients.sns().publish(publishRequest);
			logger.info("Published test result to SNS for user: {}", userId);
		} catch (Exception e) {
			logger.error("Failed to publish test result to SNS for user: {}", userId, e);
		}
	}
	
	private record GradingResult(
			List<String> wordIds,
			int correctCount,
			int incorrectCount,
			List<String> incorrectWordIds,
			int totalQuestions,
			double successRate,
			List<Map<String, Object>> results
	) {
	}
	
	public record StartTestResult(String testId, String testType, List<Map<String, Object>> questions,
	                              int totalQuestions, String startedAt) {
	}
	
	public record SubmitTestResult(String testId, String testType, int totalQuestions,
	                               int correctCount, int incorrectCount, double successRate,
	                               List<Map<String, Object>> results) {
	}
}
