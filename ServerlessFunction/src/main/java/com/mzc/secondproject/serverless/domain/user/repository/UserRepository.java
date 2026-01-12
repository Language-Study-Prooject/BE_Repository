package com.mzc.secondproject.serverless.domain.user.repository;

import com.mzc.secondproject.serverless.common.config.AwsClients;
import com.mzc.secondproject.serverless.domain.user.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
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
        logger.info("저장할 사용자 PartitionKey={}, SortKey={}", user.getPk(), user.getSk());
        table.putItem(user);
        return user;
    }

    /**
     * - PK: USER#{cognitoSub}
     * - SK: METADATA
     *
     * @param cognitoSub Cognito User Pool의 sub (UUID)
     * @return 사용자 정보 (Optional)
     */
    public Optional<User> findByCognitoSub(String cognitoSub) {
        Key key = Key.builder()
                .partitionValue("USER#" + cognitoSub)
                .sortValue("METADATA")
                .build();

        User user = table.getItem(key);
        return Optional.ofNullable(user);
    }

    /**
     * 이메일로 사용자 조회
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


    public User update(User user) {
        table.updateItem(user);
        return user;
    }

    public void delete(String cognitoSub) {
        Key key = Key.builder()
                .partitionValue("USER#" + cognitoSub)
                .sortValue("METADATA")
                .build();
        logger.info("삭제할 사용자: cognitoSub={}", cognitoSub);
        table.deleteItem(key);
    }

}
