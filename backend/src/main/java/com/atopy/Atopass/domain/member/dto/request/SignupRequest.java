package com.atopy.Atopass.domain.member.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SignupRequest(
	@NotBlank(message = "이메일을 입력해 주세요.")
	@Email(message = "이메일 형식이 올바르지 않습니다.")
	@Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
	String email,

	@NotBlank(message = "비밀번호를 입력해 주세요.")
	@Size(min = 8, max = 64, message = "비밀번호는 8~64자여야 합니다.")
	String password,

	@NotBlank(message = "닉네임을 입력해 주세요.")
	@Size(max = 50, message = "닉네임은 50자 이하여야 합니다.")
	String nickname
) {
}
