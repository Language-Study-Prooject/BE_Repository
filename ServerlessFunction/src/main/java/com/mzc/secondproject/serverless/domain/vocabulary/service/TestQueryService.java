package com.mzc.secondproject.serverless.domain.vocabulary.service;

import com.mzc.secondproject.serverless.common.dto.PaginatedResult;
import com.mzc.secondproject.serverless.domain.vocabulary.model.TestResult;
import com.mzc.secondproject.serverless.domain.vocabulary.model.Word;
import com.mzc.secondproject.serverless.domain.vocabulary.repository.TestResultRepository;
import com.mzc.secondproject.serverless.domain.vocabulary.repository.WordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

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

    public record TestResultDetail(TestResult testResult, List<Word> incorrectWords) {}
}
