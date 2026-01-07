package com.mzc.secondproject.serverless.domain.vocabulary.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.mzc.secondproject.serverless.common.dto.ApiResponse;
import static com.mzc.secondproject.serverless.common.util.ResponseUtil.createResponse;
import com.mzc.secondproject.serverless.domain.vocabulary.model.DailyStudy;
import com.mzc.secondproject.serverless.domain.vocabulary.model.UserWord;
import com.mzc.secondproject.serverless.domain.vocabulary.model.Word;
import com.mzc.secondproject.serverless.domain.vocabulary.repository.DailyStudyRepository;
import com.mzc.secondproject.serverless.domain.vocabulary.repository.UserWordRepository;
import com.mzc.secondproject.serverless.domain.vocabulary.repository.WordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class DailyStudyHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final Logger logger = LoggerFactory.getLogger(DailyStudyHandler.class);

    private static final int NEW_WORDS_COUNT = 50;
    private static final int REVIEW_WORDS_COUNT = 5;

    private final DailyStudyRepository dailyStudyRepository;
    private final UserWordRepository userWordRepository;
    private final WordRepository wordRepository;

    public DailyStudyHandler() {
        this.dailyStudyRepository = new DailyStudyRepository();
        this.userWordRepository = new UserWordRepository();
        this.wordRepository = new WordRepository();
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        String httpMethod = request.getHttpMethod();
        String path = request.getPath();

        logger.info("Received request: {} {}", httpMethod, path);

        try {
            // GET /vocab/daily/{userId} - 오늘의 학습 단어
            if ("GET".equals(httpMethod) && !path.contains("/learned")) {
                return getDailyWords(request);
            }

            // POST /vocab/daily/{userId}/words/{wordId}/learned - 학습 완료
            if ("POST".equals(httpMethod) && path.endsWith("/learned")) {
                return markWordLearned(request);
            }

            return createResponse(404, ApiResponse.error("Not found"));

        } catch (Exception e) {
            logger.error("Error handling request", e);
            return createResponse(500, ApiResponse.error("Internal server error: " + e.getMessage()));
        }
    }

    private APIGatewayProxyResponseEvent getDailyWords(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        Map<String, String> queryParams = request.getQueryStringParameters();
        String userId = pathParams != null ? pathParams.get("userId") : null;

        if (userId == null) {
            return createResponse(400, ApiResponse.error("userId is required"));
        }

        // 레벨 파라미터 (첫 생성 시 필수)
        String level = queryParams != null ? queryParams.get("level") : null;

        String today = LocalDate.now().toString();

        // 오늘의 학습 데이터 조회
        Optional<DailyStudy> optDailyStudy = dailyStudyRepository.findByUserIdAndDate(userId, today);

        DailyStudy dailyStudy;
        if (optDailyStudy.isPresent()) {
            dailyStudy = optDailyStudy.get();
        } else {
            // 첫 생성 시 레벨 필수
            if (level == null || level.isEmpty()) {
                return createResponse(400, ApiResponse.error("level is required for first daily study (BEGINNER, INTERMEDIATE, ADVANCED)"));
            }
            // 레벨 유효성 검사
            if (!level.equals("BEGINNER") && !level.equals("INTERMEDIATE") && !level.equals("ADVANCED")) {
                return createResponse(400, ApiResponse.error("Invalid level. Must be BEGINNER, INTERMEDIATE, or ADVANCED"));
            }
            // 새로운 일일 학습 생성
            dailyStudy = createDailyStudy(userId, today, level);
        }

        // 단어 상세 정보 조회
        List<Word> newWords = getWordDetails(dailyStudy.getNewWordIds());
        List<Word> reviewWords = getWordDetails(dailyStudy.getReviewWordIds());

        Map<String, Object> result = new HashMap<>();
        result.put("dailyStudy", dailyStudy);
        result.put("newWords", newWords);
        result.put("reviewWords", reviewWords);
        result.put("progress", calculateProgress(dailyStudy));

        return createResponse(200, ApiResponse.success("Daily words retrieved", result));
    }

    private DailyStudy createDailyStudy(String userId, String date, String level) {
        String now = Instant.now().toString();

        // 복습 대상 단어 조회 (5개)
        UserWordRepository.UserWordPage reviewPage = userWordRepository.findReviewDueWords(userId, date, REVIEW_WORDS_COUNT, null);
        List<String> reviewWordIds = reviewPage.getUserWords().stream()
                .map(UserWord::getWordId)
                .collect(Collectors.toList());

        // 신규 단어 조회 (50개) - 해당 레벨에서 아직 학습하지 않은 단어
        List<String> newWordIds = getNewWordsForUser(userId, level, NEW_WORDS_COUNT);

        DailyStudy dailyStudy = DailyStudy.builder()
                .pk("DAILY#" + userId)
                .sk("DATE#" + date)
                .gsi1pk("DAILY#ALL")
                .gsi1sk("DATE#" + date)
                .userId(userId)
                .date(date)
                .newWordIds(newWordIds)
                .reviewWordIds(reviewWordIds)
                .learnedWordIds(new ArrayList<>())
                .totalWords(newWordIds.size() + reviewWordIds.size())
                .learnedCount(0)
                .isCompleted(false)
                .createdAt(now)
                .updatedAt(now)
                .build();

        dailyStudyRepository.save(dailyStudy);
        logger.info("Created daily study for user: {}, date: {}", userId, date);

        return dailyStudy;
    }

    private List<String> getNewWordsForUser(String userId, String level, int count) {
        // 사용자가 학습한 단어 목록
        UserWordRepository.UserWordPage userWordPage = userWordRepository.findByUserIdWithPagination(userId, 1000, null);
        List<String> learnedWordIds = userWordPage.getUserWords().stream()
                .map(UserWord::getWordId)
                .collect(Collectors.toList());

        // 해당 레벨에서 학습하지 않은 단어 선택
        List<String> newWordIds = new ArrayList<>();
        String lastEvaluatedKey = null;

        // 페이지네이션으로 해당 레벨의 모든 단어 조회
        do {
            WordRepository.WordPage wordPage = wordRepository.findByLevelWithPagination(level, count * 2, lastEvaluatedKey);
            for (Word word : wordPage.getWords()) {
                if (!learnedWordIds.contains(word.getWordId()) && !newWordIds.contains(word.getWordId())) {
                    newWordIds.add(word.getWordId());
                    if (newWordIds.size() >= count) break;
                }
            }
            lastEvaluatedKey = wordPage.getNextCursor();
        } while (newWordIds.size() < count && lastEvaluatedKey != null);

        logger.info("Selected {} new words for user {} at level {}", newWordIds.size(), userId, level);
        return newWordIds;
    }

    private List<Word> getWordDetails(List<String> wordIds) {
        if (wordIds == null || wordIds.isEmpty()) {
            return new ArrayList<>();
        }

        // BatchGetItem으로 한 번에 조회 (N+1 문제 해결)
        return wordRepository.findByIds(wordIds);
    }

    private Map<String, Object> calculateProgress(DailyStudy dailyStudy) {
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

    private APIGatewayProxyResponseEvent markWordLearned(APIGatewayProxyRequestEvent request) {
        Map<String, String> pathParams = request.getPathParameters();
        String userId = pathParams != null ? pathParams.get("userId") : null;
        String wordId = pathParams != null ? pathParams.get("wordId") : null;

        if (userId == null || wordId == null) {
            return createResponse(400, ApiResponse.error("userId and wordId are required"));
        }

        String today = LocalDate.now().toString();

        Optional<DailyStudy> optDailyStudy = dailyStudyRepository.findByUserIdAndDate(userId, today);
        if (optDailyStudy.isEmpty()) {
            return createResponse(404, ApiResponse.error("Daily study not found"));
        }

        DailyStudy dailyStudy = optDailyStudy.get();

        // 이미 학습 완료된 단어인지 확인
        if (dailyStudy.getLearnedWordIds() != null && dailyStudy.getLearnedWordIds().contains(wordId)) {
            return createResponse(200, ApiResponse.success("Already marked as learned", dailyStudy));
        }

        // 학습 완료 처리
        dailyStudyRepository.addLearnedWord(userId, today, wordId);

        // 업데이트된 데이터 조회
        DailyStudy updatedDailyStudy = dailyStudyRepository.findByUserIdAndDate(userId, today).orElse(dailyStudy);

        // 완료 여부 확인
        if (updatedDailyStudy.getLearnedCount() >= updatedDailyStudy.getTotalWords()) {
            updatedDailyStudy.setIsCompleted(true);
            dailyStudyRepository.save(updatedDailyStudy);
        }

        logger.info("Marked word as learned: userId={}, wordId={}", userId, wordId);
        return createResponse(200, ApiResponse.success("Word marked as learned", calculateProgress(updatedDailyStudy)));
    }
}
