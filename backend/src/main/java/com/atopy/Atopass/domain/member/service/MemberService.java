package com.atopy.Atopass.domain.member.service;

import com.atopy.Atopass.domain.member.dto.request.MemberUpdateRequest;
import com.atopy.Atopass.domain.member.dto.request.OnboardingUpdateRequest;
import com.atopy.Atopass.domain.member.dto.response.MemberResponse;
import com.atopy.Atopass.domain.member.dto.response.OnboardingResponse;
import com.atopy.Atopass.domain.member.entity.MedicationStep;
import com.atopy.Atopass.domain.member.entity.Member;
import com.atopy.Atopass.domain.member.repository.MemberRepository;
import com.atopy.Atopass.global.exception.BusinessException;
import com.atopy.Atopass.global.exception.ErrorCode;
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

	public OnboardingResponse getOnboarding(Long memberId) {
		return OnboardingResponse.of(findMember(memberId).getMedicationStep());
	}

	/** 약 등록을 마쳤거나(done) 건너뛴(skipped) 것으로 바꾼다. pending으로 되돌리는 요청은 검증에서 막힌다. */
	@Transactional
	public OnboardingResponse updateOnboarding(Long memberId, OnboardingUpdateRequest request) {
		MedicationStep step = MedicationStep.fromCode(request.medicationStep())
			.orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));
		Member member = findMember(memberId);
		member.changeMedicationStep(step);
		return OnboardingResponse.of(step);
	}

	private Member findMember(Long memberId) {
		return memberRepository.findById(memberId)
			.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
	}

}
