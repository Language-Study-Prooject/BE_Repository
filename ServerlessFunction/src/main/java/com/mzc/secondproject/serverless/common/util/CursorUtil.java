package com.mzc.secondproject.serverless.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * DynamoDB 페이지네이션 커서 유틸리티
 * - lastEvaluatedKey를 Base64 인코딩/디코딩하여 커서로 사용
 */
public class CursorUtil {
	
	private static final Logger logger = LoggerFactory.getLogger(CursorUtil.class);
	
	private CursorUtil() {
		// 유틸리티 클래스 - 인스턴스화 방지
	}
	
	/**
	 * DynamoDB lastEvaluatedKey를 Base64 인코딩된 커서로 변환
	 *
	 * @param lastEvaluatedKey DynamoDB 쿼리 결과의 lastEvaluatedKey
	 * @return Base64 URL-safe 인코딩된 커서 문자열, 또는 null (더 이상 페이지가 없는 경우)
	 */
	public static String encode(Map<String, AttributeValue> lastEvaluatedKey) {
		if (lastEvaluatedKey == null || lastEvaluatedKey.isEmpty()) {
			return null;
		}
		
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<String, AttributeValue> entry : lastEvaluatedKey.entrySet()) {
			if (sb.length() > 0) {
				sb.append("|");
			}
			sb.append(entry.getKey()).append("=").append(entry.getValue().s());
		}
		
		return Base64.getUrlEncoder().encodeToString(sb.toString().getBytes());
	}
	
	/**
	 * Base64 인코딩된 커서를 DynamoDB exclusiveStartKey로 변환
	 *
	 * @param cursor Base64 URL-safe 인코딩된 커서 문자열
	 * @return DynamoDB exclusiveStartKey로 사용할 Map, 또는 null (잘못된 커서인 경우)
	 */
	public static Map<String, AttributeValue> decode(String cursor) {
		if (cursor == null || cursor.isEmpty()) {
			return null;
		}
		
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
}
