package com.mzc.secondproject.serverless.domain.vocabulary.service;

import com.mzc.secondproject.serverless.common.dto.PaginatedResult;
import com.mzc.secondproject.serverless.common.util.AwsClients;
import com.mzc.secondproject.serverless.common.util.ResponseUtil;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

public class TestService {

    private static final Logger logger = LoggerFactory.getLogger(TestService.class);
    private static final String TEST_RESULT_TOPIC_ARN = System.getenv("TEST_RESULT_TOPIC_ARN");

    private final TestResultRepository testResultRepository;
    private final DailyStudyRepository dailyStudyRepository;
    private final WordRepository wordRepository;

    public TestService() {
        this.testResultRepository = new TestResultRepository();
        this.dailyStudyRepository = new DailyStudyRepository();
        this.wordRepository = new WordRepository();
    }

    public StartTestResult startTest(String userId, String testType) {
        String today = LocalDate.now().toString();

        Optional<DailyStudy> optDailyStudy = dailyStudyRepository.findByUserIdAndDate(userId, today);
        if (optDailyStudy.isEmpty()) {
            throw new IllegalStateException("No daily study found for today");
        }

        DailyStudy dailyStudy = optDailyStudy.get();
        List<String> allWordIds = new ArrayList<>();
        if (dailyStudy.getNewWordIds() != null) allWordIds.addAll(dailyStudy.getNewWordIds());
        if (dailyStudy.getReviewWordIds() != null) allWordIds.addAll(dailyStudy.getReviewWordIds());

        if (allWordIds.isEmpty()) {
            throw new IllegalStateException("No words to test");
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
                                        List<Map<String, Object>> answers, String startedAt) {
        String now = Instant.now().toString();
        String today = LocalDate.now().toString();

        int correctCount = 0;
        int incorrectCount = 0;
        List<String> incorrectWordIds = new ArrayList<>();
        List<Map<String, Object>> results = new ArrayList<>();

        List<String> wordIds = answers.stream()
                .map(a -> (String) a.get("wordId"))
                .collect(Collectors.toList());
        List<Word> words = wordRepository.findByIds(wordIds);

        Map<String, Word> wordMap = words.stream()
                .collect(Collectors.toMap(Word::getWordId, w -> w));

        for (Map<String, Object> answer : answers) {
            String wordId = (String) answer.get("wordId");
            String userAnswer = (String) answer.get("answer");

            Word word = wordMap.get(wordId);
            if (word != null) {
                boolean isCorrect = word.getKorean().trim().equalsIgnoreCase(userAnswer.trim());

                Map<String, Object> resultItem = new HashMap<>();
                resultItem.put("wordId", wordId);
                resultItem.put("english", word.getEnglish());
                resultItem.put("correctAnswer", word.getKorean());
                resultItem.put("userAnswer", userAnswer);
                resultItem.put("isCorrect", isCorrect);
                results.add(resultItem);

                if (isCorrect) {
                    correctCount++;
                } else {
                    incorrectCount++;
                    incorrectWordIds.add(wordId);
                }
            }
        }

        int totalQuestions = answers.size();
        double successRate = totalQuestions > 0 ? (correctCount * 100.0 / totalQuestions) : 0;

        TestResult testResult = TestResult.builder()
                .pk("TEST#" + userId)
                .sk("RESULT#" + now)
                .gsi1pk("TEST#ALL")
                .gsi1sk("DATE#" + today)
                .testId(testId)
                .userId(userId)
                .testType(testType)
                .totalQuestions(totalQuestions)
                .correctAnswers(correctCount)
                .incorrectAnswers(incorrectCount)
                .successRate(successRate)
                .incorrectWordIds(incorrectWordIds)
                .startedAt(startedAt)
                .completedAt(now)
                .build();

        testResultRepository.save(testResult);

        publishTestResultToSns(userId, results);

        logger.info("Test submitted: userId={}, testId={}, successRate={}%", userId, testId, successRate);

        return new SubmitTestResult(testId, testType, totalQuestions, correctCount, incorrectCount, successRate, results);
    }

    public PaginatedResult<TestResult> getTestResults(String userId, int limit, String cursor) {
        return testResultRepository.findByUserIdWithPagination(userId, limit, cursor);
    }

    private List<String> getDistractorsForLevel(String level, List<String> excludeWordIds) {
        PaginatedResult<Word> wordPage = wordRepository.findByLevelWithPagination(level, 50, null);
        return wordPage.getItems().stream()
                .filter(w -> !excludeWordIds.contains(w.getWordId()))
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

            String messageJson = ResponseUtil.gson().toJson(message);

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

    public record StartTestResult(String testId, String testType, List<Map<String, Object>> questions,
                                   int totalQuestions, String startedAt) {}

    public record SubmitTestResult(String testId, String testType, int totalQuestions,
                                    int correctCount, int incorrectCount, double successRate,
                                    List<Map<String, Object>> results) {}
}
