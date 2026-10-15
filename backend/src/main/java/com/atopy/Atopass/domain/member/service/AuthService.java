package com.atopy.Atopass.domain.member.service;

import com.atopy.Atopass.domain.member.dto.request.LoginRequest;
import com.atopy.Atopass.domain.member.dto.request.SignupRequest;
import com.atopy.Atopass.domain.member.dto.response.MemberResponse;
import com.atopy.Atopass.domain.member.dto.response.TokenResponse;
import com.atopy.Atopass.domain.member.entity.Member;
import com.atopy.Atopass.domain.member.entity.RefreshToken;
import com.atopy.Atopass.domain.member.repository.MemberRepository;
import com.atopy.Atopass.domain.member.repository.RefreshTokenRepository;
import com.atopy.Atopass.global.exception.BusinessException;
import com.atopy.Atopass.global.exception.ErrorCode;
import com.atopy.Atopass.global.security.JwtProperties;
import com.atopy.Atopass.global.security.JwtProvider;
import java.time.LocalDateTime;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

	private final MemberRepository memberRepository;
	private final RefreshTokenRepository refreshTokenRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtProvider jwtProvider;
	private final JwtProperties jwtProperties;

	/** 로그인 결과. 리프레시 토큰 원문은 쿠키로만 내려간다. */
	public record LoginResult(TokenResponse token, String refreshToken) {
	}

	@Transactional
	public MemberResponse signup(SignupRequest request) {
		String email = normalize(request.email());
		if (memberRepository.existsByEmail(email)) {
			throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
		}
		Member member = Member.builder()
			.email(email)
			.password(passwordEncoder.encode(request.password()))
			.nickname(request.nickname().trim())
			.build();
		try {
			return MemberResponse.from(memberRepository.saveAndFlush(member));
		} catch (DataIntegrityViolationException e) {
			// 동시 가입으로 unique 제약에 걸린 경우
			throw new BusinessException(ErrorCode.EMAIL_DUPLICATED);
		}
	}

	@Transactional
	public LoginResult login(LoginRequest request) {
		Member member = memberRepository.findByEmail(normalize(request.email()))
			.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));
		if (!passwordEncoder.matches(request.password(), member.getPassword())) {
			throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
		}

		String rawRefreshToken = RefreshTokenHasher.generate();
		refreshTokenRepository.save(RefreshToken.builder()
			.member(member)
			.tokenHash(RefreshTokenHasher.hash(rawRefreshToken))
			.expiresAt(LocalDateTime.now().plusDays(jwtProperties.refreshTokenDays()))
			.build());

		return new LoginResult(issueAccessToken(member.getId()), rawRefreshToken);
	}

	/** 리프레시 토큰으로 새 액세스 토큰을 발급한다. */
	public TokenResponse refresh(String rawRefreshToken) {
		if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
			throw new BusinessException(ErrorCode.TOKEN_EXPIRED);
		}
		RefreshToken stored = refreshTokenRepository
			.findByTokenHash(RefreshTokenHasher.hash(rawRefreshToken))
			.filter(t -> t.isUsable(LocalDateTime.now()))
			.orElseThrow(() -> new BusinessException(ErrorCode.TOKEN_EXPIRED));
		return issueAccessToken(stored.getMember().getId());
	}

	/** 쿠키의 리프레시 토큰을 폐기한다. 본인 토큰만 폐기할 수 있다. */
	@Transactional
	public void logout(Long memberId, String rawRefreshToken) {
		if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
			return;
		}
		refreshTokenRepository.findByTokenHash(RefreshTokenHasher.hash(rawRefreshToken))
			.filter(t -> t.getMember().getId().equals(memberId))
			.ifPresent(RefreshToken::revoke);
	}

	private TokenResponse issueAccessToken(Long memberId) {
		return TokenResponse.bearer(
			jwtProvider.createAccessToken(memberId),
			jwtProvider.accessTokenExpiresInSeconds());
	}

	private String normalize(String email) {
		return email.trim().toLowerCase(Locale.ROOT);
	}

}
