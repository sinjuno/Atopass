package com.atopy.Atopass.domain.member.controller;

import com.atopy.Atopass.domain.member.dto.request.LoginRequest;
import com.atopy.Atopass.domain.member.dto.request.SignupRequest;
import com.atopy.Atopass.domain.member.dto.response.MemberResponse;
import com.atopy.Atopass.domain.member.dto.response.TokenResponse;
import com.atopy.Atopass.domain.member.service.AuthService;
import com.atopy.Atopass.global.config.WebConfig;
import com.atopy.Atopass.global.response.ApiResponse;
import com.atopy.Atopass.global.security.CurrentMember;
import com.atopy.Atopass.global.security.JwtProperties;
import jakarta.validation.Valid;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

	static final String REFRESH_COOKIE = "refreshToken";
	private static final String COOKIE_PATH = WebConfig.API_PREFIX + "/auth";

	private final AuthService authService;
	private final JwtProperties jwtProperties;

	@PostMapping("/signup")
	public ResponseEntity<ApiResponse<MemberResponse>> signup(@Valid @RequestBody SignupRequest request) {
		MemberResponse member = authService.signup(request);
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.ok(member, "회원가입이 완료되었습니다."));
	}

	@PostMapping("/login")
	public ResponseEntity<ApiResponse<TokenResponse>> login(@Valid @RequestBody LoginRequest request) {
		AuthService.LoginResult result = authService.login(request);
		return ResponseEntity.ok()
			.header(HttpHeaders.SET_COOKIE, refreshCookie(result.refreshToken(),
				Duration.ofDays(jwtProperties.refreshTokenDays())).toString())
			.body(ApiResponse.ok(result.token(), "로그인되었습니다."));
	}

	@PostMapping("/refresh")
	public ApiResponse<TokenResponse> refresh(
		@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
		return ApiResponse.ok(authService.refresh(refreshToken), "토큰이 재발급되었습니다.");
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logout(@CurrentMember Long memberId,
		@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
		authService.logout(memberId, refreshToken);
		return ResponseEntity.noContent()
			.header(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString())
			.build();
	}

	/** 리프레시 토큰은 JS에서 읽을 수 없고 같은 사이트 요청에만 전송된다. */
	private ResponseCookie refreshCookie(String value, Duration maxAge) {
		return ResponseCookie.from(REFRESH_COOKIE, value)
			.httpOnly(true)
			.secure(jwtProperties.cookieSecure())
			.sameSite("Strict")
			.path(COOKIE_PATH)
			.maxAge(maxAge)
			.build();
	}

}
