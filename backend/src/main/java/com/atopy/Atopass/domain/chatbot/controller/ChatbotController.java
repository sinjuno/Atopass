package com.atopy.Atopass.domain.chatbot.controller;

import com.atopy.Atopass.domain.chatbot.dto.request.ChatMessageRequest;
import com.atopy.Atopass.domain.chatbot.dto.request.ChatRecordSaveRequest;
import com.atopy.Atopass.domain.chatbot.dto.request.CreateSessionRequest;
import com.atopy.Atopass.domain.chatbot.dto.response.ChatDraftStateResponse;
import com.atopy.Atopass.domain.chatbot.dto.response.ChatMessagesResponse;
import com.atopy.Atopass.domain.chatbot.dto.response.ChatReplyResponse;
import com.atopy.Atopass.domain.chatbot.dto.response.ChatSessionResponse;
import com.atopy.Atopass.domain.chatbot.service.ChatbotService;
import com.atopy.Atopass.domain.record.dto.response.RecordCreatedResponse;
import com.atopy.Atopass.global.response.ApiResponse;
import com.atopy.Atopass.global.security.CurrentMember;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/chatbot/sessions")
@RequiredArgsConstructor
public class ChatbotController {

	private final ChatbotService chatbotService;

	@PostMapping
	public ResponseEntity<ApiResponse<ChatSessionResponse>> createSession(
		@CurrentMember Long memberId, @Valid @RequestBody CreateSessionRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.ok(chatbotService.createSession(memberId, request.mode())));
	}

	@PostMapping("/{sessionId}/messages")
	public ApiResponse<ChatReplyResponse> sendMessage(
		@CurrentMember Long memberId, @PathVariable String sessionId,
		@Valid @RequestBody ChatMessageRequest request) {
		return ApiResponse.ok(chatbotService.sendMessage(memberId, sessionId, request.message().trim()));
	}

	@GetMapping("/{sessionId}/messages")
	public ApiResponse<ChatMessagesResponse> messages(
		@CurrentMember Long memberId, @PathVariable String sessionId) {
		return ApiResponse.ok(chatbotService.messages(memberId, sessionId));
	}

	/** 새로고침 뒤에도 일일 총정리 화면이 초안을 다시 채울 수 있도록 세션의 현재 초안을 준다. */
	@GetMapping("/{sessionId}/draft")
	public ApiResponse<ChatDraftStateResponse> draft(
		@CurrentMember Long memberId, @PathVariable String sessionId) {
		return ApiResponse.ok(chatbotService.draft(memberId, sessionId));
	}

	@PostMapping("/{sessionId}/records")
	public ResponseEntity<ApiResponse<RecordCreatedResponse>> saveRecord(
		@CurrentMember Long memberId, @PathVariable String sessionId,
		@Valid @RequestBody ChatRecordSaveRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(ApiResponse.ok(chatbotService.saveRecord(memberId, sessionId, request), "피부 기록이 저장되었습니다."));
	}

}
