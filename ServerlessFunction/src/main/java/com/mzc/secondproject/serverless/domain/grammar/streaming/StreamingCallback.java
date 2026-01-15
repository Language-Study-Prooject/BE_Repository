package com.mzc.secondproject.serverless.domain.grammar.streaming;

import com.mzc.secondproject.serverless.domain.grammar.dto.response.ConversationResponse;

/**
 * Bedrock Streaming 응답을 처리하기 위한 콜백 인터페이스
 */
public interface StreamingCallback {

	/**
	 * 토큰이 수신될 때마다 호출
	 * @param token 수신된 토큰 (텍스트 조각)
	 */
	void onToken(String token);

	/**
	 * 스트리밍이 완료되고 전체 응답이 파싱되었을 때 호출
	 * @param response 완성된 대화 응답
	 */
	void onComplete(ConversationResponse response);

	/**
	 * 에러 발생 시 호출
	 * @param error 발생한 예외
	 */
	void onError(Throwable error);
}
