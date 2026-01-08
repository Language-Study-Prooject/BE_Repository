package com.mzc.secondproject.serverless.common.validation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class RequestValidator {

    private final List<String> errors = new ArrayList<>();

    public static RequestValidator create() {
        return new RequestValidator();
    }

    public RequestValidator requireNotNull(Object value, String fieldName) {
        if (value == null) {
            errors.add(fieldName + " is required");
        }
        return this;
    }

    public RequestValidator requireNotEmpty(String value, String fieldName) {
        if (value == null || value.isEmpty()) {
            errors.add(fieldName + " is required");
        }
        return this;
    }

    public RequestValidator requireNotEmpty(Collection<?> value, String fieldName) {
        if (value == null || value.isEmpty()) {
            errors.add(fieldName + " is required");
        }
        return this;
    }

    public RequestValidator requirePositive(Integer value, String fieldName) {
        if (value != null && value <= 0) {
            errors.add(fieldName + " must be positive");
        }
        return this;
    }

    public RequestValidator requireInRange(Integer value, int min, int max, String fieldName) {
        if (value != null && (value < min || value > max)) {
            errors.add(fieldName + " must be between " + min + " and " + max);
        }
        return this;
    }

    public RequestValidator requireAnyNotNull(String message, Object... values) {
        for (Object value : values) {
            if (value != null) {
                return this;
            }
        }
        errors.add(message);
        return this;
    }

    public RequestValidator validate(boolean condition, String errorMessage) {
        if (!condition) {
            errors.add(errorMessage);
        }
        return this;
    }

    public ValidationResult build() {
        if (errors.isEmpty()) {
            return ValidationResult.ok();
        }
        return ValidationResult.error(String.join(", ", errors));
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public String getFirstError() {
        return errors.isEmpty() ? null : errors.get(0);
    }
}
