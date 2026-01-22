package com.mzc.secondproject.serverless.domain.news.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

import java.util.List;

/**
 * 뉴스 퀴즈 문제
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class QuizQuestion {

	private String questionId;      // 문제 ID (q1, q2, ...)
	private String type;            // COMPREHENSION, WORD_MATCH, FILL_BLANK
	private String question;        // 문제 내용
	private List<String> options;   // 선택지 (객관식인 경우)
	private String correctAnswer;   // 정답
	private Integer points;         // 배점
}
