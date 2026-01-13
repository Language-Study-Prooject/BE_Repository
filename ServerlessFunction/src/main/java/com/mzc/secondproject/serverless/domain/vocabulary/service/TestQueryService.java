package com.mzc.secondproject.serverless.domain.vocabulary.service;

import com.mzc.secondproject.serverless.common.dto.PaginatedResult;
import com.mzc.secondproject.serverless.domain.vocabulary.model.TestResult;
import com.mzc.secondproject.serverless.domain.vocabulary.model.Word;
import com.mzc.secondproject.serverless.domain.vocabulary.repository.TestResultRepository;
import com.mzc.secondproject.serverless.domain.vocabulary.repository.WordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Test 조회 전용 서비스 (CQRS Query)
 */
public class TestQueryService {

    private static final Logger logger = LoggerFactory.getLogger(TestQueryService.class);

    private final TestResultRepository testResultRepository;
    private final WordRepository wordRepository;

    public TestQueryService() {
        this.testResultRepository = new TestResultRepository();
        this.wordRepository = new WordRepository();
    }

    public PaginatedResult<TestResult> getTestResults(String userId, int limit, String cursor) {
        return testResultRepository.findByUserIdWithPagination(userId, limit, cursor);
    }

    public Optional<TestResultDetail> getTestResultDetail(String userId, String testId) {
        Optional<TestResult> optResult = testResultRepository.findByUserIdAndTestId(userId, testId);

        if (optResult.isEmpty()) {
            return Optional.empty();
        }

        TestResult testResult = optResult.get();
        List<Word> incorrectWords = List.of();

        if (testResult.getIncorrectWordIds() != null && !testResult.getIncorrectWordIds().isEmpty()) {
            incorrectWords = wordRepository.findByIds(testResult.getIncorrectWordIds());
        }

        return Optional.of(new TestResultDetail(testResult, incorrectWords));
    }

    /**
     * 시험에 나온 단어 조회
     * 최근 테스트 결과들에서 출제된 단어들을 조회
     */
    public TestedWordsResult getTestedWords(String userId, int recentTests, int limit) {
        PaginatedResult<TestResult> results = testResultRepository.findByUserIdWithPagination(userId, recentTests, null);

        Set<String> testedWordIds = new LinkedHashSet<>();
        for (TestResult result : results.items()) {
            if (result.getTestedWordIds() != null) {
                testedWordIds.addAll(result.getTestedWordIds());
            }
        }

        if (testedWordIds.isEmpty()) {
            return new TestedWordsResult(new ArrayList<>(), testedWordIds.size());
        }

        List<String> wordIdList = new ArrayList<>(testedWordIds);
        if (wordIdList.size() > limit) {
            wordIdList = wordIdList.subList(0, limit);
        }

        List<Word> words = wordRepository.findByIds(wordIdList);
        return new TestedWordsResult(words, testedWordIds.size());
    }

    public record TestResultDetail(TestResult testResult, List<Word> incorrectWords) {}

    public record TestedWordsResult(List<Word> words, int totalCount) {}
}
