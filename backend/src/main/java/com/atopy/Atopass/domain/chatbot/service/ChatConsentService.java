package com.atopy.Atopass.domain.chatbot.service;

import com.atopy.Atopass.domain.chatbot.dto.response.ChatConsentResponse;
import com.atopy.Atopass.domain.member.entity.Member;
import com.atopy.Atopass.domain.member.repository.MemberRepository;
import com.atopy.Atopass.global.exception.BusinessException;
import com.atopy.Atopass.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 챗봇 이용 동의. 챗봇은 대화와 기록 내용을 외부 LLM에 보내므로, 동의한 회원만 쓸 수 있다.
 * 동의 여부는 서버에 저장해서 확인한다. (화면의 동의 팝업만으로는 서버가 알 수 없다.)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatConsentService {

	private final MemberRepository memberRepository;
	private final Clock clock;

	public ChatConsentResponse status(Long memberId) {
		return toResponse(find(memberId));
	}

	/** 동의한다. 이미 동의했다면 처음 동의한 시각을 유지한다. */
	@Transactional
	public ChatConsentResponse agree(Long memberId) {
		Member member = find(memberId);
		// DB(DATETIME)에 저장되는 정밀도와 응답 값이 어긋나지 않도록 초 단위로 맞춘다.
		member.agreeLlm(LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS));
		return toResponse(member);
	}

	/** 동의를 철회한다. 이미 저장한 기록은 그대로 두고, 이후의 챗봇 대화만 막는다. */
	@Transactional
	public void withdraw(Long memberId) {
		find(memberId).withdrawLlmConsent();
	}

	/** 동의하지 않은 회원이면 CONSENT_REQUIRED(403)를 던진다. LLM을 부르기 전에 호출한다. */
	public void requireConsent(Long memberId) {
		if (!find(memberId).hasLlmConsent()) {
			throw new BusinessException(ErrorCode.CONSENT_REQUIRED);
		}
	}

	private Member find(Long memberId) {
		// 토큰은 유효한데 회원이 사라진 경우: 인증이 유효하지 않은 것으로 본다.
		return memberRepository.findById(memberId)
			.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
	}

	private ChatConsentResponse toResponse(Member member) {
		return new ChatConsentResponse(member.hasLlmConsent(), member.getLlmConsentedAt());
	}

}
