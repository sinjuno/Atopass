package com.atopy.Atopass.domain.chatbot.service;

import com.atopy.Atopass.domain.chatbot.dto.response.ChatChoiceSpec;
import com.atopy.Atopass.domain.record.entity.MedicationUse;
import com.atopy.Atopass.domain.record.entity.SkinSign;
import com.atopy.Atopass.domain.record.entity.SkinTrend;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 챗봇이 정해진 문장으로 묻는 질문과 선택지. (기획 시트 "필수 항목"·"연결 질문 풀" 기준)
 * 질문 문장은 LLM이 만들지 않고 여기서만 고른다.
 */
final class ChatQuestions {

	static final String CONFIRM = "입력하신 내용을 정리했어요.";

	/** 피부 모양 선택지와 기록할 증상. "괜찮음"은 하나도 없다는 뜻이다. */
	static final Map<String, SkinSign> SKIN_SHAPES = linked(
		"붉어짐", SkinSign.ERYTHEMA,
		"하얗게 일어남", SkinSign.XEROSIS,
		"오돌토돌", SkinSign.EDEMA,
		"두꺼워짐", SkinSign.LICHENIFICATION,
		"갈라짐", SkinSign.FISSURE);
	static final String SHAPE_NONE = "괜찮음";

	static final List<String> SWELLING = List.of("예", "아니요");
	static final List<String> SCRATCH = List.of("있음", "없음");
	static final List<String> DISCHARGE = List.of("진물", "피", "둘 다", "없음");
	static final List<String> SLEEP_DISTURBED = List.of("설쳤음", "괜찮았음");
	static final List<String> MOISTURIZER = List.of("발랐음", "안 바름");
	static final List<String> TYPED_CONFIRM = List.of("맞아요", "다시 적을게요");

	/** 글로 직접 적는 단계와 적은 글을 확인하는 단계는 연결 질문 ID 뒤에 이 말을 붙인 항목으로 묻는다. (예: C12_TEXT) */
	static final String TEXT_SUFFIX = "_TEXT";
	static final String CONFIRM_SUFFIX = "_CONFIRM";

	private ChatQuestions() {
	}

	/** 지금 물어볼 항목의 정해진 질문. 선택지로 답하는 항목은 화면에 같은 선택지가 함께 나온다. */
	static String question(ChatDraft draft, String field) {
		if (field == null) {
			return CONFIRM;
		}
		if (field.endsWith(TEXT_SUFFIX)) {
			return switch (baseId(field)) {
				case "C04" -> "새로 바른 제품 이름을 적어 주세요.";
				case "C12" -> "평소와 다르게 먹은 음식을 적어 주세요.";
				default -> "직접 적어 주세요.";
			};
		}
		if (field.endsWith(CONFIRM_SUFFIX)) {
			String label = LinkedQuestion.find(baseId(field)).map(LinkedQuestion::label).orElse("기록");
			return "%s: ‘%s’ 이렇게 기록할게요. 맞나요?".formatted(label, draft.typedText());
		}
		var linked = LinkedQuestion.find(field);
		if (linked.isPresent()) {
			return linked.get().text();
		}
		return switch (field) {
			case "skinTrend" -> "오늘 피부 상태는 어땠나요?";
			case "itchScore" -> "가려움은 어느 정도였나요? (0은 없음, 10은 매우 심함)";
			case "painIndex" -> "오늘 피부 때문에 느낀 고통은 0~10 중 몇 점인가요? (0은 없음, 10은 매우 심함)";
			case "bodyParts" -> "어디가 가장 불편했나요? 해당하는 부위를 모두 골라 주세요.";
			case "symptoms" -> "그 부위의 고통 정도를 0~10점으로 알려 주세요. (0은 없음, 10은 매우 심함)";
			case "skinShape" -> "그 부분을 보면 지금 어떤 상태예요? 해당하는 것을 모두 골라 주세요.";
			case "swelling" -> "붓거나 부은 느낌이 있나요?";
			case "scratch" -> "긁어서 생긴 상처가 있나요?";
			case "oozing" -> "진물이 나거나 피가 났나요?";
			case "sleepDisturbed" -> "가려워서 잠을 설쳤나요?";
			case "sleepHours" -> "어젯밤 몇 시간 주무셨나요?";
			case "moisturizer" -> "보습제는 발랐나요?";
			case "medicationUse" -> "처방받은 연고나 약을 사용했나요?";
			default -> "평소와 다른 점이 있으면 500자 이내로 적어 주세요. 없으면 '없어요'를 눌러 주세요.";
		};
	}

	/**
	 * 앞 답을 받아 주는 짧은 문장. 원인을 단정하거나 효과를 평가하지 않고 사실만 받는다. 없으면 null
	 */
	static String acknowledgement(String field, String answer) {
		return switch (field) {
			case "skinTrend" -> SkinTrend.WORSE.label().equals(answer) ? "평소보다 안 좋았군요."
				: SkinTrend.BETTER.label().equals(answer) ? "좋아졌다니 다행이에요." : null;
			case "C04" -> "있음(사진 올리기)".equals(answer)
				? "사진은 오늘 기록이 저장된 뒤 입력창 옆 사진 버튼으로 붙여 주세요." : null;
			default -> null;
		};
	}

	/** 화면이 버튼을 그릴 선택지. 화면이 따로 그리는 항목이면 null */
	static ChatChoiceSpec choice(String field) {
		if (field == null) {
			return null;
		}
		if (field.endsWith(TEXT_SUFFIX)) {
			return new ChatChoiceSpec(label(baseId(field)), List.of(), false, null, true);
		}
		if (field.endsWith(CONFIRM_SUFFIX)) {
			return single(label(baseId(field)), TYPED_CONFIRM);
		}
		var linked = LinkedQuestion.find(field);
		if (linked.isPresent()) {
			return single(linked.get().label(), linked.get().options());
		}
		return switch (field) {
			case "skinTrend" -> single("피부 상태", Arrays.stream(SkinTrend.values()).map(SkinTrend::label).toList());
			case "skinShape" -> new ChatChoiceSpec("피부 모양", List.copyOf(SKIN_SHAPES.keySet()), true, SHAPE_NONE, false);
			case "swelling" -> single("붓기", SWELLING);
			case "scratch" -> single("긁은 자국", SCRATCH);
			case "oozing" -> single("진물·피", DISCHARGE);
			case "sleepDisturbed" -> single("수면 방해", SLEEP_DISTURBED);
			case "moisturizer" -> single("보습제", MOISTURIZER);
			case "medicationUse" -> single("연고·약",
				Arrays.stream(MedicationUse.values()).map(MedicationUse::label).toList());
			default -> null;
		};
	}

	/** 선택지 버튼으로 받는 항목인지 (화면이 따로 그리는 항목 제외) */
	static boolean generic(String field) {
		return choice(field) != null;
	}

	/** C12_TEXT, C12_CONFIRM → C12 */
	static String baseId(String field) {
		int underscore = field.indexOf('_');
		return underscore < 0 ? field : field.substring(0, underscore);
	}

	private static String label(String linkedId) {
		return LinkedQuestion.find(linkedId).map(LinkedQuestion::label).orElse(linkedId);
	}

	private static ChatChoiceSpec single(String label, List<String> options) {
		return new ChatChoiceSpec(label, options, false, null, false);
	}

	private static Map<String, SkinSign> linked(Object... pairs) {
		Map<String, SkinSign> map = new LinkedHashMap<>();
		for (int i = 0; i < pairs.length; i += 2) {
			map.put((String) pairs[i], (SkinSign) pairs[i + 1]);
		}
		return Collections.unmodifiableMap(map);
	}

}
