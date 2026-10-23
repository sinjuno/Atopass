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
import com.atopy.Atopass.domain.chatbot.entity.SituationalQuestionLog;
import com.atopy.Atopass.domain.chatbot.repository.SituationalQuestionLogRepository;
import com.atopy.Atopass.domain.chatbot.service.ChatReplyParser.ModelOutput;
import com.atopy.Atopass.domain.chatbot.service.ChatSessionService.TurnContext;
import com.atopy.Atopass.domain.member.repository.MemberRepository;
import com.atopy.Atopass.domain.record.dto.request.RecordRequest;
import com.atopy.Atopass.domain.record.dto.response.RecordCreatedResponse;
import com.atopy.Atopass.domain.record.entity.SkinRecord;
import com.atopy.Atopass.domain.record.entity.SkinSign;
import com.atopy.Atopass.domain.record.repository.RecordRepository;
import com.atopy.Atopass.global.exception.BusinessException;
import com.atopy.Atopass.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 챗봇 대화 흐름을 조율한다.
 * 이 클래스는 트랜잭션을 열지 않는다. 오래 걸리는 LLM 호출 동안 DB 연결을 붙잡지 않도록
 * DB 작업은 {@link ChatSessionService}의 짧은 트랜잭션으로 앞뒤에서 처리한다.
 * LLM 호출이 실패하면(CHATBOT_UNAVAILABLE) 사용자의 입력은 저장하지 않으므로 같은 요청을 다시 보내도 된다.
 *
 * <p>기록 모드에서 고통 지수·나타난 증상·그 증상이 나타난 부위·수면 시간·약 복용·특이사항은 서버가 정해진 문장으로 묻고,
 * 화면은 선택지로 답을 받는다({@link #answerChoice}). 선택지 답은 LLM을 거치지 않고 초안에 바로 반영한다.
 * LLM의 질문 문장은 부위나 증상 점수를 되물을 때만 쓴다.
 *
 * <p>사용자가 증상의 뜻이나 모양을 물으면 쉬운 말로 설명하고, 예시 사진을 보여 줘도 되는지 먼저 묻는다.
 * 사용자가 보여 달라고 답했을 때만 그 답에 사진(imageSign)을 붙인다. 그 뒤에는 원래 묻던 질문을 이어서 한다.
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
	static final String SHOW_IMAGE_TEXT = "보여 주세요";
	static final String NO_IMAGE_TEXT = "괜찮아요";
	static final String NO_IMAGE = "알겠어요. 사진은 보여 드리지 않을게요.";
	static final String IMAGE_NOTE = "사람마다 모양과 정도가 다를 수 있어요.";
	static final String EDIT_RESUME = "바꿀 내용이 생기면 언제든 여기에 글로 적어 주세요.";
	/** 증상 예시 사진을 보여 줄지 묻는 항목. 기록 값이 아니라 사진을 보여 줄지에 대한 답이다. */
	static final String SIGN_IMAGE = "signImage";
	/** 상황 질문. 지금은 P01 하나뿐이고, PR 2·3이 P02~P07을 추가할 때 이 집합에 더한다. */
	private static final String P01 = "P01";
	private static final Set<String> SITUATIONAL_FIELDS = Set.of(P01);
	private static final Set<String> P01_ANSWERS = Set.of("TODAY", "SINCE_YESTERDAY", "FEW_DAYS_AGO");
	private static final Map<String, String> P01_ANSWER_LABELS =
		Map.of("TODAY", "오늘", "SINCE_YESTERDAY", "어제부터", "FEW_DAYS_AGO", "2~3일 전");
	/** 마무리 질문. 매일, 특이사항 다음에 묻는다. */
	private static final String C02 = "C02";
	private static final int MAX_SITUATIONAL_NOTE_LENGTH = 200;

	/** 선택지로 답을 받는 항목. 특이사항은 글로 받지만 "없어요"는 선택지로 고를 수 있다. */
	private static final Set<String> CHOICE_FIELDS =
		Set.of("painIndex", "bodyParts", "sleepHours", "medication", "skinSigns", "notes", SIGN_IMAGE, P01, C02);

	private final ChatConsentService consentService;
	private final ChatSessionService sessionService;
	private final ChatModelClient modelClient;
	private final ChatPromptFactory promptFactory;
	private final ChatReplyParser replyParser;
	private final ChatLocalEditParser localEditParser;
	private final JsonMapper jsonMapper;
	private final LlmProperties llmProperties;
	private final SkinSignImages skinSignImages;
	private final SituationalQuestionSelector situationalQuestionSelector;
	private final SituationalAnswerService situationalAnswerService;
	private final SituationalQuestionLogRepository situationalQuestionLogRepository;
	private final RecordRepository recordRepository;
	private final MemberRepository memberRepository;

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
		consentService.requireConsent(memberId);
		TurnContext context = sessionService.loadForTurn(memberId, sessionId);
		ChatDraft draft = ChatDraft.fromJson(jsonMapper, context.draftJson());
		if (context.mode() == ChatMode.RECORD) {
			// 사진을 보여 줄지 묻던 중에 글로 다른 말을 하면, 보여 주지 않는 것으로 보고 그 말을 받는다.
			draft.clearOffer();
			Optional<SkinSign> asked = SkinSignQuestion.detect(userText);
			if (asked.isPresent()) {
				return signQuestionTurn(context, draft, userText, asked.get(), memberId);
			}
		}

		Outcome outcome;
		// "수면 시간을 6시간으로 고쳐줘"처럼 자주 쓰는 수정 말투는 LLM 없이 바로 알아듣는다.
		// ANTHROPIC_API_KEY가 없는 환경에서도 이런 수정만큼은 되게 하고, 이용 한도도 쓰지 않는다.
		// 저장되기 전(처음 입력하는 중)에는 부위·증상처럼 규칙만으로 못 알아듣는 말이 섞이기 쉬워
		// 잘못 자르지 않도록, 이미 저장된 기록을 고치는 중(draft.editing())일 때만 쓴다.
		Optional<ModelOutput> localMatch = context.mode() == ChatMode.RECORD && draft.editing()
			? localEditParser.parse(userText) : Optional.empty();
		if (localMatch.isPresent()) {
			outcome = recordOutcome(context, draft, localMatch, memberId);
		} else {
			// 대화 내용이 외부 LLM으로 나가므로, 동의하지 않았거나 철회한 회원이면 LLM을 부르기 전에 막는다.
			requireWithinDailyLimit(memberId);
			String systemPrompt = context.mode() == ChatMode.RECORD
				? promptFactory.recordPrompt(draft, context.today())
				: promptFactory.queryPrompt(context.queryContext(), context.today());
			String raw = modelClient.complete(systemPrompt, withUserMessage(context.history(), userText));
			outcome = context.mode() == ChatMode.RECORD
				? recordOutcome(context, draft, replyParser.parse(raw), memberId)
				: queryOutcome(context, raw);
		}

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
		if (SIGN_IMAGE.equals(field)) {
			return imageTurn(context, draft, request, memberId);
		}
		if (SITUATIONAL_FIELDS.contains(field)) {
			return situationalTurn(context, draft, request, memberId);
		}
		if (C02.equals(field)) {
			return c02Turn(context, draft, request, memberId);
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
		return choiceTurn(context, draft, choiceText(field, request.skipped(), draft.toResponse()), false, memberId);
	}

	/** "기록 마치기" 버튼. 필수 항목이 채워졌으면 남은 선택 항목을 묻지 않고 확인 단계로 넘어간다. */
	public ChatReplyResponse finish(Long memberId, String sessionId) {
		consentService.requireConsent(memberId);
		TurnContext context = loadRecordTurn(memberId, sessionId);
		ChatDraft draft = ChatDraft.fromJson(jsonMapper, context.draftJson());
		draft.markFollowUp(false);
		draft.clearOffer();
		return choiceTurn(context, draft, FINISH_TEXT, true, memberId);
	}

	private TurnContext loadRecordTurn(Long memberId, String sessionId) {
		TurnContext context = sessionService.loadForTurn(memberId, sessionId);
		if (context.mode() != ChatMode.RECORD) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "질의 모드 대화에서는 선택지로 답할 수 없습니다.");
		}
		return context;
	}

	private ChatReplyResponse choiceTurn(
		TurnContext context, ChatDraft draft, String userText, boolean finished, Long memberId) {
		boolean ready = draft.readyForConfirmation(finished);
		String reply = ready ? completion(draft, null) : question(draft, memberId, context.today());
		ChatReplyResponse response = new ChatReplyResponse(reply, true, draft.pendingFields(),
			ready ? null : draft.askingField(), ready, draft.toResponse(), draft.editing(), context.recordId(), null);
		Long recordId = sessionService.saveTurn(context.sessionId(), userText, reply, draft.toJson(jsonMapper), 0, ready,
			true, ready ? draft.toRecordRequest(context.recordDate()) : null);
		return response.withRecordId(recordId);
	}

	/**
	 * 증상 이름으로 뜻이나 모양을 물은 말. LLM을 부르지 않고 쉬운 말로 설명한 뒤, 예시 사진이 있으면 보여 줘도 되는지 묻는다.
	 * 사진이 없으면 설명 뒤에 원래 묻던 질문을 이어서 한다. LLM을 부르지 않으므로 이용 한도에 세지 않는다.
	 */
	private ChatReplyResponse signQuestionTurn(
		TurnContext context, ChatDraft draft, String userText, SkinSign sign, Long memberId) {
		if (skinSignImages.has(sign)) {
			draft.offerImage(sign);
		}
		String reply = SkinSignQuestion.explanation(sign) + "\n\n" + nextQuestion(draft, memberId, context.today());
		ChatReplyResponse response = new ChatReplyResponse(reply, true, draft.pendingFields(), draft.askingField(),
			false, draft.toResponse(), draft.editing(), context.recordId(), null);
		sessionService.saveTurn(context.sessionId(), userText, reply, draft.toJson(jsonMapper), 0, false, true);
		return response;
	}

	/**
	 * 증상 예시 사진을 보여 줄지에 대한 답. 보여 달라고 했을 때만 답에 사진을 붙이고, 이어서 원래 묻던 질문을 한다.
	 * 사진을 묻느라 저장을 미뤄 둔 기록이면(마지막 답과 함께 증상을 물은 경우) 이때 저장한다.
	 */
	private ChatReplyResponse imageTurn(
		TurnContext context, ChatDraft draft, ChatChoiceRequest request, Long memberId) {
		if (!request.skipped() && !(request.value() instanceof Boolean)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "선택한 값이 올바르지 않습니다.");
		}
		boolean show = !request.skipped() && Boolean.TRUE.equals(request.value());
		SkinSign sign = draft.offeredSign();
		draft.clearOffer();

		String answer = show ? sign.label() + " 예시 사진이에요. " + IMAGE_NOTE : NO_IMAGE;
		boolean ready = !draft.editing() && draft.readyForConfirmation(false);
		String reply = answer + "\n\n" + (ready ? completion(draft, null) : nextQuestion(draft, memberId, context.today()));
		SkinSign imageSign = show ? sign : null;
		ChatReplyResponse response = new ChatReplyResponse(reply, true, draft.pendingFields(),
			ready ? null : draft.askingField(), ready, draft.toResponse(), draft.editing(), context.recordId(), imageSign);
		Long recordId = sessionService.saveTurn(context.sessionId(), show ? SHOW_IMAGE_TEXT : NO_IMAGE_TEXT, reply,
			draft.toJson(jsonMapper), 0, ready, true, ready ? draft.toRecordRequest(context.recordDate()) : null, imageSign);
		return response.withRecordId(recordId);
	}

	/** 사진을 보여 줄지 묻는 중이면 그 질문을, 아니면 원래 묻던 질문을. 저장된 기록을 고치는 중이면 고칠 내용을 기다린다. */
	private String nextQuestion(ChatDraft draft, Long memberId, LocalDate today) {
		SkinSign offered = draft.offeredSign();
		if (offered != null) {
			return offered.label() + " 예시 사진을 보여 드려도 될까요? 실제 피부 사진이 나와요.";
		}
		return draft.editing() ? EDIT_RESUME : question(draft, memberId, today);
	}

	/**
	 * 상황 질문(P01 등) 답. "넘어갈게요"면 저장 없이 끝내고, 아니면 검증해 메인 기록과 별도로 저장한 뒤 끝낸다.
	 * LLM을 부르지 않으므로 이용 한도에 세지 않는다.
	 */
	private ChatReplyResponse situationalTurn(
		TurnContext context, ChatDraft draft, ChatChoiceRequest request, Long memberId) {
		if (request.skipped()) {
			draft.resolveSituationalQuestion();
			return choiceTurn(context, draft, "넘어갈게요", false, memberId);
		}
		String field = request.field();
		if (!P01.equals(field) || !(request.value() instanceof String answer) || !P01_ANSWERS.contains(answer)) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "선택한 값이 올바르지 않습니다.");
		}
		situationalAnswerService.saveP01(memberId, context.recordDate(), answer);
		draft.resolveSituationalQuestion();
		return choiceTurn(context, draft, P01_ANSWER_LABELS.get(answer), false, memberId);
	}

	/**
	 * 마무리 질문(C02) 답. "없어요"면 저장 없이 끝내고, 고른 항목의 글을 메인 기록과 별도로 저장한다.
	 * 진료 때 물어볼 것은 재진 질문 목록에도 남긴다. LLM을 부르지 않으므로 이용 한도에 세지 않는다.
	 */
	private ChatReplyResponse c02Turn(TurnContext context, ChatDraft draft, ChatChoiceRequest request, Long memberId) {
		if (request.skipped()) {
			draft.resolveC02();
			return choiceTurn(context, draft, "없어요", false, memberId);
		}
		JsonNode value = jsonMapper.valueToTree(request.value());
		String productNote = c02NoteOrNull(value.path("productNote"));
		String unusualNote = c02NoteOrNull(value.path("unusualNote"));
		String clinicQuestion = c02NoteOrNull(value.path("clinicQuestion"));
		if (productNote == null && unusualNote == null && clinicQuestion == null) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "선택한 값이 없습니다.");
		}
		situationalAnswerService.saveC02(memberId, context.recordDate(), productNote, unusualNote, clinicQuestion);
		draft.resolveC02();
		return choiceTurn(context, draft, "적어 둘게요", false, memberId);
	}

	/** C02의 글 항목 하나. 문자열이 아니면 체크하지 않은 것으로 보고 null, 너무 길면 값 오류, 빈 글은 null. */
	private static String c02NoteOrNull(JsonNode node) {
		if (!node.isString()) {
			return null;
		}
		String text = node.asString().trim();
		if (text.length() > MAX_SITUATIONAL_NOTE_LENGTH) {
			throw new BusinessException(ErrorCode.INVALID_INPUT, "적은 내용이 너무 길어요.");
		}
		return text.isEmpty() ? null : text;
	}

	/**
	 * 수면·약 복용까지 선택 항목이 끝나 상황 질문 칸을 결정할 때가 됐으면, 조건에 맞고 최근(3일 이내)에 나오지 않은
	 * 상황 질문 중 순위가 가장 높은 것을 고른다. 하루에 한 번만 고르고, 고른 질문은 바로 "오늘 나온 것"으로 남긴다.
	 */
	private void ensureSituationalDecision(Long memberId, ChatDraft draft, LocalDate today) {
		if (!draft.situationalSlotReady()) {
			return;
		}
		Optional<SkinRecord> yesterday = recordRepository.findByMemberIdAndRecordDate(memberId, today.minusDays(1));
		Map<String, LocalDate> lastShown = situationalQuestionLogRepository.findByMemberId(memberId).stream()
			.collect(Collectors.toMap(SituationalQuestionLog::getQuestionId, SituationalQuestionLog::getLastShownDate));
		Optional<String> selected = situationalQuestionSelector.select(
			new SituationalQuestionContext(draft, yesterday), lastShown, today);
		draft.decideSituationalQuestion(selected.orElse(null));
		selected.ifPresent(id -> markSituationalShown(memberId, id, today));
	}

	private void markSituationalShown(Long memberId, String questionId, LocalDate today) {
		SituationalQuestionLog log = situationalQuestionLogRepository.findByMemberIdAndQuestionId(memberId, questionId)
			.orElseGet(() -> SituationalQuestionLog.create(memberRepository.getReferenceById(memberId), questionId, today));
		log.markShown(today);
		situationalQuestionLogRepository.save(log);
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
	private Outcome recordOutcome(TurnContext context, ChatDraft draft, Optional<ModelOutput> parsed, Long memberId) {
		boolean understood = false;
		boolean explain = false;
		String reply = null;
		boolean finished = false;
		if (parsed.isPresent() && parsed.get().understood()) {
			ModelOutput output = parsed.get();
			switch (draft.apply(output.updates())) {
				case OK -> {
					understood = true;
					reply = output.reply();
					finished = output.finished();
					explain = output.explain() && output.reply() != null;
					draft.markFollowUp(output.followUp() && output.reply() != null);
					// 증상을 설명했으면, 예시 사진을 보여 줘도 되는지 이어서 묻는다.
					if (explain && output.sign() != null && skinSignImages.has(output.sign())) {
						draft.offerImage(output.sign());
					}
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
		} else if (explain) {
			// 용어 설명은 기록을 바꾸지 않으므로, 설명 뒤에 사진을 보여 줄지(또는 원래 묻던 질문을) 이어서 묻는다.
			reply = reply + "\n\n" + nextQuestion(draft, memberId, context.today());
		} else if (reply == null || !"symptoms".equals(draft.askingField())) {
			reply = question(draft, memberId, context.today());
		}

		ChatReplyResponse response = new ChatReplyResponse(reply, understood, draft.pendingFields(),
			readyForConfirmation ? null : draft.askingField(), readyForConfirmation, draft.toResponse(), draft.editing(),
			context.recordId(), null);
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
		ChatReplyResponse response = new ChatReplyResponse(reply, understood, List.of(), null, false, null, false, null, null);
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

	/**
	 * 지금 물어볼 항목의 정해진 질문. 선택지로 답하는 항목은 화면에 같은 선택지가 함께 나온다.
	 * 부위는 방금 고른 증상이 있으면 그 증상이 나타난 곳을, 없으면 불편했던 곳을 묻는다.
	 */
	private String question(ChatDraft draft, Long memberId, LocalDate today) {
		ensureSituationalDecision(memberId, draft, today);
		String askingField = draft.askingField();
		if (askingField == null) {
			return CONFIRM;
		}
		return switch (askingField) {
			case "painIndex" -> "오늘 피부 때문에 느낀 고통은 0~10 중 몇 점인가요? (0은 없음, 10은 매우 심함)";
			case "bodyParts" -> draft.hasSkinSigns()
				? "그 증상이 어느 부위에 나타났나요? 해당하는 부위를 모두 골라 주세요."
				: "어느 부위가 그렇게 불편했나요? 해당하는 부위를 모두 골라 주세요.";
			case "symptoms" -> "그 부위의 고통 정도를 0~10점으로 알려 주세요. (0은 없음, 10은 매우 심함)";
			case "sleepHours" -> "어젯밤 몇 시간 주무셨나요?";
			case "medication" -> "오늘 약을 복용하셨나요?";
			case "skinSigns" -> "오늘 나타난 증상을 모두 골라 주세요. 하나도 없으면 '없어요'를 눌러 주세요.";
			case P01 -> "고통 지수가 어제보다 많이 올랐네요. 언제부터 이랬어요?";
			case C02 -> "오늘 같이 남겨둘 게 있어요?";
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
