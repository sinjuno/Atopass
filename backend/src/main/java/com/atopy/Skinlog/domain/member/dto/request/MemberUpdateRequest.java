package com.atopy.Skinlog.domain.member.dto.request;

import jakarta.validation.constraints.Size;

public record MemberUpdateRequest(
	@Size(max = 50, message = "닉네임은 50자 이하여야 합니다.")
	String nickname,

	@Size(min = 8, max = 64, message = "비밀번호는 8~64자여야 합니다.")
	String password
) {

	public boolean hasNickname() {
		return nickname != null && !nickname.isBlank();
	}

	public boolean hasPassword() {
		return password != null && !password.isBlank();
	}

}
