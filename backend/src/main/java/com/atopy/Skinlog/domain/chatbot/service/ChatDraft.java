package com.atopy.Skinlog.domain.chatbot.service;

import com.atopy.Skinlog.domain.chatbot.dto.response.ChatDraftResponse;
import com.atopy.Skinlog.domain.record.dto.response.LifestyleResponse;
import com.atopy.Skinlog.domain.record.dto.response.SkinConditionResponse;
import com.atopy.Skinlog.domain.record.dto.response.SymptomsResponse;
import com.atopy.Skinlog.domain.record.entity.BodyPart;
import com.atopy.Skinlog.domain.record.entity.OutdoorActivity;
import com.atopy.Skinlog.domain.record.entity.SkinSign;
import com.atopy.Skinlog.domain.record.entity.SleepScore;
import com.atopy.Skinlog.domain.record.entity.Symptom;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 챗봇이 대화로 모아 가는 기록 초안. 사용자가 말한 값만 담고, 말하지 않은 값은 비워 둔다.
 * 세션에는 JSON으로 저장된다. LLM이 준 값은 {@link #apply}에서 모두 검증한 뒤에만 반영한다.
 *
 * <p>챗봇이 묻는 항목: 고통 지수(필수), 수면 시간, 약 복용 여부, 나타난 증상, 특이사항.
 * 부위별 증상 점수와 음주·야외활동은 묻지 않지만, 사용자가 먼저 말하면 함께 기록한다.
 */
public final class ChatDraft {

	/** 사용자가 "없어요/넘어갈게요"라고 답해 더 묻지 않을 선택 항목 */
	static final String SKIP_SLEEP = "sleepHours";
	static final String SKIP_MEDICATION = "medication";
	static final String SKIP_SKIN_SIGNS = "skinSigns";
	static final String SKIP_LIFESTYLE = "lifestyle";
	static final String SKIP_NOTES = "notes";
	private static final Set<String> SKIPPABLE = Set.of(
		SKIP_SLEEP, SKIP_MEDICATION, SKIP_SKIN_SIGNS, SKIP_LIFESTYLE, SKIP_NOTES);

	private static final int MIN_SCORE = 0;
	private static final int MAX_SCORE = 10;
	private static final int MIN_PAIN_INDEX = 1;
	private static final int MAX_PAIN_INDEX = 10;
	/** 특이사항은 채팅 메시지 한 번(500자)으로 받는다. */
	static final int MAX_NOTES_LENGTH = 500;

	/** 업데이트 반영 결과 */
	enum ApplyResult {
		OK, INVALID_SCORE, INVALID_PAIN_INDEX, INVALID_VALUE
	}

	private final Map<BodyPart, Map<Symptom, Integer>> conditions = new EnumMap<>(BodyPart.class);
	private Integer painIndex;
	private BigDecimal sleepHours;
	private Boolean medication;
	/** null이면 아직 묻지 않음, 비어 있으면 "증상 없음" */
	private Set<SkinSign> skinSigns;
	private Boolean alcohol;
	private OutdoorActivity outdoorActivity;
	private String notes;
	private final Set<String> skipped = new LinkedHashSet<>();

	public static ChatDraft empty() {
		return new ChatDraft();
	}

	// ── 저장·복원 ──

	public String toJson(JsonMapper mapper) {
		ObjectNode root = mapper.createObjectNode();
		ObjectNode conditionsNode = root.putObject("conditions");
		conditions.forEach((bodyPart, scores) -> {
			ObjectNode scoresNode = conditionsNode.putObject(bodyPart.name());
			scores.forEach((symptom, score) -> scoresNode.put(symptom.key(), score));
		});
		if (painIndex != null) {
			root.put("painIndex", painIndex);
		}
		if (sleepHours != null) {
			root.put("sleepHours", sleepHours);
		}
		if (medication != null) {
			root.put("medication", medication);
		}
		if (skinSigns != null) {
			ArrayNode signsNode = root.putArray("skinSigns");
			skinSigns.forEach(sign -> signsNode.add(sign.name()));
		}
		if (alcohol != null) {
			root.put("alcohol", alcohol);
		}
		if (outdoorActivity != null) {
			root.put("outdoorActivity", outdoorActivity.name());
		}
		if (notes != null) {
			root.put("notes", notes);
		}
		ArrayNode skippedNode = root.putArray("skipped");
		skipped.forEach(skippedNode::add);
		return mapper.writeValueAsString(root);
	}

	/** 저장된 JSON을 복원한다. 비어 있거나 깨져 있으면 빈 초안으로 시작한다. */
	public static ChatDraft fromJson(JsonMapper mapper, String json) {
		ChatDraft draft = new ChatDraft();
		if (json == null || json.isBlank()) {
			return draft;
		}
		try {
			JsonNode root = mapper.readTree(json);
			JsonNode conditionsNode = root.path("conditions");
			for (BodyPart bodyPart : BodyPart.values()) {
				JsonNode scoresNode = conditionsNode.path(bodyPart.name());
				if (scoresNode.isObject()) {
					Map<Symptom, Integer> scores = new EnumMap<>(Symptom.class);
					for (Symptom symptom : Symptom.values()) {
						JsonNode score = scoresNode.path(symptom.key());
						if (score.isInt()) {
							scores.put(symptom, score.intValue());
						}
					}
					draft.conditions.put(bodyPart, scores);
				}
			}
			if (root.path("painIndex").isInt()) {
				draft.painIndex = root.path("painIndex").intValue();
			}
			if (root.path("sleepHours").isNumber()) {
				draft.sleepHours = root.path("sleepHours").decimalValue();
			}
			if (root.path("medication").isBoolean()) {
				draft.medication = root.path("medication").booleanValue();
			}
			if (root.path("skinSigns").isArray()) {
				draft.skinSigns = EnumSet.noneOf(SkinSign.class);
				for (JsonNode item : root.path("skinSigns")) {
					draft.skinSigns.add(SkinSign.valueOf(item.asString()));
				}
			}
			if (root.path("alcohol").isBoolean()) {
				draft.alcohol = root.path("alcohol").booleanValue();
			}
			if (root.path("outdoorActivity").isString()) {
				draft.outdoorActivity = OutdoorActivity.valueOf(root.path("outdoorActivity").asString());
			}
			if (root.path("notes").isString()) {
				draft.notes = root.path("notes").asString();
			}
			for (JsonNode item : root.path("skipped")) {
				if (item.isString()) {
					draft.skipped.add(item.asString());
				}
			}
		} catch (RuntimeException e) {
			return new ChatDraft();
		}
		return draft;
	}

	// ── LLM이 알려 준 새 정보 반영 ──

	/**
	 * LLM 응답의 updates를 검증한 뒤 반영한다. 하나라도 규칙에 어긋나면 아무것도 반영하지 않는다.
	 * 증상 점수는 0~10, 고통 지수는 1~10 정수만 허용하고, 모르는 부위·증상 이름은 값 오류로 본다.
	 */
	ApplyResult apply(JsonNode updates) {
		if (updates == null || updates.isMissingNode() || updates.isNull()) {
			return ApplyResult.OK;
		}
		if (!updates.isObject()) {
			return ApplyResult.INVALID_VALUE;
		}

		ChatDraft next = copy();

		JsonNode conditionList = updates.path("conditions");
		if (present(conditionList)) {
			if (!conditionList.isArray()) {
				return ApplyResult.INVALID_VALUE;
			}
			for (JsonNode item : conditionList) {
				ApplyResult result = next.applyCondition(item);
				if (result != ApplyResult.OK) {
					return result;
				}
			}
		}

		JsonNode pain = updates.path("painIndex");
		if (present(pain)) {
			// 4.0처럼 소수점이 붙은 값도 정수로 인정하지 않는다. 점수를 임의로 반올림하지 않기 위해서다.
			if (!pain.isInt()) {
				return pain.isNumber() ? ApplyResult.INVALID_PAIN_INDEX : ApplyResult.INVALID_VALUE;
			}
			if (pain.intValue() < MIN_PAIN_INDEX || pain.intValue() > MAX_PAIN_INDEX) {
				return ApplyResult.INVALID_PAIN_INDEX;
			}
			next.painIndex = pain.intValue();
		}

		JsonNode sleep = updates.path("sleepHours");
		if (present(sleep)) {
			if (!sleep.isNumber()) {
				return ApplyResult.INVALID_VALUE;
			}
			BigDecimal hours = sleep.decimalValue().setScale(1, RoundingMode.HALF_UP);
			if (hours.signum() < 0 || hours.compareTo(BigDecimal.valueOf(24)) > 0) {
				return ApplyResult.INVALID_VALUE;
			}
			next.sleepHours = hours;
		}

		JsonNode medicationNode = updates.path("medication");
		if (present(medicationNode)) {
			if (!medicationNode.isBoolean()) {
				return ApplyResult.INVALID_VALUE;
			}
			next.medication = medicationNode.booleanValue();
		}

		JsonNode signs = updates.path("skinSigns");
		if (present(signs)) {
			if (!signs.isArray()) {
				return ApplyResult.INVALID_VALUE;
			}
			// 대화 중에 하나씩 말할 수 있으므로 기존 증상에 더한다. 빈 배열은 "증상 없음"이다.
			Set<SkinSign> merged = next.skinSigns == null ? EnumSet.noneOf(SkinSign.class) : next.skinSigns;
			for (JsonNode item : signs) {
				if (!item.isString()) {
					return ApplyResult.INVALID_VALUE;
				}
				try {
					merged.add(SkinSign.valueOf(item.asString().trim().toUpperCase(Locale.ROOT)));
				} catch (IllegalArgumentException e) {
					return ApplyResult.INVALID_VALUE;
				}
			}
			next.skinSigns = merged;
		}

		JsonNode alcoholNode = updates.path("alcohol");
		if (present(alcoholNode)) {
			if (!alcoholNode.isBoolean()) {
				return ApplyResult.INVALID_VALUE;
			}
			next.alcohol = alcoholNode.booleanValue();
		}

		JsonNode outdoor = updates.path("outdoorActivity");
		if (present(outdoor)) {
			if (!outdoor.isString()) {
				return ApplyResult.INVALID_VALUE;
			}
			try {
				next.outdoorActivity = OutdoorActivity.valueOf(outdoor.asString().trim().toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				return ApplyResult.INVALID_VALUE;
			}
		}

		JsonNode notesNode = updates.path("notes");
		if (present(notesNode)) {
			if (!notesNode.isString() || notesNode.asString().length() > MAX_NOTES_LENGTH) {
				return ApplyResult.INVALID_VALUE;
			}
			String text = notesNode.asString().trim();
			next.notes = text.isEmpty() ? null : text;
		}

		JsonNode skippedNode = updates.path("skipped");
		if (skippedNode.isArray()) {
			for (JsonNode item : skippedNode) {
				if (item.isString() && SKIPPABLE.contains(item.asString())) {
					next.skipped.add(item.asString());
				}
			}
		}

		commit(next);
		return ApplyResult.OK;
	}

	private static boolean present(JsonNode node) {
		return !node.isMissingNode() && !node.isNull();
	}

	private ApplyResult applyCondition(JsonNode item) {
		if (!item.isObject() || !item.path("bodyPart").isString()) {
			return ApplyResult.INVALID_VALUE;
		}
		BodyPart bodyPart;
		try {
			bodyPart = BodyPart.valueOf(item.path("bodyPart").asString().trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return ApplyResult.INVALID_VALUE;
		}
		Map<Symptom, Integer> scores = conditions.computeIfAbsent(bodyPart, k -> new EnumMap<>(Symptom.class));

		JsonNode symptoms = item.path("symptoms");
		if (!present(symptoms)) {
			return ApplyResult.OK;
		}
		if (!symptoms.isObject()) {
			return ApplyResult.INVALID_VALUE;
		}
		for (Map.Entry<String, JsonNode> entry : symptoms.properties()) {
			Symptom symptom;
			try {
				symptom = Symptom.fromKey(entry.getKey());
			} catch (IllegalArgumentException e) {
				return ApplyResult.INVALID_VALUE;
			}
			JsonNode value = entry.getValue();
			if (value.isNull()) {
				continue;
			}
			// 4.0처럼 소수점이 붙은 값은 정수로 인정하지 않는다. 점수를 임의로 반올림하지 않기 위해서다.
			if (!value.isInt()) {
				return value.isNumber() ? ApplyResult.INVALID_SCORE : ApplyResult.INVALID_VALUE;
			}
			int score = value.intValue();
			if (score < MIN_SCORE || score > MAX_SCORE) {
				return ApplyResult.INVALID_SCORE;
			}
			scores.put(symptom, score);
		}
		return ApplyResult.OK;
	}

	// ── 진행 상황 ──

	/**
	 * 기록으로 저장하려면 꼭 필요한 항목이 모두 채워졌는지.
	 * 고통 지수가 있어야 하고, 사용자가 말한 부위가 있다면 그 부위마다 증상 점수가 하나 이상 있어야 한다.
	 */
	public boolean requiredSatisfied() {
		return painIndex != null && conditions.values().stream().noneMatch(Map::isEmpty);
	}

	/** 아직 입력되지 않은 항목. 필수 항목을 먼저, 선택 항목은 건너뛰지 않은 것만 묻는 순서대로 알려 준다. */
	public List<String> pendingFields() {
		List<String> pending = new ArrayList<>();
		if (painIndex == null) {
			pending.add("painIndex");
		}
		if (conditions.values().stream().anyMatch(Map::isEmpty)) {
			pending.add("symptoms");
		}
		pending.addAll(pendingOptionalFields());
		return pending;
	}

	private List<String> pendingOptionalFields() {
		List<String> pending = new ArrayList<>();
		if (sleepHours == null && !skipped.contains(SKIP_SLEEP)) {
			pending.add("sleepHours");
		}
		if (medication == null && !skipped.contains(SKIP_MEDICATION)) {
			pending.add("medication");
		}
		if (skinSigns == null && !skipped.contains(SKIP_SKIN_SIGNS)) {
			pending.add("skinSigns");
		}
		if (notes == null && !skipped.contains(SKIP_NOTES)) {
			pending.add("notes");
		}
		return pending;
	}

	/** 확인 단계로 넘어갈 수 있는지. 필수 항목이 채워졌고, 선택 항목도 끝났거나 사용자가 마치겠다고 한 경우 */
	public boolean readyForConfirmation(boolean userFinished) {
		return requiredSatisfied() && (userFinished || pendingOptionalFields().isEmpty());
	}

	// ── 화면·프롬프트용 변환 ──

	public ChatDraftResponse toResponse() {
		List<SkinConditionResponse> list = conditions.entrySet().stream()
			.sorted(Map.Entry.comparingByKey(Comparator.comparingInt(Enum::ordinal)))
			.map(e -> new SkinConditionResponse(e.getKey(), SymptomsResponse.from(e.getValue())))
			.toList();
		return new ChatDraftResponse(
			list,
			painIndex,
			sleepHours,
			SleepScore.of(sleepHours),
			medication,
			skinSigns == null ? null : List.copyOf(skinSigns),
			new LifestyleResponse(alcohol, outdoorActivity),
			notes);
	}

	/** LLM에게 현재 초안을 알려 주기 위한 사람이 읽는 JSON */
	public String toPromptJson(JsonMapper mapper) {
		return toJson(mapper);
	}

	public Map<BodyPart, Map<Symptom, Integer>> conditions() {
		return conditions;
	}

	// ── 내부 복사·반영 ──

	private ChatDraft copy() {
		ChatDraft copy = new ChatDraft();
		conditions.forEach((bodyPart, scores) -> copy.conditions.put(bodyPart, new EnumMap<>(
			scores.isEmpty() ? new EnumMap<>(Symptom.class) : scores)));
		copy.painIndex = painIndex;
		copy.sleepHours = sleepHours;
		copy.medication = medication;
		copy.skinSigns = skinSigns == null ? null : EnumSet.copyOf(skinSigns.isEmpty()
			? EnumSet.noneOf(SkinSign.class) : skinSigns);
		copy.alcohol = alcohol;
		copy.outdoorActivity = outdoorActivity;
		copy.notes = notes;
		copy.skipped.addAll(skipped);
		return copy;
	}

	private void commit(ChatDraft next) {
		conditions.clear();
		conditions.putAll(next.conditions);
		painIndex = next.painIndex;
		sleepHours = next.sleepHours;
		medication = next.medication;
		skinSigns = next.skinSigns;
		alcohol = next.alcohol;
		outdoorActivity = next.outdoorActivity;
		notes = next.notes;
		skipped.clear();
		skipped.addAll(next.skipped);
	}

}
