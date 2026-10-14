package com.atopy.Skinlog.global.exception;

import com.atopy.Skinlog.global.response.ErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void businessException_mapsToItsErrorCodeStatusAndBody() {
        ResponseEntity<ErrorResponse> response =
                handler.handleBusinessException(new BusinessException(ErrorCode.RECORD_NOT_FOUND));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().error().code()).isEqualTo("RECORD_NOT_FOUND");
    }
}
