package com.atopy.Atopass.domain.chatbot.service;

import com.atopy.Atopass.domain.chatbot.dto.request.ChatRecordSaveRequest;
import com.atopy.Atopass.domain.chatbot.entity.ChatMessage;
import com.atopy.Atopass.domain.chatbot.entity.ChatMode;
import com.atopy.Atopass.domain.chatbot.entity.ChatRole;
import com.atopy.Atopass.domain.chatbot.entity.ChatSession;
import com.atopy.Atopass.domain.chatbot.llm.ChatTurn;
import com.atopy.Atopass.domain.chatbot.repository.ChatMessageRepository;
import com.atopy.Atopass.domain.chatbot.repository.ChatSessionRepository;
import com.atopy.Atopass.domain.member.repository.MemberRepository;
import com.atopy.Atopass.domain.member.entity.Member;
import com.atopy.Atopass.domain.record.dto.request.RecordRequest;
import com.atopy.Atopass.domain.record.dto.response.RecordCreatedResponse;
import com.atopy.Atopass.domain.record.dto.response.RecordDetailResponse;
import com.atopy.Atopass.domain.record.entity.SkinRecord;
import com.atopy.Atopass.domain.record.repository.RecordRepository;
import com.atopy.Atopass.domain.record.service.RecordService;
import com.atopy.Atopass.global.exception.BusinessException;
import com.atopy.Atopass.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 챗봇 세션·메시지의 DB 작업. LLM 호출(오래 걸리는 외부 통신) 동안 DB 트랜잭션이 열려 있지 않도록
 * 호출 전후의 DB 작업을 이 클래스의 짧은 트랜잭션으로 나눠 둔다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChatSessionService {

	/** 질의 모드에서 LLM에 근거로 보내는 기록 기간(일) */
	private static final int QUERY_CONTEXT_DAYS = 90;

	private final ChatSessionRepository sessionRepository;
	private final ChatMessageRepository messageRepository;
	private final MemberRepository memberRepository;
	private final RecordRepository recordRepository;
	private final RecordService recordService;
	private final ChatPromptFactory promptFactory;
	private final Clock clock;
	private final JsonMapper jsonMapper;

	/** LLM 호출에 필요한 정보. DB 연결 없이 쓸 수 있는 값만 담는다. */
	record TurnContext(String sessionId, ChatMode mode, String draftJson, int failCount,
		List<ChatTurn> history, LocalDate today, String queryContext) {
	}

	/**
	 * 같은 날짜·모드에 열려 있는 세션이 있으면 그것을 다시 쓴다.
	 * 기록 모드에서 그날 기록이 이미 저장되어 있으면, 그 기록을 초안으로 불러온 수정 세션을 열고
	 * 저장된 내용과 함께 "수정할 내용이 있나요?"라고 먼저 묻는다.
	 * 열려 있던 세션이 지금 저장된 기록과 맞지 않으면(그사이 총정리에서 기록을 만들거나 고치거나 지웠으면) 닫고 새로 연다.
	 */
	@Transactional
	public ChatSession open(Long memberId, ChatMode mode) {
		LocalDate today = LocalDate.now(clock);
		Optional<ChatSession> opened = sessionRepository
			.findFirstByMemberIdAndRecordDateAndModeAndClosedFalseOrderByCreatedAtDesc(memberId, today, mode);
		Member member = memberRepository.getReferenceById(memberId);
		if (mode != ChatMode.RECORD) {
			return opened.orElseGet(() -> sessionRepository.save(ChatSession.open(member, today, mode)));
		}

		SkinRecord record = recordRepository.findByMemberIdAndRecordDate(memberId, today).orElse(null);
		if (opened.isPresent() && matchesRecord(opened.get(), record)) {
			return opened.get();
		}
		opened.ifPresent(ChatSession::close);
		if (record == null) {
			return sessionRepository.save(ChatSession.open(member, today, mode));
		}
		ChatDraft draft = ChatDraft.fromRecord(record);
		ChatSession session = sessionRepository.save(
			ChatSession.openForEdit(member, today, record.getId(), draft.toJson(jsonMapper)));
		messageRepository.save(ChatMessage.of(session, ChatRole.ASSISTANT, editGreeting(draft)));
		return session;
	}

	/** 열려 있는 세션이 지금 저장된 기록(없으면 null)을 그대로 이어서 다루는지 */
	private static boolean matchesRecord(ChatSession session, SkinRecord record) {
		if (record == null) {
			return session.getEditingRecordId() == null;
		}
		return record.getId().equals(session.getEditingRecordId())
			&& !record.getUpdatedAt().isAfter(session.getUpdatedAt());
	}

	/** 수정 세션의 첫 인사. 저장된 내용을 보여 주고 바꿀 내용이 있는지 묻는다. */
	private static String editGreeting(ChatDraft draft) {
		String ask = draft.requiredSatisfied()
			? "수정할 내용이 있나요? 바꿀 내용을 글로 말씀해 주세요. (예: 잠은 8시간 잤어요, 가려움은 없어졌어요)"
			: "수정할 내용이 있나요? 먼저 오늘 피부 때문에 느낀 고통을 0~10 중에서 골라 주세요.";
		return "오늘 기록이 이미 저장되어 있어요.\n" + draft.summary() + "\n\n" + ask;
	}

	/** 메시지를 보내기 전에 세션을 확인하고, LLM에 넘길 대화 내역과 근거 데이터를 모은다. */
	public TurnContext loadForTurn(Long memberId, String sessionId) {
		ChatSession session = findOwned(memberId, sessionId);
		if (session.isClosed()) {
			throw new BusinessException(ErrorCode.SESSION_CLOSED);
		}
		List<ChatMessage> recent = new ArrayList<>(messageRepository.findTop20BySessionIdOrderByIdDesc(sessionId));
		Collections.reverse(recent);
		List<ChatTurn> history = recent.stream()
			.map(m -> new ChatTurn(m.getRole(), m.getContent()))
			.toList();

		LocalDate today = LocalDate.now(clock);
		String queryContext = null;
		if (session.getMode() == ChatMode.QUERY) {
			List<SkinRecord> records = recordRepository
				.findByMemberIdAndRecordDateBetweenOrderByRecordDateAsc(
					memberId, today.minusDays(QUERY_CONTEXT_DAYS), today);
			queryContext = promptFactory.recordsContext(records);
		}
		return new TurnContext(sessionId, session.getMode(), session.getDraft(), session.getFailCount(),
			history, today, queryContext);
	}

	/**
	 * LLM 응답을 받은 뒤(또는 선택지 답을 반영한 뒤) 사용자 메시지와 챗봇 응답, 초안을 한 번에 저장한다.
	 * viaChoice가 true이면 선택지로 보낸 답이라 이용 한도에 세지 않는다.
	 */
	@Transactional
	public void saveTurn(String sessionId, String userText, String assistantText, String draftJson,
		int failCount, boolean confirmationReady, boolean viaChoice) {
		ChatSession session = sessionRepository.findById(sessionId)
			.orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
		messageRepository.save(ChatMessage.of(session, ChatRole.USER, userText, viaChoice));
		messageRepository.save(ChatMessage.of(session, ChatRole.ASSISTANT, assistantText));
		session.updateTurn(draftJson, failCount, confirmationReady);
	}

	/** 새로고침 뒤 초안을 다시 채우기 위한 세션의 현재 초안 상태 */
	record DraftState(String draftJson, boolean confirmationReady) {
	}

	/**
	 * 초안 상태를 조회한다. 기록을 저장해 닫힌 세션이면 초안이 더는 의미가 없으므로 SESSION_CLOSED,
	 * 질의 모드 세션에는 초안이 없으므로 INVALID_INPUT으로 처리한다.
	 */
	public DraftState draftState(Long memberId, String sessionId) {
		ChatSession session = findOwned(memberId, sessionId);
		if (session.isClosed()) {
			throw new BusinessException(ErrorCode.SESSION_CLOSED);
		}
		if (session.getMode() != ChatMode.RECORD) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "질의 모드 대화에는 기록 초안이 없습니다.");
		}
		return new DraftState(session.getDraft(), session.isConfirmationReady());
	}

	public List<ChatMessage> messages(Long memberId, String sessionId) {
		findOwned(memberId, sessionId);
		return messageRepository.findBySessionIdOrderByIdAsc(sessionId);
	}

	/**
	 * 사용자가 확인한 초안을 기록으로 저장하고 세션을 닫는다. 저장 로직은 일반 기록 생성과 같다.
	 * 저장된 기록을 고치는 세션이면 새로 만들지 않고 그 기록을 수정한다. (그사이 기록이 지워졌으면 새로 만든다)
	 */
	@Transactional
	public RecordCreatedResponse saveRecord(Long memberId, String sessionId, ChatRecordSaveRequest request) {
		ChatSession session = findOwned(memberId, sessionId);
		if (session.isClosed()) {
			throw new BusinessException(ErrorCode.SESSION_CLOSED);
		}
		if (session.getMode() != ChatMode.RECORD) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "질의 모드 대화에서는 기록을 저장할 수 없습니다.");
		}
		if (!Boolean.TRUE.equals(request.confirmed())) {
			throw new BusinessException(ErrorCode.DRAFT_NOT_CONFIRMED);
		}
		RecordRequest record = request.record().toRecordRequest(request.recordDate());
		Long editingId = session.getEditingRecordId();
		RecordCreatedResponse saved;
		if (editingId != null && recordRepository.findByIdAndMemberId(editingId, memberId).isPresent()) {
			RecordDetailResponse updated = recordService.update(memberId, editingId, record);
			saved = new RecordCreatedResponse(updated.recordId(), updated.recordDate(), updated.createdAt());
		} else {
			saved = recordService.create(memberId, record);
		}
		session.close();
		return saved;
	}

	/** 회원이 {@code from} 이후 글로 보낸 메시지 수 (선택지 답 제외) */
	public long countUserMessagesSince(Long memberId, LocalDateTime from) {
		return messageRepository.countByMemberSince(memberId, ChatRole.USER, from);
	}

	/** 보존 기간이 지난 메시지를 지운다. 지운 개수를 돌려준다. */
	@Transactional
	public int deleteMessagesOlderThan(LocalDateTime limit) {
		return messageRepository.deleteOlderThan(limit);
	}

	/** 다른 회원의 세션은 존재 여부가 드러나지 않도록 없는 세션과 같게 404로 처리한다. */
	private ChatSession findOwned(Long memberId, String sessionId) {
		return sessionRepository.findByIdAndMemberId(sessionId, memberId)
			.orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
	}

}
