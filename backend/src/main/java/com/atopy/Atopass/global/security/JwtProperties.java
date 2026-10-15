package com.atopy.Atopass.global.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * JWT 및 리프레시 토큰 쿠키 설정. secret은 환경변수(JWT_SECRET)로만 주입한다.
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
	String secret,
	@DefaultValue("30") long accessTokenMinutes,
	@DefaultValue("14") long refreshTokenDays,
	@DefaultValue("true") boolean cookieSecure
) {
}
