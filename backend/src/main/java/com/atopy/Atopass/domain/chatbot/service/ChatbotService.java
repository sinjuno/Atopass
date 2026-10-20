package com.atopy.Atopass.domain.chatbot.service;

import com.atopy.Atopass.domain.chatbot.dto.request.ChatChoiceRequest;
import com.atopy.Atopass.domain.chatbot.dto.request.ChatRecordSaveRequest;
import com.atopy.Atopass.domain.chatbot.dto.response.ChatDraftStateResponse;
import com.atopy.Atopass.domain.chatbot.dto.response.ChatMessagesResponse;
import com.atopy.Atopass.domain.chatbot.dto.response.ChatReplyResponse;
import com.atopy.Atopass.domain.chatbot.dto.response.ChatDraftResponse;
import com.atopy.Atopass.domain.chatbot.dto.response.ChatSessionResponse;
import com.atopy.Atopass.domain.chatbot.entity.ChatMode;
import com.atopy.Atopass.domain.chatbot.entity.ChatRole;
import com.atopy.Atopass.domain.chatbot.llm.ChatModelClient;
import com.atopy.Atopass.domain.chatbot.llm.ChatTurn;
import com.atopy.Atopass.domain.chatbot.llm.LlmProperties;
import com.atopy.Atopass.domain.chatbot.service.ChatReplyParser.ModelOutput;
import com.atopy.Atopass.domain.chatbot.service.ChatSessionService.TurnContext;
import com.atopy.Atopass.domain.record.dto.request.RecordRequest;
import com.atopy.Atopass.domain.record.dto.response.RecordCreatedResponse;
import com.atopy.Atopass.domain.record.entity.SkinSign;
import com.atopy.Atopass.global.exception.BusinessException;
import com.atopy.Atopass.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 챗봇 대화 흐름을 조율한다.
 * 이 클래스는 트랜잭션을 열지 않는다. 오래 걸리는 LLM 호출 동안 DB 연결을 붙잡지 않도록
 * DB 작업은 {@link ChatSessionService}의 짧은 트랜잭션으로 앞뒤에서 처리한다.
 * LLM 호출이 실패하면(CHATBOT_UNAVAILABLE) 사용자의 입력은 저장하지 않으므로 같은 요청을 다시 보내도 된다.
 *
 * <p>기록 모드에서 고통 지수·고통이 있는 부위·수면 시간·약 복용·나타난 증상·특이사항은 서버가 정해진 문장으로 묻고,
 * 화면은 선택지로 답을 받는다({@link #answerChoice}). 선택지 답은 LLM을 거치지 않고 초안에 바로 반영한다.
 * LLM의 질문 문장은 부위나 증상 점수를 되물을 때만 쓴다.
 *
 * <p>물어볼 항목이 끝나면 그날 기록을 바로 저장하고, 일일 총정리와 수고했다는 인사를 보여 준다.
 * 그 뒤로는 같은 대화에서 글로 고치는 대로 바로 다시 저장한다. (총정리 화면으로 보내지 않는다)
 */
@Service
@RequiredArgsConstructor
public class ChatbotService {

	/** 해석 실패가 이 횟수만큼 이어지면 입력 예시를 함께 안내한다. */
	static final int EXAMPLE_AFTER_FAILURES = 3;
	private static final int MAX_QUERY_REPLY_LENGTH = 2000;

	static final String NOT_UNDERSTOOD = "죄송해요, 잘 이해하지 못했어요. 다시 한 번 말씀해 주시겠어요?";
	static final String SCORE_OUT_OF_RANGE = "점수는 0~10 사이 숫자로 알려 주세요. (0은 없음, 10은 매우 심함)";
	static final String PAIN_INDEX_OUT_OF_RANGE = "고통 지수는 0~10 사이 숫자로 알려 주세요. (0은 없음, 10은 매우 심함)";
	static final String CONFIRM = "입력하신 내용을 정리했어요.";
	static final String SAVED = "오늘 기록을 저장했어요. 오늘의 총정리예요.";
	static final String THANKS = "오늘 하루도 정말 수고 많으셨어요! 바꿀 내용이 생기면 언제든 여기에 글로 적어 주세요. 바로 고쳐 둘게요.";
	static final String NO_CHANGE = "알겠어요. 기록은 그대로 둘게요. 바꿀 내용이 생기면 언제든 적어 주세요.";
	static final String EDIT_SAVED = "바로 반영해서 저장했어요. 지금 기록은 아래와 같아요.";
	static final String EXAMPLE = " 예: \"고통은 6점이고 잠은 6시간 잤어요.\"";
	static final String FINISH_TEXT = "기록을 마칠게요";

	/** 선택지로 답을 받는 항목. 특이사항은 글로 받지만 "없어요"는 선택지로 고를 수 있다. */
	private static final Set<String> CHOICE_FIELDS = Set.of("painIndex", "bodyParts", "sleepHours", "medication", "skinSigns", "notes");

	private final ChatConsentService consentService;
	private final ChatSessionService sessionService;
	private final ChatModelClient modelClient;
	private final ChatPromptFactory promptFactory;
	private final ChatReplyParser replyParser;
	private final JsonMapper jsonMapper;
	private final LlmProperties llmProperties;

	public ChatSessionResponse createSession(Long memberId, ChatMode mode) {
		consentService.requireConsent(memberId);
		return ChatSessionResponse.from(sessionService.open(memberId, mode));
	}

	public ChatMessagesResponse messages(Long memberId, String sessionId) {
		return ChatMessagesResponse.from(sessionService.messages(memberId, sessionId));
	}

	public RecordCreatedResponse saveRecord(Long memberId, String sessionId, ChatRecordSaveRequest request) {
		return sessionService.saveRecord(memberId, sessionId, request);
	}

	public ChatReplyResponse sendMessage(Long memberId, String sessionId, String userText) {
		// 대화 내용이 외부 LLM으로 나가므로, 동의하지 않았거나 철회한 회원이면 LLM을 부르기 전에 막는다.
		consentService.requireConsent(memberId);
		requireWithinDailyLimit(memberId);
		TurnContext context = sessionService.loadForTurn(memberId, sessionId);

		ChatDraft draft = ChatDraft.fromJson(jsonMapper, context.draftJson());
		String systemPrompt = context.mode() == ChatMode.RECORD
			? promptFactory.recordPrompt(draft, context.today())
			: promptFactory.queryPrompt(context.queryContext(), context.today());
		String raw = modelClient.complete(systemPrompt, withUserMessage(context.history(), userText));

		Outcome outcome = context.mode() == ChatMode.RECORD
			? recordOutcome(context, draft, raw)
			: queryOutcome(context, raw);

		Long recordId = sessionService.saveTurn(sessionId, userText, outcome.response().reply(),
			outcome.draftJson(), outcome.failCount(), outcome.response().requiresConfirmation(), false, outcome.record());
		return context.mode() == ChatMode.RECORD ? outcome.response().withRecordId(recordId) : outcome.response();
	}

	/**
	 * 선택지로 고른 답을 반영한다. LLM을 부르지 않으므로 이용 한도에 세지 않는다.
	 * 지금 묻는 항목(askingField)이 아니거나 값이 규칙에 어긋나면 400 INVALID_INPUT이다.
	 */
	public ChatReplyResponse answerChoice(Long memberId, String sessionId, ChatChoiceRequest request) {
		consentService.requireConsent(memberId);
		TurnContext context = loadRecordTurn(memberId, sessionId);
		ChatDraft draft = ChatDraft.fromJson(jsonMapper, context.draftJson());

		String field = request.field();
		if (!CHOICE_FIELDS.contains(field) || !field.equals(draft.askingField())) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "지금 고를 수 있는 항목이 아닙니다.");
		}
		ObjectNode updates = jsonMapper.createObjectNode();
		if (request.skipped()) {
			if (!ChatDraft.skippable(field)) {
				throw new BusinessException(ErrorCode.INVALID_INPUT, "건너뛸 수 없는 항목입니다.");
			}
			updates.putArray("skipped").add(field);
		} else {
			// 특이사항은 글로 받는다. 선택지로는 "없어요"(skip)만 고를 수 있다.
			if (request.value() == null || "notes".equals(field)) {
				throw new BusinessException(ErrorCode.INVALID_INPUT, "선택한 값이 없습니다.");
			}
			updates.set(field, jsonMapper.valueToTree(request.value()));
		}
		ChatDraft.ApplyResult result = "bodyParts".equals(field) && !request.skipped()
			? draft.applyBodyParts(updates.path(field))
			: draft.apply(updates);
		if (result != ChatDraft.ApplyResult.OK) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "선택한 값이 올바르지 않습니다.");
		}
		draft.markFollowUp(false);
		return choiceTurn(context, draft, choiceText(field, request.skipped(), draft.toResponse()), false);
	}

	/** "기록 마치기" 버튼. 필수 항목이 채워졌으면 남은 선택 항목을 묻지 않고 확인 단계로 넘어간다. */
	public ChatReplyResponse finish(Long memberId, String sessionId) {
		consentService.requireConsent(memberId);
		TurnContext context = loadRecordTurn(memberId, sessionId);
		ChatDraft draft = ChatDraft.fromJson(jsonMapper, context.draftJson());
		draft.markFollowUp(false);
		return choiceTurn(context, draft, FINISH_TEXT, true);
	}

	private TurnContext loadRecordTurn(Long memberId, String sessionId) {
		TurnContext context = sessionService.loadForTurn(memberId, sessionId);
		if (context.mode() != ChatMode.RECORD) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "질의 모드 대화에서는 선택지로 답할 수 없습니다.");
		}
		return context;
	}

	private ChatReplyResponse choiceTurn(TurnContext context, ChatDraft draft, String userText, boolean finished) {
		boolean ready = draft.readyForConfirmation(finished);
		String reply = ready ? completion(draft, null) : question(draft.askingField());
		ChatReplyResponse response = new ChatReplyResponse(reply, true, draft.pendingFields(),
			ready ? null : draft.askingField(), ready, draft.toResponse(), draft.editing(), context.recordId());
		Long recordId = sessionService.saveTurn(context.sessionId(), userText, reply, draft.toJson(jsonMapper), 0, ready,
			true, ready ? draft.toRecordRequest(context.recordDate()) : null);
		return response.withRecordId(recordId);
	}

	/** 대화 내역에 남길 사용자 말. 화면이 버튼을 누르자마자 보여 주는 문장과 같다. */
	private static String choiceText(String field, boolean skip, ChatDraftResponse draft) {
		if (skip) {
			return "notes".equals(field) || "bodyParts".equals(field) ? "없어요" : "넘어갈게요";
		}
		return switch (field) {
			case "painIndex" -> draft.painIndex() + "점";
			case "bodyParts" -> draft.skinConditions().stream()
				.map(c -> c.bodyPart().label()).collect(Collectors.joining(", "));
			case "sleepHours" -> draft.sleepHours().stripTrailingZeros().toPlainString() + "시간";
			case "medication" -> Boolean.TRUE.equals(draft.medication()) ? "예" : "아니오";
			default -> draft.skinSigns().isEmpty() ? "없어요"
				: draft.skinSigns().stream().map(SkinSign::label).collect(Collectors.joining(", "));
		};
	}

	/** 최근 24시간 동안 보낸 메시지가 한도에 도달했으면 LLM을 부르기 전에 막는다. (비용 보호) */
	private void requireWithinDailyLimit(Long memberId) {
		long used = sessionService.countUserMessagesSince(memberId, LocalDateTime.now().minusHours(24));
		if (used >= llmProperties.dailyMessageLimit()) {
			throw new BusinessException(ErrorCode.CHAT_LIMIT_EXCEEDED);
		}
	}

	/** 세션에 쌓인 초안과 진행 상태. 마지막 메시지 응답의 draft·pendingFields·requiresConfirmation과 같은 값이다. */
	public ChatDraftStateResponse draft(Long memberId, String sessionId) {
		ChatSessionService.DraftState state = sessionService.draftState(memberId, sessionId);
		ChatDraft draft = ChatDraft.fromJson(jsonMapper, state.draftJson());
		boolean ready = state.confirmationReady();
		return new ChatDraftStateResponse(draft.toResponse(), draft.pendingFields(),
			ready ? null : draft.askingField(), ready, draft.editing() || state.recordId() != null, state.recordId());
	}

	/**
	 * 기록 모드: 응답을 검증한 뒤에만 초안에 반영하고, 반영 결과에 따라 다음 질문을 정한다.
	 * 다음에 물을 항목이 선택지로 답하는 항목이면 화면의 선택지와 어긋나지 않도록 서버의 정해진 문장으로 묻는다.
	 */
	private Outcome recordOutcome(TurnContext context, ChatDraft draft, String raw) {
		Optional<ModelOutput> parsed = replyParser.parse(raw);

		boolean understood = false;
		String reply = null;
		boolean finished = false;
		if (parsed.isPresent() && parsed.get().understood()) {
			ModelOutput output = parsed.get();
			switch (draft.apply(output.updates())) {
				case OK -> {
					understood = true;
					reply = output.reply();
					finished = output.finished();
					draft.markFollowUp(output.followUp() && output.reply() != null);
				}
				case INVALID_SCORE -> reply = SCORE_OUT_OF_RANGE;
				case INVALID_PAIN_INDEX -> reply = PAIN_INDEX_OUT_OF_RANGE;
				case INVALID_VALUE -> reply = NOT_UNDERSTOOD;
			}
		} else if (parsed.isPresent()) {
			reply = parsed.get().reply();
		}

		int failCount = understood ? 0 : context.failCount() + 1;
		boolean readyForConfirmation = understood && draft.readyForConfirmation(finished);
		// 저장된 기록을 고치는 중인데 바뀐 값이 없으면("없어요") 다시 저장하지 않고 답만 한다.
		boolean unchanged = draft.editing() && draft.sameContent(ChatDraft.fromJson(jsonMapper, context.draftJson()));
		boolean save = readyForConfirmation && !unchanged;

		if (!understood) {
			String message = reply == null ? NOT_UNDERSTOOD : reply;
			reply = failCount >= EXAMPLE_AFTER_FAILURES ? message + EXAMPLE : message;
		} else if (save) {
			reply = completion(draft, reply);
		} else if (readyForConfirmation) {
			reply = reply == null || reply.isBlank() ? NO_CHANGE : reply;
		} else if (reply == null || !"symptoms".equals(draft.askingField())) {
			reply = question(draft.askingField());
		}

		ChatReplyResponse response = new ChatReplyResponse(reply, understood, draft.pendingFields(),
			readyForConfirmation ? null : draft.askingField(), readyForConfirmation, draft.toResponse(), draft.editing(),
			context.recordId());
		return new Outcome(response, draft.toJson(jsonMapper), failCount,
			save ? draft.toRecordRequest(context.recordDate()) : null);
	}

	/** 질의 모드: LLM이 쓴 글을 그대로 보여준다. 비어 있으면 이해하지 못한 것으로 본다. */
	private Outcome queryOutcome(TurnContext context, String raw) {
		String text = raw == null ? "" : raw.trim();
		boolean understood = !text.isEmpty();
		if (text.length() > MAX_QUERY_REPLY_LENGTH) {
			text = text.substring(0, MAX_QUERY_REPLY_LENGTH);
		}
		int failCount = understood ? 0 : context.failCount() + 1;
		String reply = understood ? text
			: (failCount >= EXAMPLE_AFTER_FAILURES ? NOT_UNDERSTOOD + " 예: \"이번 주 가려움은 어땠어?\"" : NOT_UNDERSTOOD);
		ChatReplyResponse response = new ChatReplyResponse(reply, understood, List.of(), null, false, null, false, null);
		return new Outcome(response, context.draftJson(), failCount, null);
	}

	/**
	 * 기록을 저장하는 턴의 안내. 처음 저장이면 일일 총정리와 수고했다는 인사를,
	 * 저장된 기록을 고친 것이면 무엇을 바꿨는지(LLM의 답)와 지금 기록을 보여 준다.
	 * 이후에는 빈 항목을 다시 묻지 않고 고칠 내용을 받도록 초안을 수정 상태로 바꾼다.
	 */
	private static String completion(ChatDraft draft, String llmReply) {
		boolean firstSave = !draft.editing();
		draft.startEditing();
		if (firstSave) {
			return SAVED + "\n" + draft.summary() + "\n\n" + THANKS;
		}
		String changed = llmReply == null || llmReply.isBlank() ? "" : llmReply.trim() + "\n";
		return changed + EDIT_SAVED + "\n" + draft.summary();
	}

	/** 지금 물어볼 항목의 정해진 질문. 선택지로 답하는 항목은 화면에 같은 선택지가 함께 나온다. */
	private String question(String askingField) {
		if (askingField == null) {
			return CONFIRM;
		}
		return switch (askingField) {
			case "painIndex" -> "오늘 피부 때문에 느낀 고통은 0~10 중 몇 점인가요? (0은 없음, 10은 매우 심함)";
			case "bodyParts" -> "어느 부위가 그렇게 불편했나요? 해당하는 부위를 모두 골라 주세요.";
			case "symptoms" -> "그 부위의 고통 정도를 0~10점으로 알려 주세요. (0은 없음, 10은 매우 심함)";
			case "sleepHours" -> "어젯밤 몇 시간 주무셨나요?";
			case "medication" -> "오늘 약을 복용하셨나요?";
			case "skinSigns" -> "오늘 나타난 증상을 모두 골라 주세요. 하나도 없으면 '없어요'를 눌러 주세요.";
			default -> "평소와 다른 점이 있으면 500자 이내로 적어 주세요. 없으면 '없어요'를 눌러 주세요.";
		};
	}

	/** 이전 대화에 이번 사용자 메시지를 붙인다. 대화는 사용자 턴으로 시작해야 하므로 앞쪽의 챗봇 턴은 버린다. */
	private List<ChatTurn> withUserMessage(List<ChatTurn> history, String userText) {
		List<ChatTurn> turns = new ArrayList<>(history);
		while (!turns.isEmpty() && turns.get(0).role() != ChatRole.USER) {
			turns.remove(0);
		}
		turns.add(new ChatTurn(ChatRole.USER, userText));
		return turns;
	}

	/** record가 있으면 이번 턴에 그날 기록을 저장한다. */
	private record Outcome(ChatReplyResponse response, String draftJson, int failCount, RecordRequest record) {
	}

}
