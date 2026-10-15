package com.atopy.Atopass.domain.member.dto.response;

public record TokenResponse(String accessToken, String tokenType, long expiresIn) {

	public static TokenResponse bearer(String accessToken, long expiresIn) {
		return new TokenResponse(accessToken, "Bearer", expiresIn);
	}

}
