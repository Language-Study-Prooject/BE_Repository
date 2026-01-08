package com.mzc.secondproject.serverless.domain.vocabulary.repository;

import com.mzc.secondproject.serverless.domain.vocabulary.model.Word;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchGetResultPageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.ReadBatch;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import com.mzc.secondproject.serverless.common.dto.PaginatedResult;
import com.mzc.secondproject.serverless.common.util.CursorUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class WordRepository {

    private static final Logger logger = LoggerFactory.getLogger(WordRepository.class);

    // Singleton 패턴으로 Cold Start 최적화
    private static final DynamoDbClient dynamoDbClient = DynamoDbClient.builder().build();
    private static final DynamoDbEnhancedClient enhancedClient = DynamoDbEnhancedClient.builder()
            .dynamoDbClient(dynamoDbClient)
            .build();
    private static final String tableName = System.getenv("VOCAB_TABLE_NAME");

    private final DynamoDbTable<Word> table;

    public WordRepository() {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(Word.class));
    }

    public Word save(Word word) {
        logger.info("Saving word to DynamoDB: {}", word.getWordId());
        table.putItem(word);
        return word;
    }

    public Optional<Word> findById(String wordId) {
        Key key = Key.builder()
                .partitionValue("WORD#" + wordId)
                .sortValue("METADATA")
                .build();

        Word word = table.getItem(key);
        return Optional.ofNullable(word);
    }

    /**
     * 여러 단어를 한 번에 조회 (BatchGetItem) - N+1 문제 해결
     * DynamoDB BatchGetItem은 최대 100개까지 지원
     */
    public List<Word> findByIds(List<String> wordIds) {
        if (wordIds == null || wordIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<Word> results = new ArrayList<>();

        // BatchGetItem은 최대 100개까지 지원하므로 분할 처리
        int batchSize = 100;
        for (int i = 0; i < wordIds.size(); i += batchSize) {
            List<String> batch = wordIds.subList(i, Math.min(i + batchSize, wordIds.size()));
            results.addAll(batchGetWords(batch));
        }

        return results;
    }

    private List<Word> batchGetWords(List<String> wordIds) {
        ReadBatch.Builder<Word> readBatchBuilder = ReadBatch.builder(Word.class)
                .mappedTableResource(table);

        for (String wordId : wordIds) {
            Key key = Key.builder()
                    .partitionValue("WORD#" + wordId)
                    .sortValue("METADATA")
                    .build();
            readBatchBuilder.addGetItem(key);
        }

        BatchGetResultPageIterable resultPages = enhancedClient.batchGetItem(r -> r.readBatches(readBatchBuilder.build()));

        List<Word> words = new ArrayList<>();
        resultPages.resultsForTable(table).forEach(words::add);
        logger.info("BatchGetItem: requested={}, retrieved={}", wordIds.size(), words.size());

        return words;
    }

    public void delete(String wordId) {
        Key key = Key.builder()
                .partitionValue("WORD#" + wordId)
                .sortValue("METADATA")
                .build();

        table.deleteItem(key);
        logger.info("Deleted word: {}", wordId);
    }

    /**
     * 난이도별 단어 조회 - 페이지네이션
     */
    public PaginatedResult<Word> findByLevelWithPagination(String level, int limit, String cursor) {
        QueryConditional queryConditional = QueryConditional
                .keyEqualTo(Key.builder().partitionValue("LEVEL#" + level).build());

        QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .limit(limit);

        if (cursor != null && !cursor.isEmpty()) {
            Map<String, AttributeValue> exclusiveStartKey = CursorUtil.decode(cursor);
            if (exclusiveStartKey != null) {
                requestBuilder.exclusiveStartKey(exclusiveStartKey);
            }
        }

        DynamoDbIndex<Word> gsi1 = table.index("GSI1");
        Page<Word> page = gsi1.query(requestBuilder.build()).iterator().next();
        String nextCursor = CursorUtil.encode(page.lastEvaluatedKey());

        return new PaginatedResult<>(page.items(), nextCursor);
    }

    /**
     * 카테고리별 단어 조회 - 페이지네이션
     */
    public PaginatedResult<Word> findByCategoryWithPagination(String category, int limit, String cursor) {
        QueryConditional queryConditional = QueryConditional
                .keyEqualTo(Key.builder().partitionValue("CATEGORY#" + category).build());

        QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .limit(limit);

        if (cursor != null && !cursor.isEmpty()) {
            Map<String, AttributeValue> exclusiveStartKey = CursorUtil.decode(cursor);
            if (exclusiveStartKey != null) {
                requestBuilder.exclusiveStartKey(exclusiveStartKey);
            }
        }

        DynamoDbIndex<Word> gsi2 = table.index("GSI2");
        Page<Word> page = gsi2.query(requestBuilder.build()).iterator().next();
        String nextCursor = CursorUtil.encode(page.lastEvaluatedKey());

        return new PaginatedResult<>(page.items(), nextCursor);
    }

    /**
     * 키워드로 단어 검색 (영어/한국어 contains)
     * 참고: Scan은 비용이 높으므로 데이터가 많아지면 OpenSearch 도입 권장
     */
    public PaginatedResult<Word> searchByKeyword(String keyword, int limit, String cursor) {
        String lowerKeyword = keyword.toLowerCase();

        // Filter: PK가 WORD#로 시작하고, english 또는 korean에 keyword 포함
        Expression filterExpression = Expression.builder()
                .expression("begins_with(PK, :pk) AND (contains(#eng, :keyword) OR contains(korean, :keyword))")
                .putExpressionName("#eng", "english")
                .putExpressionValue(":pk", AttributeValue.builder().s("WORD#").build())
                .putExpressionValue(":keyword", AttributeValue.builder().s(lowerKeyword).build())
                .build();

        ScanEnhancedRequest.Builder requestBuilder = ScanEnhancedRequest.builder()
                .filterExpression(filterExpression)
                .limit(limit * 3);  // filter 적용되므로 넉넉히

        if (cursor != null && !cursor.isEmpty()) {
            Map<String, AttributeValue> exclusiveStartKey = CursorUtil.decode(cursor);
            if (exclusiveStartKey != null) {
                requestBuilder.exclusiveStartKey(exclusiveStartKey);
            }
        }

        List<Word> results = new ArrayList<>();
        Map<String, AttributeValue> lastKey = null;

        for (Page<Word> page : table.scan(requestBuilder.build())) {
            for (Word word : page.items()) {
                // 대소문자 무시 검색
                if (word.getEnglish().toLowerCase().contains(lowerKeyword) ||
                    word.getKorean().contains(keyword)) {
                    results.add(word);
                    if (results.size() >= limit) break;
                }
            }
            lastKey = page.lastEvaluatedKey();
            if (results.size() >= limit) break;
        }

        String nextCursor = results.size() >= limit ? CursorUtil.encode(lastKey) : null;
        return new PaginatedResult<>(results, nextCursor);
    }
}
