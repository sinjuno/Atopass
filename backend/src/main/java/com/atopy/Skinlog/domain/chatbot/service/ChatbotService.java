package com.atopy.Skinlog.domain.chatbot.service;

import com.atopy.Skinlog.domain.chatbot.dto.request.ChatRecordSaveRequest;
import com.atopy.Skinlog.domain.chatbot.dto.response.ChatDraftStateResponse;
import com.atopy.Skinlog.domain.chatbot.dto.response.ChatMessagesResponse;
import com.atopy.Skinlog.domain.chatbot.dto.response.ChatReplyResponse;
import com.atopy.Skinlog.domain.chatbot.dto.response.ChatSessionResponse;
import com.atopy.Skinlog.domain.chatbot.entity.ChatMode;
import com.atopy.Skinlog.domain.chatbot.entity.ChatRole;
import com.atopy.Skinlog.domain.chatbot.llm.ChatModelClient;
import com.atopy.Skinlog.domain.chatbot.llm.ChatTurn;
import com.atopy.Skinlog.domain.chatbot.service.ChatReplyParser.ModelOutput;
import com.atopy.Skinlog.domain.chatbot.service.ChatSessionService.TurnContext;
import com.atopy.Skinlog.domain.record.dto.response.RecordCreatedResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

/**
 * 챗봇 대화 흐름을 조율한다.
 * 이 클래스는 트랜잭션을 열지 않는다. 오래 걸리는 LLM 호출 동안 DB 연결을 붙잡지 않도록
 * DB 작업은 {@link ChatSessionService}의 짧은 트랜잭션으로 앞뒤에서 처리한다.
 * LLM 호출이 실패하면(CHATBOT_UNAVAILABLE) 사용자의 입력은 저장하지 않으므로 같은 요청을 다시 보내도 된다.
 */
@Service
@RequiredArgsConstructor
public class ChatbotService {

	/** 해석 실패가 이 횟수만큼 이어지면 입력 예시를 함께 안내한다. */
	static final int EXAMPLE_AFTER_FAILURES = 3;
	private static final int MAX_QUERY_REPLY_LENGTH = 2000;

	static final String NOT_UNDERSTOOD = "죄송해요, 잘 이해하지 못했어요. 다시 한 번 말씀해 주시겠어요?";
	static final String SCORE_OUT_OF_RANGE = "점수는 0~10 사이 숫자로 알려 주세요. (0은 없음, 10은 매우 심함)";
	static final String PAIN_INDEX_OUT_OF_RANGE = "고통 지수는 1~10 사이 숫자로 알려 주세요. (1은 거의 없음, 10은 매우 심함)";
	static final String CONFIRM = "입력하신 내용을 정리했어요. 아래 내용을 확인하고 저장해 주세요.";
	static final String EXAMPLE = " 예: \"고통은 6점이고 잠은 6시간 잤어요.\"";

	private final ChatConsentService consentService;
	private final ChatSessionService sessionService;
	private final ChatModelClient modelClient;
	private final ChatPromptFactory promptFactory;
	private final ChatReplyParser replyParser;
	private final JsonMapper jsonMapper;

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
		TurnContext context = sessionService.loadForTurn(memberId, sessionId);

		ChatDraft draft = ChatDraft.fromJson(jsonMapper, context.draftJson());
		String systemPrompt = context.mode() == ChatMode.RECORD
			? promptFactory.recordPrompt(draft, context.today())
			: promptFactory.queryPrompt(context.queryContext(), context.today());
		String raw = modelClient.complete(systemPrompt, withUserMessage(context.history(), userText));

		Outcome outcome = context.mode() == ChatMode.RECORD
			? recordOutcome(context, draft, raw)
			: queryOutcome(context, raw);

		sessionService.saveTurn(sessionId, userText, outcome.response().reply(),
			outcome.draftJson(), outcome.failCount(), outcome.response().requiresConfirmation());
		return outcome.response();
	}

	/** 세션에 쌓인 초안과 진행 상태. 마지막 메시지 응답의 draft·pendingFields·requiresConfirmation과 같은 값이다. */
	public ChatDraftStateResponse draft(Long memberId, String sessionId) {
		ChatSessionService.DraftState state = sessionService.draftState(memberId, sessionId);
		ChatDraft draft = ChatDraft.fromJson(jsonMapper, state.draftJson());
		return new ChatDraftStateResponse(draft.toResponse(), draft.pendingFields(), state.confirmationReady());
	}

	/** 기록 모드: 응답을 검증한 뒤에만 초안에 반영하고, 반영 결과에 따라 다음 질문을 정한다. */
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

		if (!understood) {
			String message = reply == null ? NOT_UNDERSTOOD : reply;
			reply = failCount >= EXAMPLE_AFTER_FAILURES ? message + EXAMPLE : message;
		} else if (readyForConfirmation) {
			reply = CONFIRM;
		} else if (reply == null) {
			reply = fallbackQuestion(draft.pendingFields());
		}

		ChatReplyResponse response = new ChatReplyResponse(
			reply, understood, draft.pendingFields(), readyForConfirmation, draft.toResponse());
		return new Outcome(response, draft.toJson(jsonMapper), failCount);
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
		ChatReplyResponse response = new ChatReplyResponse(reply, understood, List.of(), false, null);
		return new Outcome(response, context.draftJson(), failCount);
	}

	/** LLM이 질문을 주지 않았을 때 비어 있는 항목 중 가장 먼저 필요한 것을 서버가 대신 묻는다. */
	private String fallbackQuestion(List<String> pendingFields) {
		if (pendingFields.isEmpty()) {
			return CONFIRM;
		}
		return switch (pendingFields.get(0)) {
			case "painIndex" -> "오늘 피부 때문에 느낀 고통은 1~10 중 몇 점인가요? (1은 거의 없음, 10은 매우 심함)";
			case "symptoms" -> "그 부위의 증상 정도를 0~10점으로 알려 주세요. (0은 없음, 10은 매우 심함)";
			case "sleepHours" -> "어젯밤 몇 시간 주무셨나요?";
			case "medication" -> "오늘 약을 복용하셨나요? '예' 또는 '아니오'로 답해 주세요.";
			case "skinSigns" -> "오늘 나타난 증상을 모두 골라 주세요. "
				+ "1 피부건조증, 2 홍반, 3 각질, 4 구진, 5 긁은 자국, 6 결절, 7 태선화 (없으면 '없어요')";
			default -> "평소와 다른 점이 있으면 500자 이내로 적어 주세요. 없으면 '없어요'라고 말씀해 주세요.";
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

	private record Outcome(ChatReplyResponse response, String draftJson, int failCount) {
	}

}
