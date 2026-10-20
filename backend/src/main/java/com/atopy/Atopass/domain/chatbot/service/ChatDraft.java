package com.atopy.Atopass.domain.chatbot.service;

import com.atopy.Atopass.domain.chatbot.dto.response.ChatDraftResponse;
import com.atopy.Atopass.domain.record.dto.request.LifestyleRequest;
import com.atopy.Atopass.domain.record.dto.request.RecordRequest;
import com.atopy.Atopass.domain.record.dto.request.SkinConditionRequest;
import com.atopy.Atopass.domain.record.dto.request.SymptomsRequest;
import com.atopy.Atopass.domain.record.dto.response.LifestyleResponse;
import com.atopy.Atopass.domain.record.dto.response.SkinConditionResponse;
import com.atopy.Atopass.domain.record.dto.response.SymptomsResponse;
import com.atopy.Atopass.domain.record.entity.BodyPart;
import com.atopy.Atopass.domain.record.entity.OutdoorActivity;
import com.atopy.Atopass.domain.record.entity.SkinCondition;
import com.atopy.Atopass.domain.record.entity.SkinRecord;
import com.atopy.Atopass.domain.record.entity.SkinSign;
import com.atopy.Atopass.domain.record.entity.SleepScore;
import com.atopy.Atopass.domain.record.entity.Symptom;
import com.atopy.Atopass.domain.record.entity.SymptomScore;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 챗봇이 대화로 모아 가는 기록 초안. 사용자가 말한 값만 담고, 말하지 않은 값은 비워 둔다.
 * 세션에는 JSON으로 저장된다. LLM이 준 값은 {@link #apply}에서 모두 검증한 뒤에만 반영한다.
 *
 * <p>챗봇이 묻는 항목: 고통 지수(필수), 나타난 증상, 그 증상이 나타난 부위, 수면 시간, 약 복용 여부, 특이사항.
 * 고통 지수·증상·부위는 한 묶음이라, 고통 지수 다음에 증상을, 증상 다음에 바로 부위를 묻는다.
 * 고른 부위에는 고통 지수가 점수로, 방금 고른 증상이 그 부위의 증상으로 들어간다. (고통이 0점이고 증상도 없으면 부위는 묻지 않는다)
 * 음주·야외활동은 묻지 않지만, 사용자가 먼저 말하면 함께 기록한다.
 * 지금 물어볼 항목은 {@link #askingField}로 서버가 정한다. 화면은 이 값에 맞춰 선택지를 보여 준다.
 */
public final class ChatDraft {

	/** 사용자가 "없어요/넘어갈게요"라고 답해 더 묻지 않을 선택 항목 */
	static final String SKIP_BODY_PARTS = "bodyParts";
	static final String SKIP_SLEEP = "sleepHours";
	static final String SKIP_MEDICATION = "medication";
	static final String SKIP_SKIN_SIGNS = "skinSigns";
	static final String SKIP_LIFESTYLE = "lifestyle";
	static final String SKIP_NOTES = "notes";
	private static final Set<String> SKIPPABLE = Set.of(
		SKIP_BODY_PARTS, SKIP_SLEEP, SKIP_MEDICATION, SKIP_SKIN_SIGNS, SKIP_LIFESTYLE, SKIP_NOTES);

	private static final int MIN_SCORE = 0;
	private static final int MAX_SCORE = 10;
	private static final int MIN_PAIN_INDEX = 0;
	private static final int MAX_PAIN_INDEX = 10;
	/** 특이사항은 채팅 메시지 한 번(500자)으로 받는다. */
	static final int MAX_NOTES_LENGTH = 500;

	/** 업데이트 반영 결과 */
	enum ApplyResult {
		OK, INVALID_SCORE, INVALID_PAIN_INDEX, INVALID_VALUE
	}

	/** 부위별 점수. 지금은 고통 점수(PAIN) 하나다. 부위만 말하고 점수가 없으면 빈 맵이다. */
	private final Map<BodyPart, Map<Symptom, Integer>> conditions = new EnumMap<>(BodyPart.class);
	/** 부위마다 나타난 증상 */
	private final Map<BodyPart, Set<SkinSign>> conditionSigns = new EnumMap<>(BodyPart.class);
	private Integer painIndex;
	private BigDecimal sleepHours;
	private Boolean medication;
	/** null이면 아직 묻지 않음, 비어 있으면 "증상 없음" */
	private Set<SkinSign> skinSigns;
	private Boolean alcohol;
	private OutdoorActivity outdoorActivity;
	private String notes;
	private final Set<String> skipped = new LinkedHashSet<>();
	/** 챗봇이 부위나 증상 점수를 되물은 상태. 사용자가 글로 답해야 하므로 선택지 대신 입력창을 연다. */
	private boolean followUp;
	/** 이미 저장된 기록을 불러와 고치는 중. 빈 항목을 차례로 묻지 않고, 사용자가 바꿀 내용을 말하길 기다린다. */
	private boolean editing;

	public static ChatDraft empty() {
		return new ChatDraft();
	}

	/**
	 * 저장된 기록을 고치기 위한 초안. 기록에 없는 선택 항목은 건너뛴 것으로 두어 다시 묻지 않는다.
	 * 기록의 하루 증상에는 부위 증상이 합쳐져 있으므로, 부위 증상을 빼고 직접 고른 증상만 남긴다.
	 */
	public static ChatDraft fromRecord(SkinRecord record) {
		ChatDraft draft = new ChatDraft();
		Set<SkinSign> partSigns = EnumSet.noneOf(SkinSign.class);
		for (SkinCondition condition : record.getConditions()) {
			Map<Symptom, Integer> scores = new EnumMap<>(Symptom.class);
			for (SymptomScore score : condition.getScores()) {
				scores.put(score.getSymptom(), score.getScore());
			}
			draft.conditions.put(condition.getBodyPart(), scores);
			if (!condition.getSkinSigns().isEmpty()) {
				draft.conditionSigns.put(condition.getBodyPart(), EnumSet.copyOf(condition.getSkinSigns()));
				partSigns.addAll(condition.getSkinSigns());
			}
		}
		draft.painIndex = record.getPainIndex();
		draft.sleepHours = record.getSleepHours();
		draft.medication = record.getMedication();
		Set<SkinSign> dailySigns = EnumSet.noneOf(SkinSign.class);
		record.getSkinSigns().stream().filter(sign -> !partSigns.contains(sign)).forEach(dailySigns::add);
		draft.skinSigns = dailySigns;
		draft.alcohol = record.getAlcohol();
		draft.outdoorActivity = record.getOutdoorActivity();
		draft.notes = record.getNotes();
		draft.skipped.addAll(SKIPPABLE);
		draft.editing = true;
		return draft;
	}

	// ── 저장·복원 ──

	public String toJson(JsonMapper mapper) {
		ObjectNode root = mapper.createObjectNode();
		ObjectNode conditionsNode = root.putObject("conditions");
		conditions.forEach((bodyPart, scores) -> {
			ObjectNode scoresNode = conditionsNode.putObject(bodyPart.name());
			scores.forEach((symptom, score) -> scoresNode.put(symptom.key(), score));
			Set<SkinSign> signs = conditionSigns.get(bodyPart);
			if (signs != null && !signs.isEmpty()) {
				ArrayNode signsNode = scoresNode.putArray("skinSigns");
				signs.forEach(sign -> signsNode.add(sign.name()));
			}
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
		if (followUp) {
			root.put("followUp", true);
		}
		if (editing) {
			root.put("editing", true);
		}
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
					Set<SkinSign> signs = readSigns(scoresNode.path("skinSigns"));
					if (!signs.isEmpty()) {
						draft.conditionSigns.put(bodyPart, signs);
					}
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
				draft.skinSigns = readSigns(root.path("skinSigns"));
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
			draft.followUp = root.path("followUp").asBoolean(false);
			draft.editing = root.path("editing").asBoolean(false);
		} catch (RuntimeException e) {
			return new ChatDraft();
		}
		return draft;
	}

	/** 저장된 증상 이름을 읽는다. 증상 목록을 바꾸기 전에 저장된 이름(예: PAPULE)은 건너뛴다. */
	private static Set<SkinSign> readSigns(JsonNode array) {
		Set<SkinSign> signs = EnumSet.noneOf(SkinSign.class);
		for (JsonNode item : array) {
			try {
				signs.add(SkinSign.valueOf(item.asString()));
			} catch (IllegalArgumentException e) {
				// 예전 증상 이름은 무시한다.
			}
		}
		return signs;
	}

	// ── LLM이 알려 준 새 정보 반영 ──

	/**
	 * LLM 응답의 updates를 검증한 뒤 반영한다. 하나라도 규칙에 어긋나면 아무것도 반영하지 않는다.
	 * 부위 고통 점수와 고통 지수는 0~10 정수만 허용하고, 모르는 부위·증상 이름은 값 오류로 본다.
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
			// 정해진 형식은 [{"bodyPart": "FACE", "pain": 6, "skinSigns": ["ITCHING"]}]이지만,
			// LLM이 {"FACE": {"pain": 6}}처럼 부위를 키로 쓰거나 "symptoms": {"pain": 6}으로 감싸는 경우도 같은 뜻으로 받는다.
			if (conditionList.isObject()) {
				for (Map.Entry<String, JsonNode> entry : conditionList.properties()) {
					ApplyResult result = next.applyCondition(entry.getKey(), entry.getValue());
					if (result != ApplyResult.OK) {
						return result;
					}
				}
			} else if (conditionList.isArray()) {
				for (JsonNode item : conditionList) {
					if (!item.isObject() || !item.path("bodyPart").isString()) {
						return ApplyResult.INVALID_VALUE;
					}
					ApplyResult result = next.applyCondition(item.path("bodyPart").asString(), item);
					if (result != ApplyResult.OK) {
						return result;
					}
				}
			} else {
				return ApplyResult.INVALID_VALUE;
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

		// 저장된 기록을 고칠 때 "가려움은 없어졌어요", "얼굴은 빼 주세요"처럼 지우는 말을 받는다.
		JsonNode removeSigns = updates.path("removeSkinSigns");
		if (present(removeSigns)) {
			if (!removeSigns.isArray()) {
				return ApplyResult.INVALID_VALUE;
			}
			for (JsonNode item : removeSigns) {
				SkinSign sign;
				try {
					sign = SkinSign.valueOf(item.asString().trim().toUpperCase(Locale.ROOT));
				} catch (IllegalArgumentException e) {
					return ApplyResult.INVALID_VALUE;
				}
				if (next.skinSigns != null) {
					next.skinSigns.remove(sign);
				}
				next.conditionSigns.values().forEach(signsOfPart -> signsOfPart.remove(sign));
			}
		}
		JsonNode removeParts = updates.path("removeBodyParts");
		if (present(removeParts)) {
			if (!removeParts.isArray()) {
				return ApplyResult.INVALID_VALUE;
			}
			for (JsonNode item : removeParts) {
				BodyPart part;
				try {
					part = BodyPart.valueOf(item.asString().trim().toUpperCase(Locale.ROOT));
				} catch (IllegalArgumentException e) {
					return ApplyResult.INVALID_VALUE;
				}
				next.conditions.remove(part);
				next.conditionSigns.remove(part);
			}
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

	/** "넘어갈게요"로 건너뛸 수 있는 항목인지 */
	static boolean skippable(String field) {
		return SKIPPABLE.contains(field);
	}

	/** 저장된 기록을 고치는 중인지 */
	public boolean editing() {
		return editing;
	}

	/** 기록을 저장했으므로 이제부터는 빈 항목을 묻지 않고 고칠 내용을 받는다. */
	void startEditing() {
		this.editing = true;
		skipped.addAll(SKIPPABLE);
	}

	/** 기록 내용(초안 값)이 같은지. 진행 상태(건너뜀·되묻기)는 비교하지 않는다. */
	boolean sameContent(ChatDraft other) {
		return toResponse().equals(other.toResponse());
	}

	/** 일반 기록 저장과 같은 형식으로 바꾼다. */
	public RecordRequest toRecordRequest(LocalDate recordDate) {
		List<SkinConditionRequest> list = conditions.entrySet().stream()
			.sorted(Map.Entry.comparingByKey(Comparator.comparingInt(Enum::ordinal)))
			.map(e -> new SkinConditionRequest(e.getKey(), new SymptomsRequest(e.getValue().get(Symptom.PAIN)),
				List.copyOf(conditionSigns.getOrDefault(e.getKey(), EnumSet.noneOf(SkinSign.class)))))
			.toList();
		LifestyleRequest lifestyle = alcohol == null && outdoorActivity == null
			? null : new LifestyleRequest(alcohol, outdoorActivity);
		return new RecordRequest(recordDate, list, painIndex, sleepHours, medication,
			skinSigns == null ? List.of() : List.copyOf(skinSigns), lifestyle, notes);
	}

	/** 이번 응답이 부위나 증상 점수를 되묻는 질문인지 기록한다. 다음 턴이 성공하면 다시 정해진다. */
	void markFollowUp(boolean followUp) {
		this.followUp = followUp;
	}

	private static boolean present(JsonNode node) {
		return !node.isMissingNode() && !node.isNull();
	}

	/** 부위 하나를 반영한다. item에는 고통 점수(pain 또는 symptoms.pain)와 그 부위에 나타난 증상(skinSigns)이 올 수 있다. */
	private ApplyResult applyCondition(String bodyPartName, JsonNode item) {
		BodyPart bodyPart;
		try {
			bodyPart = BodyPart.valueOf(bodyPartName.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return ApplyResult.INVALID_VALUE;
		}
		Map<Symptom, Integer> scores = conditions.computeIfAbsent(bodyPart, k -> new EnumMap<>(Symptom.class));

		if (!present(item)) {
			return ApplyResult.OK;
		}
		if (!item.isObject()) {
			return ApplyResult.INVALID_VALUE;
		}
		JsonNode wrapped = item.path("symptoms");
		JsonNode pain = present(wrapped) ? wrapped.path(Symptom.PAIN.key()) : item.path(Symptom.PAIN.key());
		if (present(wrapped)) {
			// symptoms에는 고통 점수(pain)만 올 수 있다. 예전 증상 이름(itching 등)은 값 오류로 본다.
			if (!wrapped.isObject()) {
				return ApplyResult.INVALID_VALUE;
			}
			for (String key : wrapped.propertyNames()) {
				if (!Symptom.PAIN.key().equals(key)) {
					return ApplyResult.INVALID_VALUE;
				}
			}
		}
		if (present(pain)) {
			// 4.0처럼 소수점이 붙은 값은 정수로 인정하지 않는다. 점수를 임의로 반올림하지 않기 위해서다.
			if (!pain.isInt()) {
				return pain.isNumber() ? ApplyResult.INVALID_SCORE : ApplyResult.INVALID_VALUE;
			}
			int score = pain.intValue();
			if (score < MIN_SCORE || score > MAX_SCORE) {
				return ApplyResult.INVALID_SCORE;
			}
			scores.put(Symptom.PAIN, score);
		}

		JsonNode signs = item.path("skinSigns");
		if (present(signs)) {
			if (!signs.isArray()) {
				return ApplyResult.INVALID_VALUE;
			}
			// 부위의 증상도 대화 중에 하나씩 말할 수 있으므로 기존 증상에 더한다.
			Set<SkinSign> merged = conditionSigns.computeIfAbsent(bodyPart, k -> EnumSet.noneOf(SkinSign.class));
			for (JsonNode sign : signs) {
				if (!sign.isString()) {
					return ApplyResult.INVALID_VALUE;
				}
				try {
					merged.add(SkinSign.valueOf(sign.asString().trim().toUpperCase(Locale.ROOT)));
				} catch (IllegalArgumentException e) {
					return ApplyResult.INVALID_VALUE;
				}
			}
		}
		return ApplyResult.OK;
	}

	/**
	 * 증상 다음에 고른 부위를 반영한다. 고통 지수·증상·부위는 한 묶음이라, 고른 부위마다 고통 지수를 그 부위의 고통 점수로,
	 * 방금 고른 증상을 그 부위의 증상으로 넣는다. (예: 홍반·가려움을 고르고 얼굴·왼손을 고르면 두 부위 모두 홍반·가려움)
	 * 고통 지수가 없거나 부위 이름이 하나라도 틀리면 아무것도 반영하지 않는다.
	 */
	ApplyResult applyBodyParts(JsonNode parts) {
		if (painIndex == null || parts == null || !parts.isArray() || parts.isEmpty()) {
			return ApplyResult.INVALID_VALUE;
		}
		ChatDraft next = copy();
		for (JsonNode part : parts) {
			if (!part.isString()) {
				return ApplyResult.INVALID_VALUE;
			}
			BodyPart bodyPart;
			try {
				bodyPart = BodyPart.valueOf(part.asString().trim().toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				return ApplyResult.INVALID_VALUE;
			}
			next.conditions.computeIfAbsent(bodyPart, k -> new EnumMap<>(Symptom.class)).put(Symptom.PAIN, painIndex);
			if (skinSigns != null && !skinSigns.isEmpty()) {
				next.conditionSigns.computeIfAbsent(bodyPart, k -> EnumSet.noneOf(SkinSign.class)).addAll(skinSigns);
			}
		}
		commit(next);
		return ApplyResult.OK;
	}

	// ── 진행 상황 ──

	/**
	 * 기록으로 저장하려면 꼭 필요한 항목이 모두 채워졌는지.
	 * 고통 지수가 있어야 하고, 사용자가 말한 부위가 있다면 그 부위마다 고통 점수가 있어야 한다.
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
		if (skinSigns == null && !skipped.contains(SKIP_SKIN_SIGNS)) {
			pending.add("skinSigns");
		}
		// 고통이 없고(0점) 나타난 증상도 없으면 부위는 묻지 않는다. 사용자가 이미 부위를 말했으면 다시 묻지 않는다.
		boolean painful = painIndex == null || painIndex > MIN_PAIN_INDEX;
		if ((painful || hasSkinSigns()) && conditions.isEmpty() && !skipped.contains(SKIP_BODY_PARTS)) {
			pending.add("bodyParts");
		}
		if (sleepHours == null && !skipped.contains(SKIP_SLEEP)) {
			pending.add("sleepHours");
		}
		if (medication == null && !skipped.contains(SKIP_MEDICATION)) {
			pending.add("medication");
		}
		if (notes == null && !skipped.contains(SKIP_NOTES)) {
			pending.add("notes");
		}
		return pending;
	}

	/** 나타난 증상을 하나 이상 골랐는지 */
	public boolean hasSkinSigns() {
		return skinSigns != null && !skinSigns.isEmpty();
	}

	/**
	 * 지금 물어볼 항목. 아직 입력되지 않은 항목 중 가장 먼저 필요한 것이고, 모두 채워졌으면 null이다.
	 * 챗봇이 부위나 부위 고통 점수를 되묻는 중이거나 점수가 빠진 부위가 있으면, 이어서 묻도록 고통 지수보다 먼저 symptoms다.
	 */
	public String askingField() {
		if (followUp || conditions.values().stream().anyMatch(Map::isEmpty)) {
			return "symptoms";
		}
		List<String> pending = pendingFields();
		return pending.isEmpty() ? null : pending.get(0);
	}

	/** 확인 단계로 넘어갈 수 있는지. 필수 항목이 채워졌고, 선택 항목도 끝났거나 사용자가 마치겠다고 한 경우 */
	public boolean readyForConfirmation(boolean userFinished) {
		return requiredSatisfied() && (userFinished || pendingOptionalFields().isEmpty());
	}

	// ── 화면·프롬프트용 변환 ──

	public ChatDraftResponse toResponse() {
		List<SkinConditionResponse> list = conditions.entrySet().stream()
			.sorted(Map.Entry.comparingByKey(Comparator.comparingInt(Enum::ordinal)))
			.map(e -> new SkinConditionResponse(e.getKey(), SymptomsResponse.from(e.getValue()),
				List.copyOf(conditionSigns.getOrDefault(e.getKey(), EnumSet.noneOf(SkinSign.class)))))
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

	/**
	 * 사람이 읽는 초안 요약(챗봇의 일일 총정리). 기록된 항목만 한 줄씩 보여 준다. (예: "- 고통 지수: 6점")
	 * 고통 지수는 한 줄로 따로 두고, 부위 줄에는 부위 고통 점수 없이 그 부위에 나타난 증상만 보여 준다. (예: "- 얼굴: 홍반, 건조증")
	 */
	public String summary() {
		StringJoiner lines = new StringJoiner("\n");
		if (painIndex != null) {
			lines.add("- 고통 지수: " + painIndex + "점");
		}
		conditions.keySet().stream()
			.sorted(Comparator.comparingInt(Enum::ordinal))
			.forEach(bodyPart -> {
				Set<SkinSign> signs = conditionSigns.getOrDefault(bodyPart, Set.of());
				lines.add("- " + bodyPart.label() + ": " + (signs.isEmpty() ? "증상 없음" : labels(signs)));
			});
		if (sleepHours != null) {
			lines.add("- 수면 시간: " + sleepHours.stripTrailingZeros().toPlainString() + "시간");
		}
		if (medication != null) {
			lines.add("- 약 복용: " + (medication ? "예" : "아니오"));
		}
		if (skinSigns != null) {
			lines.add("- 나타난 증상: " + (skinSigns.isEmpty() ? "없음" : labels(skinSigns)));
		}
		if (notes != null) {
			lines.add("- 특이사항: " + notes);
		}
		return lines.toString();
	}

	private static String labels(Set<SkinSign> signs) {
		return signs.stream().sorted().map(SkinSign::label).collect(Collectors.joining(", "));
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
		conditionSigns.forEach((bodyPart, signs) -> copy.conditionSigns.put(bodyPart, signs.isEmpty()
			? EnumSet.noneOf(SkinSign.class) : EnumSet.copyOf(signs)));
		copy.painIndex = painIndex;
		copy.sleepHours = sleepHours;
		copy.medication = medication;
		copy.skinSigns = skinSigns == null ? null : EnumSet.copyOf(skinSigns.isEmpty()
			? EnumSet.noneOf(SkinSign.class) : skinSigns);
		copy.alcohol = alcohol;
		copy.outdoorActivity = outdoorActivity;
		copy.notes = notes;
		copy.skipped.addAll(skipped);
		copy.followUp = followUp;
		copy.editing = editing;
		return copy;
	}

	private void commit(ChatDraft next) {
		conditions.clear();
		conditions.putAll(next.conditions);
		conditionSigns.clear();
		conditionSigns.putAll(next.conditionSigns);
		painIndex = next.painIndex;
		sleepHours = next.sleepHours;
		medication = next.medication;
		skinSigns = next.skinSigns;
		alcohol = next.alcohol;
		outdoorActivity = next.outdoorActivity;
		notes = next.notes;
		skipped.clear();
		skipped.addAll(next.skipped);
		followUp = next.followUp;
		editing = next.editing;
	}

}
