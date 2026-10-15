package com.atopy.Atopass.global.security;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtProvider {

	private static final String ISSUER = "atopass";

	private final JwtEncoder jwtEncoder;
	private final JwtProperties properties;

	/** 액세스 토큰을 발급한다. subject는 회원 ID다. */
	public String createAccessToken(Long memberId) {
		Instant now = Instant.now();
		JwtClaimsSet claims = JwtClaimsSet.builder()
			.issuer(ISSUER)
			.subject(String.valueOf(memberId))
			.issuedAt(now)
			.expiresAt(now.plus(properties.accessTokenMinutes(), ChronoUnit.MINUTES))
			.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	/** 액세스 토큰 유효 시간(초) */
	public long accessTokenExpiresInSeconds() {
		return properties.accessTokenMinutes() * 60;
	}

}
