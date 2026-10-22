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
import java.util.Set;
import java.util.TreeSet;
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
	/** 연결 질문을 고를 때 돌아보는 최근 기록 기간(일) */
	private static final int RECENT_LINKED_DAYS = 7;

	private final ChatSessionRepository sessionRepository;
	private final ChatMessageRepository messageRepository;
	private final MemberRepository memberRepository;
	private final RecordRepository recordRepository;
	private final RecordService recordService;
	private final ChatPromptFactory promptFactory;
	private final Clock clock;
	private final JsonMapper jsonMapper;

	/**
	 * LLM 호출에 필요한 정보. DB 연결 없이 쓸 수 있는 값만 담는다.
	 * recentLinked는 기록 날짜 전 7일 동안의 기록에서 답한 연결 질문 ID다.
	 */
	record TurnContext(String sessionId, ChatMode mode, String draftJson, int failCount,
		List<ChatTurn> history, LocalDate today, String queryContext, LocalDate recordDate, Long recordId,
		Set<String> recentLinked) {
	}

	/**
	 * 같은 날짜·모드의 대화를 다시 쓴다. 기록 모드는 하루에 대화 하나를 이어 가므로, 화면을 나갔다 들어와도 대화 내용이 남는다.
	 * - 그날 기록이 없으면: 열려 있는 대화를 이어 가고, 없으면 새로 연다.
	 * - 그날 기록이 있으면: 가장 최근 대화(저장하며 닫힌 대화 포함)를 열어 그 기록을 고치는 대화로 이어 간다.
	 *   대화가 없으면 저장된 내용과 함께 "수정할 내용이 있나요?"라고 먼저 묻는다.
	 *   그사이 다른 화면에서 기록이 바뀌었으면 최신 내용으로 다시 불러오고 그 사실을 알려 준다.
	 * - 고치던 기록이 지워졌으면 그 대화는 닫고 처음부터 새로 묻는다.
	 */
	@Transactional
	public ChatSession open(Long memberId, ChatMode mode) {
		LocalDate today = LocalDate.now(clock);
		Member member = memberRepository.getReferenceById(memberId);
		if (mode != ChatMode.RECORD) {
			return sessionRepository
				.findFirstByMemberIdAndRecordDateAndModeAndClosedFalseOrderByCreatedAtDesc(memberId, today, mode)
				.orElseGet(() -> sessionRepository.save(ChatSession.open(member, today, mode)));
		}

		Optional<ChatSession> latest = sessionRepository
			.findFirstByMemberIdAndRecordDateAndModeOrderByCreatedAtDesc(memberId, today, mode);
		SkinRecord record = recordRepository.findByMemberIdAndRecordDate(memberId, today).orElse(null);
		if (record == null) {
			if (latest.isPresent() && !latest.get().isClosed() && latest.get().getEditingRecordId() == null) {
				return latest.get();
			}
			latest.filter(s -> !s.isClosed()).ifPresent(ChatSession::close);
			return sessionRepository.save(ChatSession.open(member, today, mode));
		}

		ChatDraft saved = ChatDraft.fromRecord(record);
		if (latest.isEmpty()) {
			ChatSession session = sessionRepository.save(
				ChatSession.openForEdit(member, today, record.getId(), saved.toJson(jsonMapper)));
			messageRepository.save(ChatMessage.of(session, ChatRole.ASSISTANT, editGreeting(saved)));
			return session;
		}
		ChatSession session = latest.get();
		session.reopen();
		session.startEditing(record.getId());
		boolean changed = !saved.sameContent(ChatDraft.fromJson(jsonMapper, session.getDraft()));
		boolean empty = messageRepository.findBySessionIdOrderByIdAsc(session.getId()).isEmpty();
		ChatDraft current = ChatDraft.fromJson(jsonMapper, session.getDraft());
		if (changed || !current.editing()) {
			session.updateTurn(saved.toJson(jsonMapper), 0, saved.requiredSatisfied());
		}
		if (empty) {
			messageRepository.save(ChatMessage.of(session, ChatRole.ASSISTANT, editGreeting(saved)));
		} else if (changed) {
			messageRepository.save(ChatMessage.of(session, ChatRole.ASSISTANT,
				"다른 화면에서 바뀐 기록을 불러왔어요.\n" + saved.summary() + "\n\n" + EDIT_ASK));
		}
		return session;
	}

	private static final String EDIT_ASK = "바꿀 내용이 있으면 글로 적어 주세요. 바로 고쳐 둘게요. (예: 잠은 8시간 잤어요, 가려움은 없어졌어요)";

	/** 대화 없이 기록만 있을 때의 첫 인사. 저장된 내용을 보여 주고 바꿀 내용이 있는지 묻는다. */
	private static String editGreeting(ChatDraft draft) {
		String ask = draft.requiredSatisfied()
			? "수정할 내용이 있나요? " + EDIT_ASK
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
		Set<String> recentLinked = new TreeSet<>();
		if (session.getMode() == ChatMode.RECORD) {
			LocalDate recordDate = session.getRecordDate();
			recordRepository.findByMemberIdAndRecordDateBetweenOrderByRecordDateAsc(
					memberId, recordDate.minusDays(RECENT_LINKED_DAYS), recordDate.minusDays(1))
				.forEach(record -> recentLinked.addAll(record.getLinkedAnswers().keySet()));
		}
		return new TurnContext(sessionId, session.getMode(), session.getDraft(), session.getFailCount(),
			history, today, queryContext, session.getRecordDate(), session.getEditingRecordId(), recentLinked);
	}

	/**
	 * LLM 응답을 받은 뒤(또는 선택지 답을 반영한 뒤) 사용자 메시지와 챗봇 응답, 초안을 한 번에 저장한다.
	 * viaChoice가 true이면 선택지로 보낸 답이라 이용 한도에 세지 않는다.
	 */
	@Transactional
	public void saveTurn(String sessionId, String userText, String assistantText, String draftJson,
		int failCount, boolean confirmationReady, boolean viaChoice) {
		saveTurn(sessionId, userText, assistantText, draftJson, failCount, confirmationReady, viaChoice, null);
	}

	/**
	 * 한 턴을 저장하면서, record가 있으면 그 내용으로 그날 기록도 저장한다(없으면 만들고 있으면 고친다).
	 * 대화는 닫지 않으므로 같은 날 다시 들어와도 이어서 고칠 수 있다. 저장한 기록의 ID를 돌려준다.
	 */
	@Transactional
	public Long saveTurn(String sessionId, String userText, String assistantText, String draftJson,
		int failCount, boolean confirmationReady, boolean viaChoice, RecordRequest record) {
		ChatSession session = sessionRepository.findById(sessionId)
			.orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_FOUND));
		messageRepository.save(ChatMessage.of(session, ChatRole.USER, userText, viaChoice));
		messageRepository.save(ChatMessage.of(session, ChatRole.ASSISTANT, assistantText));
		session.updateTurn(draftJson, failCount, confirmationReady);
		if (record == null) {
			return session.getEditingRecordId();
		}
		Long memberId = session.getMember().getId();
		Long recordId = existingRecordId(memberId, session)
			.map(id -> recordService.update(memberId, id, record).recordId())
			.orElseGet(() -> recordService.create(memberId, record).recordId());
		session.startEditing(recordId);
		return recordId;
	}

	/** 이 대화가 고치는 기록. 없으면 그사이 다른 화면에서 같은 날짜로 만든 기록 */
	private Optional<Long> existingRecordId(Long memberId, ChatSession session) {
		Long editingId = session.getEditingRecordId();
		if (editingId != null && recordRepository.findByIdAndMemberId(editingId, memberId).isPresent()) {
			return Optional.of(editingId);
		}
		return recordRepository.findByMemberIdAndRecordDate(memberId, session.getRecordDate()).map(SkinRecord::getId);
	}

	/** 새로고침 뒤 초안을 다시 채우기 위한 세션의 현재 초안 상태 */
	record DraftState(String draftJson, boolean confirmationReady, Long recordId) {
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
		return new DraftState(session.getDraft(), session.isConfirmationReady(), session.getEditingRecordId());
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
