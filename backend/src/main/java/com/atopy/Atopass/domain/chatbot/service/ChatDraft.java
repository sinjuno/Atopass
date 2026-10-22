package com.atopy.Atopass.domain.chatbot.service;

import com.atopy.Atopass.domain.chatbot.dto.response.ChatDraftResponse;
import com.atopy.Atopass.domain.record.dto.request.DailyCheckRequest;
import com.atopy.Atopass.domain.record.dto.request.LifestyleRequest;
import com.atopy.Atopass.domain.record.dto.request.RecordRequest;
import com.atopy.Atopass.domain.record.dto.request.SkinConditionRequest;
import com.atopy.Atopass.domain.record.dto.request.SymptomsRequest;
import com.atopy.Atopass.domain.record.dto.response.DailyCheckResponse;
import com.atopy.Atopass.domain.record.dto.response.LifestyleResponse;
import com.atopy.Atopass.domain.record.dto.response.SkinConditionResponse;
import com.atopy.Atopass.domain.record.dto.response.SymptomsResponse;
import com.atopy.Atopass.domain.record.entity.BodyPart;
import com.atopy.Atopass.domain.record.entity.MedicationUse;
import com.atopy.Atopass.domain.record.entity.OutdoorActivity;
import com.atopy.Atopass.domain.record.entity.SkinCondition;
import com.atopy.Atopass.domain.record.entity.SkinRecord;
import com.atopy.Atopass.domain.record.entity.SkinSign;
import com.atopy.Atopass.domain.record.entity.SkinTrend;
import com.atopy.Atopass.domain.record.entity.SleepScore;
import com.atopy.Atopass.domain.record.entity.Symptom;
import com.atopy.Atopass.domain.record.entity.SymptomScore;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.MissingNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 챗봇이 대화로 모아 가는 기록 초안. 사용자가 말한 값만 담고, 말하지 않은 값은 비워 둔다.
 * 세션에는 JSON으로 저장된다. LLM이 준 값은 {@link #apply}에서 모두 검증한 뒤에만 반영한다.
 *
 * <p>챗봇이 묻는 항목은 {@link #FLOW} 순서다: 오늘 피부 상태, 가려움 정도, 고통 지수(필수), 불편한 부위, 피부 모양, 붓기,
 * 긁은 자국, 진물·피, 수면 방해, 수면 시간, 보습제, 연고·약, 특이사항. (기획 시트 "필수 항목"에 기존 항목을 더한 것)
 * 피부 모양·붓기·긁은 자국·진물·피의 답은 나타난 증상(skinSigns)으로 기록하고, 가려움이 1점 이상이면 가려움 증상도 기록한다.
 * 음주·야외활동은 묻지 않지만, 사용자가 먼저 말하면 함께 기록한다.
 *
 * <p>항목 사이에는 앞 답에 맞는 연결 질문({@link LinkedQuestion})을 끼워 묻는다. 선택 규칙:
 * 나빠진 날은 최대 3개(우선순위 낮음 제외), 그 밖의 날은 1개. 부위 질문은 하루 1개. 계절 조건은 기록 날짜의 달로 본다.
 * 연결 질문 답이 뒤 항목을 대신하면(예: "자다가 긁음" → 수면 방해) 그 항목은 미리 채워 묻지 않는다.
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
		SKIP_BODY_PARTS, SKIP_SLEEP, SKIP_MEDICATION, SKIP_SKIN_SIGNS, SKIP_LIFESTYLE, SKIP_NOTES,
		"skinTrend", "itchScore", "skinShape", "swelling", "scratch", "oozing", "sleepDisturbed", "moisturizer",
		"medicationUse");

	/** 묻는 순서. 부위의 고통 점수(symptoms)는 부위를 고른 뒤 필요할 때만 되묻는다. */
	static final List<String> FLOW = List.of("skinTrend", "itchScore", "painIndex", "bodyParts", "skinShape", "swelling",
		"scratch", "oozing", "sleepDisturbed", "sleepHours", "moisturizer", "medicationUse", "notes");

	/** 각 항목 바로 뒤에 묻는 연결 질문 후보. 앞에 있는 것부터 조건을 본다. */
	private static final Map<String, List<LinkedQuestion>> LINKED_AFTER = Map.of(
		"skinTrend", List.of(LinkedQuestion.C01, LinkedQuestion.C14, LinkedQuestion.C16),
		"itchScore", List.of(LinkedQuestion.C02, LinkedQuestion.C11, LinkedQuestion.C12),
		"bodyParts", List.of(LinkedQuestion.C04, LinkedQuestion.C13, LinkedQuestion.C03, LinkedQuestion.C05,
			LinkedQuestion.C15, LinkedQuestion.C11),
		"skinShape", List.of(LinkedQuestion.C06, LinkedQuestion.C07),
		"scratch", List.of(LinkedQuestion.C08),
		"oozing", List.of(LinkedQuestion.C09),
		"sleepDisturbed", List.of(LinkedQuestion.C10));

	/** 부위 질문. 부위가 여러 곳이어도 하루에 하나만 묻는다. */
	private static final Set<LinkedQuestion> PART_QUESTIONS = EnumSet.of(
		LinkedQuestion.C03, LinkedQuestion.C04, LinkedQuestion.C05, LinkedQuestion.C13, LinkedQuestion.C15);
	/** 나빠진 날에 묻는 연결 질문 수. 그 밖의 날은 하나다. */
	private static final int WORSE_DAY_LINKED = 3;
	private static final int OTHER_DAY_LINKED = 1;
	/** 이 점수 이상이면 가려움이 심해진 순간을 묻는다. */
	private static final int HIGH_ITCH = 6;
	/** 직접 적는 연결 질문 답의 최대 글자 수 (기록 칸 크기) */
	static final int MAX_TYPED_LENGTH = 200;

	private static final Set<BodyPart> HANDS = EnumSet.of(BodyPart.RIGHT_HAND, BodyPart.LEFT_HAND, BodyPart.HAND);
	private static final Set<BodyPart> TRUNK_AND_ARMS = EnumSet.of(BodyPart.TRUNK, BodyPart.BACK, BodyPart.CHEST,
		BodyPart.ABDOMEN, BodyPart.RIGHT_UPPER_ARM, BodyPart.RIGHT_FOREARM, BodyPart.LEFT_UPPER_ARM,
		BodyPart.LEFT_FOREARM, BodyPart.ARM);

	/** 진물·피 답 */
	enum Discharge {
		NONE, OOZING, BLOOD, BOTH
	}

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
	private SkinTrend skinTrend;
	private Integer itchScore;
	/** 피부 모양을 답했는지. "괜찮음"이면 증상 없이 true다. */
	private boolean shapeAnswered;
	private Boolean swelling;
	private Boolean scratch;
	private Discharge discharge;
	private Boolean sleepDisturbed;
	private Boolean moisturizer;
	private MedicationUse medicationUse;
	/** 연결 질문 답. 질문 ID → 고른 선택지 또는 직접 쓴 글 */
	private final Map<String, String> linkedAnswers = new LinkedHashMap<>();
	/** 글로 직접 적는 중인 연결 질문과, 확인을 기다리는 글 */
	private String typingQuestion;
	private String typedText;
	/** 계절 조건을 보는 기록 날짜. 서비스가 턴마다 알려 준다. */
	private LocalDate recordDate;
	/** 최근 7일 기록에서 물었던 연결 질문 */
	private final Set<String> recentLinked = new LinkedHashSet<>();
	/** 건너뛴 항목과 건너뛴 연결 질문 ID */
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
		draft.skinTrend = record.getSkinTrend();
		draft.itchScore = record.getItchScore();
		draft.sleepDisturbed = record.getSleepDisturbed();
		draft.moisturizer = record.getMoisturizer();
		draft.medicationUse = record.getMedicationUse();
		draft.linkedAnswers.putAll(new TreeMap<>(record.getLinkedAnswers()));
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
		if (skinTrend != null) {
			root.put("skinTrend", skinTrend.name());
		}
		if (itchScore != null) {
			root.put("itchScore", itchScore);
		}
		if (shapeAnswered) {
			root.put("shapeAnswered", true);
		}
		putIfPresent(root, "swelling", swelling);
		putIfPresent(root, "scratch", scratch);
		if (discharge != null) {
			root.put("discharge", discharge.name());
		}
		putIfPresent(root, "sleepDisturbed", sleepDisturbed);
		putIfPresent(root, "moisturizer", moisturizer);
		if (medicationUse != null) {
			root.put("medicationUse", medicationUse.name());
		}
		ObjectNode linkedNode = root.putObject("linkedAnswers");
		linkedAnswers.forEach(linkedNode::put);
		if (typingQuestion != null) {
			root.put("typingQuestion", typingQuestion);
		}
		if (typedText != null) {
			root.put("typedText", typedText);
		}
		if (recordDate != null) {
			root.put("recordDate", recordDate.toString());
		}
		ArrayNode recentNode = root.putArray("recentLinked");
		recentLinked.forEach(recentNode::add);
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
			if (root.path("skinTrend").isString()) {
				draft.skinTrend = SkinTrend.valueOf(root.path("skinTrend").asString());
			}
			if (root.path("itchScore").isInt()) {
				draft.itchScore = root.path("itchScore").intValue();
			}
			draft.shapeAnswered = root.path("shapeAnswered").asBoolean(false);
			draft.swelling = readBoolean(root, "swelling");
			draft.scratch = readBoolean(root, "scratch");
			if (root.path("discharge").isString()) {
				draft.discharge = Discharge.valueOf(root.path("discharge").asString());
			}
			draft.sleepDisturbed = readBoolean(root, "sleepDisturbed");
			draft.moisturizer = readBoolean(root, "moisturizer");
			if (root.path("medicationUse").isString()) {
				draft.medicationUse = MedicationUse.valueOf(root.path("medicationUse").asString());
			}
			for (Map.Entry<String, JsonNode> entry : root.path("linkedAnswers").properties()) {
				if (entry.getValue().isString()) {
					draft.linkedAnswers.put(entry.getKey(), entry.getValue().asString());
				}
			}
			if (root.path("typingQuestion").isString()) {
				draft.typingQuestion = root.path("typingQuestion").asString();
			}
			if (root.path("typedText").isString()) {
				draft.typedText = root.path("typedText").asString();
			}
			if (root.path("recordDate").isString()) {
				draft.recordDate = LocalDate.parse(root.path("recordDate").asString());
			}
			for (JsonNode item : root.path("recentLinked")) {
				if (item.isString()) {
					draft.recentLinked.add(item.asString());
				}
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

	private static void putIfPresent(ObjectNode root, String key, Boolean value) {
		if (value != null) {
			root.put(key, value);
		}
	}

	private static Boolean readBoolean(JsonNode root, String key) {
		return root.path(key).isBoolean() ? root.path(key).booleanValue() : null;
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

		JsonNode trend = updates.path("skinTrend");
		if (present(trend)) {
			SkinTrend value = enumValue(SkinTrend.class, trend);
			if (value == null) {
				return ApplyResult.INVALID_VALUE;
			}
			next.skinTrend = value;
		}

		JsonNode itch = updates.path("itchScore");
		if (present(itch)) {
			if (!itch.isInt()) {
				return itch.isNumber() ? ApplyResult.INVALID_SCORE : ApplyResult.INVALID_VALUE;
			}
			if (itch.intValue() < MIN_SCORE || itch.intValue() > MAX_SCORE) {
				return ApplyResult.INVALID_SCORE;
			}
			next.setItchScore(itch.intValue());
		}

		for (String key : List.of("swelling", "scratch", "sleepDisturbed", "moisturizer")) {
			JsonNode node = updates.path(key);
			if (!present(node)) {
				continue;
			}
			if (!node.isBoolean()) {
				return ApplyResult.INVALID_VALUE;
			}
			switch (key) {
				case "swelling" -> next.setSwelling(node.booleanValue());
				case "scratch" -> next.setScratch(node.booleanValue());
				case "sleepDisturbed" -> next.sleepDisturbed = node.booleanValue();
				default -> next.moisturizer = node.booleanValue();
			}
		}

		JsonNode dischargeNode = updates.path("oozing");
		if (present(dischargeNode)) {
			Discharge value = enumValue(Discharge.class, dischargeNode);
			if (value == null) {
				return ApplyResult.INVALID_VALUE;
			}
			next.setDischarge(value);
		}

		JsonNode medicationUseNode = updates.path("medicationUse");
		if (present(medicationUseNode)) {
			MedicationUse value = enumValue(MedicationUse.class, medicationUseNode);
			if (value == null) {
				return ApplyResult.INVALID_VALUE;
			}
			next.setMedicationUse(value);
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

	/** "넘어갈게요"로 건너뛸 수 있는 항목인지. 연결 질문(직접 적기·확인 단계 포함)은 모두 건너뛸 수 있다. */
	static boolean skippable(String field) {
		return SKIPPABLE.contains(field) || LinkedQuestion.find(ChatQuestions.baseId(field)).isPresent();
	}

	/** "넘어갈게요". 연결 질문을 직접 적다가 넘기면 그 질문을 건너뛴 것으로 본다. */
	ApplyResult skip(String field) {
		if (!skippable(field)) {
			return ApplyResult.INVALID_VALUE;
		}
		String id = ChatQuestions.baseId(field);
		if (LinkedQuestion.find(id).isPresent()) {
			skipped.add(id);
			if (id.equals(typingQuestion)) {
				typingQuestion = null;
				typedText = null;
			}
		} else {
			skipped.add(field);
		}
		return ApplyResult.OK;
	}

	/**
	 * 선택지 버튼으로 고른 답을 반영한다. (피부 상태, 피부 모양, 붓기, 긁은 자국, 진물·피, 수면 방해, 보습제, 연고·약, 연결 질문)
	 * value는 고른 선택지 글자이고, 피부 모양은 글자 배열("괜찮음"이면 빈 배열)이다. 연결 질문을 직접 적는 단계는 적은 글이다.
	 * 선택지에 없는 값이면 아무것도 반영하지 않는다.
	 */
	ApplyResult answer(String field, JsonNode value) {
		ChatDraft next = copy();
		ApplyResult result = next.answerInPlace(field, value == null ? MissingNode.getInstance() : value);
		if (result == ApplyResult.OK) {
			commit(next);
		}
		return result;
	}

	private ApplyResult answerInPlace(String field, JsonNode value) {
		if (field.endsWith(ChatQuestions.TEXT_SUFFIX) || field.endsWith(ChatQuestions.CONFIRM_SUFFIX)) {
			return answerTyped(field, value);
		}
		Optional<LinkedQuestion> linked = LinkedQuestion.find(field);
		if (linked.isPresent()) {
			String choice = value.isString() ? value.asString() : null;
			if (choice == null || !linked.get().options().contains(choice)) {
				return ApplyResult.INVALID_VALUE;
			}
			if (linked.get().asksFreeText(choice)) {
				typingQuestion = field;
				typedText = null;
			} else {
				linkedAnswers.put(field, choice);
				prefill(linked.get(), choice);
			}
			return ApplyResult.OK;
		}
		if ("skinShape".equals(field)) {
			if (!value.isArray()) {
				return ApplyResult.INVALID_VALUE;
			}
			Set<SkinSign> chosen = EnumSet.noneOf(SkinSign.class);
			for (JsonNode item : value) {
				String text = item.isString() ? item.asString() : null;
				if (ChatQuestions.SHAPE_NONE.equals(text)) {
					continue;
				}
				SkinSign sign = text == null ? null : ChatQuestions.SKIN_SHAPES.get(text);
				if (sign == null) {
					return ApplyResult.INVALID_VALUE;
				}
				chosen.add(sign);
			}
			if (!chosen.isEmpty()) {
				signs().addAll(chosen);
			}
			shapeAnswered = true;
			return ApplyResult.OK;
		}

		String choice = value.isString() ? value.asString() : null;
		int index = switch (field) {
			case "skinTrend" -> indexOf(Arrays.stream(SkinTrend.values()).map(SkinTrend::label).toList(), choice);
			case "swelling" -> indexOf(ChatQuestions.SWELLING, choice);
			case "scratch" -> indexOf(ChatQuestions.SCRATCH, choice);
			case "oozing" -> indexOf(ChatQuestions.DISCHARGE, choice);
			case "sleepDisturbed" -> indexOf(ChatQuestions.SLEEP_DISTURBED, choice);
			case "moisturizer" -> indexOf(ChatQuestions.MOISTURIZER, choice);
			case "medicationUse" -> indexOf(Arrays.stream(MedicationUse.values()).map(MedicationUse::label).toList(), choice);
			default -> -1;
		};
		if (index < 0) {
			return ApplyResult.INVALID_VALUE;
		}
		switch (field) {
			case "skinTrend" -> skinTrend = SkinTrend.values()[index];
			case "swelling" -> setSwelling(index == 0);
			case "scratch" -> setScratch(index == 0);
			case "oozing" -> setDischarge(
				new Discharge[] {Discharge.OOZING, Discharge.BLOOD, Discharge.BOTH, Discharge.NONE}[index]);
			case "sleepDisturbed" -> sleepDisturbed = index == 0;
			case "moisturizer" -> moisturizer = index == 0;
			default -> setMedicationUse(MedicationUse.values()[index]);
		}
		return ApplyResult.OK;
	}

	/** 연결 질문을 글로 직접 적는 단계(적은 글)와, 적은 글이 맞는지 확인하는 단계("맞아요/다시 적을게요") */
	private ApplyResult answerTyped(String field, JsonNode value) {
		String id = ChatQuestions.baseId(field);
		if (!id.equals(typingQuestion) || !value.isString()) {
			return ApplyResult.INVALID_VALUE;
		}
		if (field.endsWith(ChatQuestions.TEXT_SUFFIX)) {
			String text = value.asString().trim();
			if (typedText != null || text.isEmpty() || text.length() > MAX_TYPED_LENGTH) {
				return ApplyResult.INVALID_VALUE;
			}
			typedText = text;
			return ApplyResult.OK;
		}
		int index = indexOf(ChatQuestions.TYPED_CONFIRM, value.asString());
		if (typedText == null || index < 0) {
			return ApplyResult.INVALID_VALUE;
		}
		if (index == 0) {
			linkedAnswers.put(id, typedText);
			typingQuestion = null;
		}
		typedText = null;
		return ApplyResult.OK;
	}

	/** 연결 질문 답이 뒤의 필수 항목을 대신하면 그 항목을 미리 채운다. 이미 답한 항목은 바꾸지 않는다. */
	private void prefill(LinkedQuestion question, String choice) {
		switch (question) {
			case C06 -> {
				if (moisturizer == null) {
					moisturizer = !"안 바름".equals(choice);
				}
			}
			case C07 -> {
				if (swelling == null) {
					setSwelling("예".equals(choice));
				}
			}
			case C08 -> {
				if (sleepDisturbed == null && "자다가 긁음".equals(choice)) {
					sleepDisturbed = true;
				}
			}
			case C09 -> {
				// 자려고 누웠을 때 가장 신경 쓰였고 아침에 처음 봤다면 자는 동안 긁은 것으로 본다.
				if (sleepDisturbed == null && "아침에 일어나서".equals(choice)
					&& "자려고 누웠을 때".equals(linkedAnswers.get(LinkedQuestion.C01.name()))) {
					sleepDisturbed = true;
				}
			}
			default -> {
			}
		}
	}

	private static int indexOf(List<String> options, String choice) {
		return choice == null ? -1 : options.indexOf(choice);
	}

	private static <E extends Enum<E>> E enumValue(Class<E> type, JsonNode node) {
		if (!node.isString()) {
			return null;
		}
		try {
			return Enum.valueOf(type, node.asString().trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private Set<SkinSign> signs() {
		if (skinSigns == null) {
			skinSigns = EnumSet.noneOf(SkinSign.class);
		}
		return skinSigns;
	}

	/** 가려움이 1점 이상이면 가려움 증상도 기록한다. */
	private void setItchScore(int score) {
		itchScore = score;
		if (score > MIN_SCORE) {
			signs().add(SkinSign.ITCHING);
		}
	}

	private void setSwelling(boolean value) {
		swelling = value;
		if (value) {
			signs().add(SkinSign.EDEMA);
		}
	}

	private void setScratch(boolean value) {
		scratch = value;
		if (value) {
			signs().add(SkinSign.EXCORIATION);
		}
	}

	private void setDischarge(Discharge value) {
		discharge = value;
		if (value == Discharge.OOZING || value == Discharge.BOTH) {
			signs().add(SkinSign.OOZING);
		}
		if (value == Discharge.BLOOD || value == Discharge.BOTH) {
			signs().add(SkinSign.BLEEDING);
		}
	}

	/** 연고·약 사용 여부에 맞춰 약 복용 여부도 정한다. */
	private void setMedicationUse(MedicationUse value) {
		medicationUse = value;
		medication = value == MedicationUse.USED;
	}

	/** 계절 조건과 최근 기록을 보기 위한 정보. 서비스가 턴마다 알려 준다. */
	void context(LocalDate date, Set<String> recentLinkedIds) {
		this.recordDate = date;
		recentLinked.clear();
		recentLinked.addAll(recentLinkedIds);
	}

	/** 확인을 기다리는, 직접 적은 연결 질문 답 */
	String typedText() {
		return typedText;
	}

	/** 연결 질문 답을 글로 적어 주길 기다리는 중인지. 이때 입력창의 글은 LLM을 거치지 않고 그대로 답이 된다. */
	boolean awaitingText() {
		return typingQuestion != null && typedText == null && !editing;
	}

	/** 저장된 기록을 고치는 중인지 */
	public boolean editing() {
		return editing;
	}

	/** 기록을 저장했으므로 이제부터는 빈 항목을 묻지 않고 고칠 내용을 받는다. */
	void startEditing() {
		this.editing = true;
		skipped.addAll(SKIPPABLE);
		typingQuestion = null;
		typedText = null;
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
		DailyCheckRequest dailyCheck = new DailyCheckRequest(skinTrend, itchScore, sleepDisturbed, moisturizer,
			medicationUse, new LinkedHashMap<>(linkedAnswers));
		return new RecordRequest(recordDate, list, painIndex, sleepHours, medication,
			skinSigns == null ? List.of() : List.copyOf(skinSigns), lifestyle, notes, dailyCheck);
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
	 * 고통 지수 다음에 고른 부위를 반영한다. 고통 지수와 부위는 한 묶음이라, 고른 부위마다 고통 지수를 그 부위의 고통 점수로 넣는다.
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

	/** 아직 입력되지 않은 항목을 묻는 순서대로 알려 준다. 점수가 빠진 부위가 있으면 고통 지수 다음에 symptoms가 온다. */
	public List<String> pendingFields() {
		List<String> pending = new ArrayList<>();
		for (String field : FLOW) {
			if (pending(field)) {
				pending.add(field);
			}
			if ("painIndex".equals(field) && conditions.values().stream().anyMatch(Map::isEmpty)) {
				pending.add("symptoms");
			}
		}
		return pending;
	}

	/**
	 * 지금 물어볼 항목. 아직 입력되지 않은 항목이나 조건에 맞는 연결 질문 중 가장 먼저 물을 것이고, 모두 끝났으면 null이다.
	 * 챗봇이 부위나 부위 고통 점수를 되묻는 중이거나 점수가 빠진 부위가 있으면, 이어서 묻도록 symptoms다.
	 */
	public String askingField() {
		if (followUp || conditions.values().stream().anyMatch(Map::isEmpty)) {
			return "symptoms";
		}
		return nextStep();
	}

	/** 확인 단계로 넘어갈 수 있는지. 필수 항목이 채워졌고, 물을 것이 끝났거나 사용자가 마치겠다고 한 경우 */
	public boolean readyForConfirmation(boolean userFinished) {
		return requiredSatisfied() && (userFinished || nextStep() == null);
	}

	private String nextStep() {
		if (typingQuestion != null && !editing) {
			return typingQuestion + (typedText == null ? ChatQuestions.TEXT_SUFFIX : ChatQuestions.CONFIRM_SUFFIX);
		}
		for (String field : FLOW) {
			if (pending(field)) {
				return field;
			}
			for (LinkedQuestion question : LINKED_AFTER.getOrDefault(field, List.of())) {
				if (shouldAsk(question)) {
					return question.name();
				}
			}
		}
		return null;
	}

	private boolean pending(String field) {
		if (skipped.contains(field)) {
			return false;
		}
		return switch (field) {
			case "skinTrend" -> skinTrend == null;
			case "itchScore" -> itchScore == null;
			case "painIndex" -> painIndex == null;
			// 사용자가 이미 부위를 말했으면 다시 묻지 않는다.
			case "bodyParts" -> conditions.isEmpty() && bodyPartsNeeded();
			// 고른 부위가 없으면 피부 모양은 묻지 않는다.
			case "skinShape" -> !conditions.isEmpty() && !shapeAnswered && !hasAny(ChatQuestions.SKIN_SHAPES.values());
			case "swelling" -> swelling == null;
			case "scratch" -> scratch == null;
			// 긁은 자국이 없다고 하면 진물·피는 묻지 않는다.
			case "oozing" -> discharge == null && !Boolean.FALSE.equals(scratch);
			case "sleepDisturbed" -> sleepDisturbed == null;
			case "sleepHours" -> sleepHours == null;
			case "moisturizer" -> moisturizer == null;
			case "medicationUse" -> medicationUse == null && medication == null;
			case "notes" -> notes == null;
			default -> false;
		};
	}

	/** 고통도 가려움도 없거나, 피부가 좋아졌고 가렵지 않으면 부위는 묻지 않는다. */
	private boolean bodyPartsNeeded() {
		boolean itchy = itchScore != null && itchScore > MIN_SCORE;
		if (skinTrend == SkinTrend.BETTER && !itchy && itchScore != null) {
			return false;
		}
		return painIndex == null || painIndex > MIN_PAIN_INDEX || itchy;
	}

	/** 연결 질문 선택 규칙: 아직 묻지 않았고, 오늘 물을 개수가 남았고, 우선순위·부위 규칙에 맞고, 출현 조건에 맞는지 */
	private boolean shouldAsk(LinkedQuestion question) {
		if (editing || asked(question)) {
			return false;
		}
		long askedCount = Arrays.stream(LinkedQuestion.values()).filter(this::asked).count();
		if (askedCount >= (skinTrend == SkinTrend.WORSE ? WORSE_DAY_LINKED : OTHER_DAY_LINKED)) {
			return false;
		}
		// 나빠진 날은 원인 탐색형을 묻고, 순환형(우선순위 낮음)은 묻지 않는다.
		if (skinTrend == SkinTrend.WORSE && question.priority() == LinkedQuestion.Priority.LOW) {
			return false;
		}
		if (PART_QUESTIONS.contains(question) && PART_QUESTIONS.stream().anyMatch(this::asked)) {
			return false;
		}
		return appears(question);
	}

	private boolean asked(LinkedQuestion question) {
		return linkedAnswers.containsKey(question.name()) || skipped.contains(question.name());
	}

	/** 연결 질문의 출현 조건 (기획 시트 "출현 조건" 열) */
	private boolean appears(LinkedQuestion question) {
		String worstMoment = linkedAnswers.get(LinkedQuestion.C02.name());
		return switch (question) {
			case C01 -> skinTrend == SkinTrend.WORSE;
			case C02 -> itchScore != null && itchScore >= HIGH_ITCH;
			case C03 -> conditions.containsKey(BodyPart.NECK);
			// 얼굴은 최근 7일 안에 새 제품(C04)을 물었으면 마스크(C13)를 대신 묻는다.
			case C04 -> conditions.containsKey(BodyPart.FACE) && !recentLinked.contains(LinkedQuestion.C04.name());
			case C13 -> conditions.containsKey(BodyPart.FACE) && recentLinked.contains(LinkedQuestion.C04.name());
			case C05 -> conditions.keySet().stream().anyMatch(HANDS::contains);
			case C06 -> hasAny(EnumSet.of(SkinSign.XEROSIS, SkinSign.FISSURE));
			case C07 -> hasAny(EnumSet.of(SkinSign.ERYTHEMA));
			case C08 -> Boolean.TRUE.equals(scratch);
			case C09 -> discharge != null && discharge != Discharge.NONE;
			case C10 -> Boolean.TRUE.equals(sleepDisturbed);
			case C11 -> "운동 후".equals(worstMoment) || "더운 곳에서".equals(worstMoment)
				|| "땀".equals(linkedAnswers.get(LinkedQuestion.C03.name()));
			case C12 -> "밥 먹고".equals(worstMoment);
			case C14 -> skinTrend == SkinTrend.SAME && inMonths(12, 1, 2);
			case C15 -> conditions.keySet().stream().anyMatch(TRUNK_AND_ARMS::contains) && inMonths(9, 10, 11, 12, 1, 2);
			case C16 -> skinTrend == SkinTrend.SAME && inMonths(6, 7, 8);
		};
	}

	private boolean hasAny(Collection<SkinSign> wanted) {
		return skinSigns != null && wanted.stream().anyMatch(skinSigns::contains);
	}

	private boolean inMonths(int... months) {
		return recordDate != null && Arrays.stream(months).anyMatch(m -> m == recordDate.getMonthValue());
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
			notes,
			new DailyCheckResponse(skinTrend, itchScore, sleepDisturbed, moisturizer, medicationUse,
				new TreeMap<>(linkedAnswers)));
	}

	/** 사람이 읽는 초안 요약. 기록된 항목만 묻는 순서대로 한 줄씩 보여 준다. (예: "- 고통 지수: 6점") */
	public String summary() {
		StringJoiner lines = new StringJoiner("\n");
		if (skinTrend != null) {
			lines.add("- 피부 상태: " + skinTrend.label());
		}
		if (itchScore != null) {
			lines.add("- 가려움: " + itchScore + "점");
		}
		if (painIndex != null) {
			lines.add("- 고통 지수: " + painIndex + "점");
		}
		conditions.entrySet().stream()
			.sorted(Map.Entry.comparingByKey(Comparator.comparingInt(Enum::ordinal)))
			.forEach(e -> {
				Integer pain = e.getValue().get(Symptom.PAIN);
				Set<SkinSign> signs = conditionSigns.getOrDefault(e.getKey(), Set.of());
				StringBuilder line = new StringBuilder("- " + e.getKey().label());
				if (pain != null) {
					line.append(": 고통 ").append(pain).append("점");
				}
				if (!signs.isEmpty()) {
					line.append(pain != null ? ", " : ": ").append(labels(signs));
				}
				lines.add(line);
			});
		if (skinSigns != null) {
			lines.add("- 나타난 증상: " + (skinSigns.isEmpty() ? "없음" : labels(skinSigns)));
		}
		if (sleepDisturbed != null) {
			lines.add("- 수면 방해: " + (sleepDisturbed ? "설쳤음" : "괜찮았음"));
		}
		if (sleepHours != null) {
			lines.add("- 수면 시간: " + sleepHours.stripTrailingZeros().toPlainString() + "시간");
		}
		if (moisturizer != null) {
			lines.add("- 보습제: " + (moisturizer ? "발랐음" : "안 바름"));
		}
		if (medicationUse != null) {
			lines.add("- 연고·약: " + medicationUse.label());
		} else if (medication != null) {
			lines.add("- 약 복용: " + (medication ? "예" : "아니오"));
		}
		new TreeMap<>(linkedAnswers).forEach((id, answer) -> lines.add(
			"- " + LinkedQuestion.find(id).map(LinkedQuestion::label).orElse(id) + ": " + answer));
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
		copy.skinTrend = skinTrend;
		copy.itchScore = itchScore;
		copy.shapeAnswered = shapeAnswered;
		copy.swelling = swelling;
		copy.scratch = scratch;
		copy.discharge = discharge;
		copy.sleepDisturbed = sleepDisturbed;
		copy.moisturizer = moisturizer;
		copy.medicationUse = medicationUse;
		copy.linkedAnswers.putAll(linkedAnswers);
		copy.typingQuestion = typingQuestion;
		copy.typedText = typedText;
		copy.recordDate = recordDate;
		copy.recentLinked.addAll(recentLinked);
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
		skinTrend = next.skinTrend;
		itchScore = next.itchScore;
		shapeAnswered = next.shapeAnswered;
		swelling = next.swelling;
		scratch = next.scratch;
		discharge = next.discharge;
		sleepDisturbed = next.sleepDisturbed;
		moisturizer = next.moisturizer;
		medicationUse = next.medicationUse;
		linkedAnswers.clear();
		linkedAnswers.putAll(next.linkedAnswers);
		typingQuestion = next.typingQuestion;
		typedText = next.typedText;
		recordDate = next.recordDate;
		recentLinked.clear();
		recentLinked.addAll(next.recentLinked);
		skipped.clear();
		skipped.addAll(next.skipped);
		followUp = next.followUp;
		editing = next.editing;
	}

}
