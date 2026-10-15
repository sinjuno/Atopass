package com.atopy.Skinlog.domain.member.service;

import com.atopy.Skinlog.domain.member.dto.request.MemberUpdateRequest;
import com.atopy.Skinlog.domain.member.dto.response.MemberResponse;
import com.atopy.Skinlog.domain.member.entity.Member;
import com.atopy.Skinlog.domain.member.repository.MemberRepository;
import com.atopy.Skinlog.global.exception.BusinessException;
import com.atopy.Skinlog.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

	private final MemberRepository memberRepository;
	private final PasswordEncoder passwordEncoder;

	public MemberResponse getMe(Long memberId) {
		return MemberResponse.from(findMember(memberId));
	}

	@Transactional
	public MemberResponse updateMe(Long memberId, MemberUpdateRequest request) {
		if (!request.hasNickname() && !request.hasPassword()) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "수정할 내용이 없습니다.");
		}

		Member member = findMember(memberId);

		if (request.hasNickname()) {
			member.changeNickname(request.nickname());
		}
		if (request.hasPassword()) {
			member.changePassword(passwordEncoder.encode(request.password()));
		}

		return MemberResponse.from(member);
	}

	private Member findMember(Long memberId) {
		return memberRepository.findById(memberId)
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
	}

}
