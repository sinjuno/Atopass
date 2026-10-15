package com.atopy.Atopass.domain.member.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** 리프레시 토큰 생성과 해시. 서버에는 해시만 저장한다. */
final class RefreshTokenHasher {

	private static final SecureRandom RANDOM = new SecureRandom();

	private RefreshTokenHasher() {
	}

	/** 256비트 난수를 URL-safe Base64로 만든 리프레시 토큰 원문 */
	static String generate() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	static String hash(String rawToken) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
		}
	}

}
