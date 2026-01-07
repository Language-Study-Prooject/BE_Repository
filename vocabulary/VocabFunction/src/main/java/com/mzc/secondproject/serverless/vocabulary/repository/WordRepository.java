package com.mzc.secondproject.serverless.vocabulary.repository;

import com.mzc.secondproject.serverless.vocabulary.model.Word;
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

import java.util.ArrayList;

import java.util.Base64;
import java.util.HashMap;
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
    public WordPage findByLevelWithPagination(String level, int limit, String cursor) {
        QueryConditional queryConditional = QueryConditional
                .keyEqualTo(Key.builder().partitionValue("LEVEL#" + level).build());

        QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .limit(limit);

        if (cursor != null && !cursor.isEmpty()) {
            Map<String, AttributeValue> exclusiveStartKey = decodeCursor(cursor);
            if (exclusiveStartKey != null) {
                requestBuilder.exclusiveStartKey(exclusiveStartKey);
            }
        }

        DynamoDbIndex<Word> gsi1 = table.index("GSI1");
        Page<Word> page = gsi1.query(requestBuilder.build()).iterator().next();
        String nextCursor = encodeCursor(page.lastEvaluatedKey());

        return new WordPage(page.items(), nextCursor);
    }

    /**
     * 카테고리별 단어 조회 - 페이지네이션
     */
    public WordPage findByCategoryWithPagination(String category, int limit, String cursor) {
        QueryConditional queryConditional = QueryConditional
                .keyEqualTo(Key.builder().partitionValue("CATEGORY#" + category).build());

        QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .limit(limit);

        if (cursor != null && !cursor.isEmpty()) {
            Map<String, AttributeValue> exclusiveStartKey = decodeCursor(cursor);
            if (exclusiveStartKey != null) {
                requestBuilder.exclusiveStartKey(exclusiveStartKey);
            }
        }

        DynamoDbIndex<Word> gsi2 = table.index("GSI2");
        Page<Word> page = gsi2.query(requestBuilder.build()).iterator().next();
        String nextCursor = encodeCursor(page.lastEvaluatedKey());

        return new WordPage(page.items(), nextCursor);
    }

    private String encodeCursor(Map<String, AttributeValue> lastEvaluatedKey) {
        if (lastEvaluatedKey == null || lastEvaluatedKey.isEmpty()) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, AttributeValue> entry : lastEvaluatedKey.entrySet()) {
            if (sb.length() > 0) sb.append("|");
            sb.append(entry.getKey()).append("=").append(entry.getValue().s());
        }

        return Base64.getUrlEncoder().encodeToString(sb.toString().getBytes());
    }

    private Map<String, AttributeValue> decodeCursor(String cursor) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor));
            Map<String, AttributeValue> result = new HashMap<>();

            for (String pair : decoded.split("\\|")) {
                String[] kv = pair.split("=", 2);
                if (kv.length == 2) {
                    result.put(kv[0], AttributeValue.builder().s(kv[1]).build());
                }
            }

            return result.isEmpty() ? null : result;
        } catch (Exception e) {
            logger.error("Failed to decode cursor: {}", cursor, e);
            return null;
        }
    }

    /**
     * 키워드로 단어 검색 (영어/한국어 contains)
     * 참고: Scan은 비용이 높으므로 데이터가 많아지면 OpenSearch 도입 권장
     */
    public WordPage searchByKeyword(String keyword, int limit, String cursor) {
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
            Map<String, AttributeValue> exclusiveStartKey = decodeCursor(cursor);
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

        String nextCursor = results.size() >= limit ? encodeCursor(lastKey) : null;
        return new WordPage(results, nextCursor);
    }

    public static class WordPage {
        private final List<Word> words;
        private final String nextCursor;

        public WordPage(List<Word> words, String nextCursor) {
            this.words = words;
            this.nextCursor = nextCursor;
        }

        public List<Word> getWords() {
            return words;
        }

        public String getNextCursor() {
            return nextCursor;
        }

        public boolean hasMore() {
            return nextCursor != null;
        }
    }
}
