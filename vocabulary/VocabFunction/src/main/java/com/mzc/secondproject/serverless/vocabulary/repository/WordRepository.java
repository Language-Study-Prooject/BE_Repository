package com.mzc.secondproject.serverless.vocabulary.repository;

import com.mzc.secondproject.serverless.vocabulary.model.Word;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

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
