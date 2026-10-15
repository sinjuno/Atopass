package com.atopy.Skinlog.domain.member.controller;

import com.atopy.Skinlog.domain.member.dto.request.MemberUpdateRequest;
import com.atopy.Skinlog.domain.member.dto.response.MemberResponse;
import com.atopy.Skinlog.domain.member.service.MemberService;
import com.atopy.Skinlog.global.response.ApiResponse;
import com.atopy.Skinlog.global.security.CurrentMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/members")
@RequiredArgsConstructor
public class MemberController {

	private final MemberService memberService;

	@GetMapping("/me")
	public ApiResponse<MemberResponse> getMe(@CurrentMember Long memberId) {
		return ApiResponse.ok(memberService.getMe(memberId));
	}

	@PatchMapping("/me")
	public ApiResponse<MemberResponse> updateMe(
		@CurrentMember Long memberId, @Valid @RequestBody MemberUpdateRequest request) {
		return ApiResponse.ok(memberService.updateMe(memberId, request), "회원 정보가 수정되었습니다.");
	}

}
