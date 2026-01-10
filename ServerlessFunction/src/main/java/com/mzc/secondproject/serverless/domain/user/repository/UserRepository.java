package com.mzc.secondproject.serverless.domain.user.repository;

import com.mzc.secondproject.serverless.common.config.AwsClients;
import com.mzc.secondproject.serverless.domain.user.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.Optional;

public class UserRepository {

    private static final Logger logger = LoggerFactory.getLogger(UserRepository.class);
    private static final String TABLE_NAME = System.getenv("USER_TABLE_NAME");

    private final DynamoDbTable<User> table;

    public UserRepository() {
        this.table = AwsClients.dynamoDbEnhanced().table(TABLE_NAME, TableSchema.fromBean(User.class));
    }

    public User save(User user) {
        table.putItem(user);
        return user;
    }

    public Optional<User> findById(String userId) {
        Key key = Key.builder()
                .partitionValue(userId)
                .build();

        User user = table.getItem(key);
        return Optional.ofNullable(user);
    }

    /**
     * 이메일로 사용자 조회 (로그인, 중복 체크용)
     * GSI1 사용: GSI1PK = EMAIL#{email}
     */
    public Optional<User> findByEmail(String email) {
        QueryConditional queryConditional = QueryConditional
                .keyEqualTo(Key.builder()
                        .partitionValue("EMAIL#" + email)
                        .build());

        DynamoDbIndex<User> gsi1 = table.index("GSI1");

        return gsi1.query(queryConditional)
                .stream()
                .flatMap(page -> page.items().stream())
                .findFirst();
    }

    public boolean existsByEmail(String email) {
        return findByEmail(email).isPresent();
    }


    public void delete(String userId) {
        Key key = Key.builder()
                .partitionValue(userId)
                .build();
        table.deleteItem(key);
    }

}
