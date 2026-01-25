package com.mzc.secondproject.serverless.domain.news.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

/**
 * 퀴즈 답변 결과
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class QuizAnswerResult {
	
	private String questionId;
	private String type;            // COMPREHENSION, WORD_MATCH, FILL_BLANK
	private String userAnswer;
	private String correctAnswer;
	private boolean correct;
	private int points;             // 획득 점수 (정답시 배점, 오답시 0)
}
