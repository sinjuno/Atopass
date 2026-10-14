package com.atopy.Skinlog.global.auth;

import com.atopy.Skinlog.global.exception.BusinessException;
import com.atopy.Skinlog.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.NativeWebRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CurrentMemberArgumentResolverTest {

    private final CurrentMemberArgumentResolver resolver = new CurrentMemberArgumentResolver();

    @Test
    void validHeader_resolvesAuthenticatedMember() throws Exception {
        NativeWebRequest request = mock(NativeWebRequest.class);
        when(request.getHeader("X-Member-Id")).thenReturn("7");

        AuthenticatedMember result = (AuthenticatedMember) resolver.resolveArgument(null, null, request, null);

        assertThat(result.memberId()).isEqualTo(7L);
    }

    @Test
    void missingHeader_throwsUnauthorized() {
        NativeWebRequest request = mock(NativeWebRequest.class);
        when(request.getHeader("X-Member-Id")).thenReturn(null);

        assertThatThrownBy(() -> resolver.resolveArgument(null, null, request, null))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    void nonNumericHeader_throwsUnauthorized() {
        NativeWebRequest request = mock(NativeWebRequest.class);
        when(request.getHeader("X-Member-Id")).thenReturn("not-a-number");

        assertThatThrownBy(() -> resolver.resolveArgument(null, null, request, null))
                .isInstanceOf(BusinessException.class);
    }
}
