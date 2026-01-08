package com.mzc.secondproject.serverless.domain.vocabulary.service;

import com.mzc.secondproject.serverless.common.dto.PaginatedResult;
import com.mzc.secondproject.serverless.domain.vocabulary.model.TestResult;
import com.mzc.secondproject.serverless.domain.vocabulary.repository.TestResultRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test 조회 전용 서비스 (CQRS Query)
 */
public class TestQueryService {

    private static final Logger logger = LoggerFactory.getLogger(TestQueryService.class);

    private final TestResultRepository testResultRepository;

    public TestQueryService() {
        this.testResultRepository = new TestResultRepository();
    }

    public PaginatedResult<TestResult> getTestResults(String userId, int limit, String cursor) {
        return testResultRepository.findByUserIdWithPagination(userId, limit, cursor);
    }
}
