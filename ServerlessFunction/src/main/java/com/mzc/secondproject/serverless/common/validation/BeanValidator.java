package com.mzc.secondproject.serverless.common.validation;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.mzc.secondproject.serverless.common.exception.CommonErrorCode;
import com.mzc.secondproject.serverless.common.util.ResponseGenerator;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Jakarta Bean Validation 기반 검증 유틸리티
 *
 * DTO에 선언된 @NotNull, @NotEmpty 등의 어노테이션을 검증합니다.
 *
 * 사용 예시:
 * CreateRoomRequest req = ResponseGenerator.gson().fromJson(body, CreateRoomRequest.class);
 * return BeanValidator.validate(req)
 *     .map(error -> ResponseGenerator.fail(CommonErrorCode.REQUIRED_FIELD_MISSING, error))
 *     .orElseGet(() -> {
 *         // 비즈니스 로직
 *         return ResponseGenerator.ok("Success", result);
 *     });
 */
public final class BeanValidator {
	
	private static final Validator VALIDATOR;
	
	static {
		ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
		VALIDATOR = factory.getValidator();
	}
	
	private BeanValidator() {
	}
	
	/**
	 * 객체 검증 후 첫 번째 에러 메시지 반환
	 *
	 * @param object 검증할 객체
	 * @return 에러 메시지 (검증 성공 시 empty)
	 */
	public static <T> Optional<String> validate(T object) {
		if (object == null) {
			return Optional.of("Request body is required");
		}
		
		Set<ConstraintViolation<T>> violations = VALIDATOR.validate(object);
		
		if (violations.isEmpty()) {
			return Optional.empty();
		}
		
		String errorMessage = violations.stream()
				.map(v -> v.getPropertyPath() + " " + v.getMessage())
				.collect(Collectors.joining(", "));
		
		return Optional.of(errorMessage);
	}
	
	/**
	 * 검증 실패 시 에러 응답, 성공 시 핸들러 실행
	 *
	 * @param object  검증할 객체
	 * @param handler 검증 성공 시 실행할 핸들러
	 * @return API 응답
	 */
	public static <T> APIGatewayProxyResponseEvent validateAndExecute(
			T object,
			Function<T, APIGatewayProxyResponseEvent> handler) {
		
		return validate(object)
				.map(error -> ResponseGenerator.fail(CommonErrorCode.REQUIRED_FIELD_MISSING, error))
				.orElseGet(() -> handler.apply(object));
	}
}
