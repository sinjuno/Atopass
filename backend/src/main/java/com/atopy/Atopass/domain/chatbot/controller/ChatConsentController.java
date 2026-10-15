package com.atopy.Atopass.domain.chatbot.controller;

import com.atopy.Atopass.domain.chatbot.dto.response.ChatConsentResponse;
import com.atopy.Atopass.domain.chatbot.service.ChatConsentService;
import com.atopy.Atopass.global.response.ApiResponse;
import com.atopy.Atopass.global.security.CurrentMember;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 챗봇 이용 동의(외부 LLM에 대화·기록 내용 전달) 조회·동의·철회 */
@RestController
@RequestMapping("/chatbot/consent")
@RequiredArgsConstructor
public class ChatConsentController {

	private final ChatConsentService consentService;

	/** 챗봇 화면에 들어올 때 호출해, 동의하지 않았으면 동의 팝업을 보여준다. */
	@GetMapping
	public ApiResponse<ChatConsentResponse> status(@CurrentMember Long memberId) {
		return ApiResponse.ok(consentService.status(memberId));
	}

	@PostMapping
	public ApiResponse<ChatConsentResponse> agree(@CurrentMember Long memberId) {
		return ApiResponse.ok(consentService.agree(memberId), "챗봇 이용에 동의했습니다.");
	}

	@DeleteMapping
	public ResponseEntity<Void> withdraw(@CurrentMember Long memberId) {
		consentService.withdraw(memberId);
		return ResponseEntity.noContent().build();
	}

}
