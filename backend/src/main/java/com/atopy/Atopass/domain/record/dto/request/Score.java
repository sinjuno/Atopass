package com.atopy.Atopass.domain.record.dto.request;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 증상 점수(0~10). null은 "기록하지 않음"이므로 허용한다.
 * 위반 시 오류 코드는 {@code INVALID_SCORE_RANGE}로 응답한다. (GlobalExceptionHandler가 "Score" 코드를 인식)
 */
@Documented
@Constraint(validatedBy = {})
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Min(0)
@Max(10)
@ReportAsSingleViolation
public @interface Score {

	String message() default "증상 점수는 0~10 범위여야 합니다.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};

}
