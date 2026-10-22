package com.atopy.Atopass.domain.chatbot.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.atopy.Atopass.domain.chatbot.service.ChatDraft.ApplyResult;
import com.atopy.Atopass.domain.record.entity.BodyPart;
import com.atopy.Atopass.domain.record.entity.MedicationUse;
import com.atopy.Atopass.domain.record.entity.OutdoorActivity;
import com.atopy.Atopass.domain.record.entity.SkinSign;
import com.atopy.Atopass.domain.record.entity.SkinTrend;
import com.atopy.Atopass.domain.record.entity.Symptom;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ChatDraftTest {

	private final JsonMapper mapper = JsonMapper.builder().build();

	private JsonNode json(String text) {
		return mapper.readTree(text);
	}

	/** 기록 날짜가 봄이라 계절 질문이 나오지 않는 초안 */
	private ChatDraft spring() {
		ChatDraft draft = ChatDraft.empty();
		draft.context(LocalDate.of(2026, 4, 15), Set.of());
		return draft;
	}

	private ApplyResult answer(ChatDraft draft, String field, Object value) {
		return draft.answer(field, mapper.valueToTree(value));
	}

	@Test
	void 처음에는_피부_상태부터_묻는_순서로_항목이_남아_있다() {
		ChatDraft draft = ChatDraft.empty();

		// 고른 부위가 없으면 피부 모양은 묻지 않는다.
		assertThat(draft.pendingFields()).containsExactly("skinTrend", "itchScore", "painIndex", "bodyParts", "swelling",
			"scratch", "oozing", "sleepDisturbed", "sleepHours", "moisturizer", "medicationUse", "notes");
		assertThat(draft.askingField()).isEqualTo("skinTrend");
		assertThat(draft.requiredSatisfied()).isFalse();
		assertThat(draft.readyForConfirmation(true)).isFalse();
	}

	@Test
	void 고통_지수를_반영하면_필수_항목이_채워진다() {
		ChatDraft draft = ChatDraft.empty();

		assertThat(draft.apply(json("{\"painIndex\": 6}"))).isEqualTo(ApplyResult.OK);

		assertThat(draft.toResponse().painIndex()).isEqualTo(6);
		assertThat(draft.requiredSatisfied()).isTrue();
		assertThat(draft.pendingFields()).doesNotContain("painIndex").contains("bodyParts");
	}

	@Test
	void 고통_지수는_0에서_10_정수만_허용한다() {
		assertThat(ChatDraft.empty().apply(json("{\"painIndex\": -1}"))).isEqualTo(ApplyResult.INVALID_PAIN_INDEX);
		assertThat(ChatDraft.empty().apply(json("{\"painIndex\": 11}"))).isEqualTo(ApplyResult.INVALID_PAIN_INDEX);
		assertThat(ChatDraft.empty().apply(json("{\"painIndex\": 4.5}"))).isEqualTo(ApplyResult.INVALID_PAIN_INDEX);
		assertThat(ChatDraft.empty().apply(json("{\"painIndex\": \"많이\"}"))).isEqualTo(ApplyResult.INVALID_VALUE);
		assertThat(ChatDraft.empty().apply(json("{\"painIndex\": 0}"))).isEqualTo(ApplyResult.OK);
		assertThat(ChatDraft.empty().apply(json("{\"painIndex\": 10}"))).isEqualTo(ApplyResult.OK);
	}

	@Test
	void 수면_시간으로_수면_점수를_계산한다() {
		ChatDraft draft = ChatDraft.empty();

		draft.apply(json("{\"sleepHours\": 4}"));

		assertThat(draft.toResponse().sleepScore()).isEqualTo(3);
		assertThat(ChatDraft.empty().toResponse().sleepScore()).isNull();
	}

	@Test
	void 약_복용_여부는_예_아니오만_받는다() {
		ChatDraft draft = ChatDraft.empty();

		assertThat(draft.apply(json("{\"medication\": false}"))).isEqualTo(ApplyResult.OK);
		assertThat(draft.apply(json("{\"medication\": \"예\"}"))).isEqualTo(ApplyResult.INVALID_VALUE);
		assertThat(draft.toResponse().medication()).isFalse();
		// 약 복용 여부를 글로 말했으면 연고·약도 다시 묻지 않는다.
		assertThat(draft.pendingFields()).doesNotContain("medicationUse");
	}

	@Test
	void 나타난_증상은_대화에_걸쳐_합쳐지고_정의된_순서로_내려간다() {
		ChatDraft draft = ChatDraft.empty();

		draft.apply(json("{\"skinSigns\": [\"lichenification\"]}"));
		draft.apply(json("{\"skinSigns\": [\"XEROSIS\", \"LICHENIFICATION\"]}"));

		assertThat(draft.toResponse().skinSigns()).containsExactly(SkinSign.LICHENIFICATION, SkinSign.XEROSIS);
		assertThat(draft.pendingFields()).doesNotContain("skinSigns");
	}

	@Test
	void 증상이_없다고_하면_빈_목록으로_기록하고_더_묻지_않는다() {
		ChatDraft draft = ChatDraft.empty();

		draft.apply(json("{\"skinSigns\": []}"));

		assertThat(draft.toResponse().skinSigns()).isEmpty();
		assertThat(draft.pendingFields()).doesNotContain("skinSigns");
		assertThat(ChatDraft.empty().toResponse().skinSigns()).isNull();
	}

	@Test
	void 모르는_증상_이름은_값_오류다() {
		// 증상 목록을 바꾸기 전의 이름(PAPULE 등)도 모르는 이름이다.
		assertThat(ChatDraft.empty().apply(json("{\"skinSigns\": [\"PAPULE\"]}"))).isEqualTo(ApplyResult.INVALID_VALUE);
		assertThat(ChatDraft.empty().apply(json("{\"skinSigns\": \"XEROSIS\"}"))).isEqualTo(ApplyResult.INVALID_VALUE);
	}

	@Test
	void 특이사항은_500자까지만_받는다() {
		ChatDraft draft = ChatDraft.empty();

		assertThat(draft.apply(json("{\"notes\": \"" + "가".repeat(501) + "\"}"))).isEqualTo(ApplyResult.INVALID_VALUE);
		assertThat(draft.apply(json("{\"notes\": \"" + "가".repeat(500) + "\"}"))).isEqualTo(ApplyResult.OK);
		assertThat(draft.toResponse().notes()).hasSize(500);
	}

	@Test
	void 부위와_점수를_말하면_함께_기록한다() {
		ChatDraft draft = ChatDraft.empty();

		ApplyResult result = draft.apply(json("""
			{"painIndex": 5, "conditions": [{"bodyPart": "ARM", "pain": 4, "skinSigns": ["ITCHING", "ERYTHEMA"]}]}"""));

		assertThat(result).isEqualTo(ApplyResult.OK);
		assertThat(draft.conditions().get(BodyPart.ARM)).containsEntry(Symptom.PAIN, 4);
		assertThat(draft.toResponse().skinConditions().get(0).skinSigns())
			.containsExactly(SkinSign.ERYTHEMA, SkinSign.ITCHING);
		assertThat(draft.requiredSatisfied()).isTrue();
	}

	@Test
	void 부위만_말하고_점수를_말하지_않으면_증상이_필요한_상태다() {
		ChatDraft draft = ChatDraft.empty();

		draft.apply(json("""
			{"painIndex": 5, "conditions": [{"bodyPart": "LEG"}]}"""));

		assertThat(draft.pendingFields()).contains("symptoms");
		assertThat(draft.askingField()).isEqualTo("symptoms");
		assertThat(draft.requiredSatisfied()).isFalse();
	}

	@Test
	void 점수가_범위를_벗어나면_아무것도_반영하지_않는다() {
		ChatDraft draft = ChatDraft.empty();
		draft.apply(json("""
			{"conditions": [{"bodyPart": "ARM", "symptoms": {"pain": 4}}]}"""));

		// 뒤의 점수가 잘못이면 앞의 sleepHours도 반영되지 않아야 한다.
		ApplyResult result = draft.apply(json("""
			{"sleepHours": 6, "conditions": [{"bodyPart": "ARM", "symptoms": {"pain": 11}}]}"""));

		assertThat(result).isEqualTo(ApplyResult.INVALID_SCORE);
		assertThat(draft.conditions().get(BodyPart.ARM)).containsEntry(Symptom.PAIN, 4);
		assertThat(draft.pendingFields()).contains("sleepHours");
	}

	@Test
	void 음수_점수와_소수_점수도_거부한다() {
		assertThat(ChatDraft.empty().apply(json("""
			{"conditions": [{"bodyPart": "ARM", "symptoms": {"pain": -1}}]}"""))).isEqualTo(ApplyResult.INVALID_SCORE);
		assertThat(ChatDraft.empty().apply(json("""
			{"conditions": [{"bodyPart": "ARM", "symptoms": {"pain": 4.5}}]}"""))).isEqualTo(ApplyResult.INVALID_SCORE);
	}

	@Test
	void 모르는_부위나_증상_이름은_값_오류다() {
		assertThat(ChatDraft.empty().apply(json("""
			{"conditions": [{"bodyPart": "TAIL", "symptoms": {"pain": 4}}]}"""))).isEqualTo(ApplyResult.INVALID_VALUE);
		assertThat(ChatDraft.empty().apply(json("""
			{"conditions": [{"bodyPart": "ARM", "symptoms": {"hunger": 4}}]}"""))).isEqualTo(ApplyResult.INVALID_VALUE);
		assertThat(ChatDraft.empty().apply(json("""
			{"conditions": [{"bodyPart": "ARM", "symptoms": {"pain": "많이"}}]}"""))).isEqualTo(ApplyResult.INVALID_VALUE);
	}

	@Test
	void 수면_시간은_0에서_24시간만_허용한다() {
		ChatDraft draft = ChatDraft.empty();

		assertThat(draft.apply(json("{\"sleepHours\": 6.5}"))).isEqualTo(ApplyResult.OK);
		assertThat(draft.apply(json("{\"sleepHours\": 25}"))).isEqualTo(ApplyResult.INVALID_VALUE);
		assertThat(draft.apply(json("{\"sleepHours\": -1}"))).isEqualTo(ApplyResult.INVALID_VALUE);
		assertThat(draft.toResponse().sleepHours()).isEqualByComparingTo(BigDecimal.valueOf(6.5));
	}

	@Test
	void 생활습관과_메모를_반영한다() {
		ChatDraft draft = ChatDraft.empty();

		draft.apply(json("""
			{"alcohol": true, "outdoorActivity": "medium", "notes": "  평소보다 오래 걸었음  "}"""));

		assertThat(draft.toResponse().lifestyle().alcohol()).isTrue();
		assertThat(draft.toResponse().lifestyle().outdoorActivity()).isEqualTo(OutdoorActivity.MEDIUM);
		assertThat(draft.toResponse().notes()).isEqualTo("평소보다 오래 걸었음");
		assertThat(draft.pendingFields()).contains("painIndex").doesNotContain("notes");
	}

	@Test
	void 건너뛴_선택_항목은_더_묻지_않고_확인_단계로_넘어갈_수_있다() {
		ChatDraft draft = ChatDraft.empty();
		draft.apply(json("""
			{"painIndex": 3, "skipped": ["skinTrend", "itchScore", "bodyParts", "swelling", "scratch", "oozing",
			 "sleepDisturbed", "sleepHours", "moisturizer", "medicationUse", "notes", "unknown"]}"""));

		assertThat(draft.pendingFields()).isEmpty();
		assertThat(draft.readyForConfirmation(false)).isTrue();
	}

	@Test
	void 선택_항목이_남아_있어도_사용자가_마치겠다고_하면_확인_단계로_넘어간다() {
		ChatDraft draft = ChatDraft.empty();
		draft.apply(json("{\"painIndex\": 3}"));

		assertThat(draft.readyForConfirmation(false)).isFalse();
		assertThat(draft.readyForConfirmation(true)).isTrue();
	}

	@Test
	void 필수_항목이_없으면_마치겠다고_해도_확인_단계로_넘어가지_않는다() {
		assertThat(ChatDraft.empty().readyForConfirmation(true)).isFalse();
	}

	@Test
	void 같은_부위의_증상은_합쳐지고_점수는_새_값으로_바뀐다() {
		ChatDraft draft = ChatDraft.empty();
		draft.apply(json("""
			{"conditions": [{"bodyPart": "ARM", "pain": 4, "skinSigns": ["ITCHING"]}]}"""));
		draft.apply(json("""
			{"conditions": [{"bodyPart": "ARM", "symptoms": {"pain": 6}, "skinSigns": ["OOZING"]}]}"""));

		assertThat(draft.conditions().get(BodyPart.ARM)).containsEntry(Symptom.PAIN, 6);
		assertThat(draft.toResponse().skinConditions().get(0).skinSigns())
			.containsExactly(SkinSign.OOZING, SkinSign.ITCHING);
	}

	@Test
	void 부위_점수에_예전_증상_이름을_쓰면_값_오류다() {
		assertThat(ChatDraft.empty().apply(json("""
			{"conditions": [{"bodyPart": "ARM", "symptoms": {"itching": 4}}]}"""))).isEqualTo(ApplyResult.INVALID_VALUE);
		assertThat(ChatDraft.empty().apply(json("""
			{"conditions": [{"bodyPart": "ARM", "pain": 4, "skinSigns": ["PAPULE"]}]}"""))).isEqualTo(ApplyResult.INVALID_VALUE);
	}

	@Test
	void JSON으로_저장했다_복원해도_같다() {
		ChatDraft draft = ChatDraft.empty();
		draft.apply(json("""
			{"conditions": [{"bodyPart": "ARM", "pain": 4, "skinSigns": ["XEROSIS"]}, {"bodyPart": "FACE"}],
			 "painIndex": 7, "sleepHours": 6, "medication": true, "skinSigns": ["EDEMA", "ERYTHEMA"],
			 "alcohol": false, "outdoorActivity": "LOW", "notes": "메모", "skipped": ["notes"]}"""));

		ChatDraft restored = ChatDraft.fromJson(mapper, draft.toJson(mapper));

		assertThat(restored.toJson(mapper)).isEqualTo(draft.toJson(mapper));
		assertThat(restored.toResponse().skinSigns()).containsExactly(SkinSign.ERYTHEMA, SkinSign.EDEMA);
		// 홍반을 말했으므로 피부 모양은 답한 것으로 보고, 약 복용을 말했으므로 연고·약도 묻지 않는다.
		assertThat(restored.pendingFields()).containsExactly("skinTrend", "itchScore", "symptoms", "swelling", "scratch",
			"oozing", "sleepDisturbed", "moisturizer");
	}

	@Test
	void 증상_없음도_복원된다() {
		ChatDraft draft = ChatDraft.empty();
		draft.apply(json("{\"skinSigns\": []}"));

		ChatDraft restored = ChatDraft.fromJson(mapper, draft.toJson(mapper));

		assertThat(restored.toResponse().skinSigns()).isEmpty();
	}

	@Test
	void 지금_묻는_항목은_남은_항목_중_첫_번째이고_모두_채우면_null이다() {
		ChatDraft draft = spring();
		assertThat(draft.askingField()).isEqualTo("skinTrend");

		draft.apply(json("{\"skinTrend\": \"SAME\", \"itchScore\": 2, \"painIndex\": 6, \"medication\": true}"));
		assertThat(draft.askingField()).isEqualTo("bodyParts");

		draft.apply(json("{\"skipped\": [\"bodyParts\"]}"));
		assertThat(draft.askingField()).isEqualTo("swelling");

		draft.apply(json("""
			{"swelling": false, "scratch": false, "sleepDisturbed": false, "sleepHours": 7, "moisturizer": true,
			 "skipped": ["notes"]}"""));
		assertThat(draft.askingField()).isNull();
		assertThat(draft.readyForConfirmation(false)).isTrue();
	}

	@Test
	void 고통_지수_다음에는_부위를_묻고_고른_부위에는_고통_지수가_점수로_들어간다() {
		ChatDraft draft = ChatDraft.empty();
		// 고통 지수가 없으면 부위를 반영하지 않는다.
		assertThat(draft.applyBodyParts(json("[\"FACE\"]"))).isEqualTo(ApplyResult.INVALID_VALUE);

		draft.apply(json("{\"skinTrend\": \"SAME\", \"itchScore\": 3, \"painIndex\": 7}"));
		assertThat(draft.askingField()).isEqualTo("bodyParts");

		assertThat(draft.applyBodyParts(json("[\"FACE\", \"NOSE\"]"))).isEqualTo(ApplyResult.INVALID_VALUE);
		assertThat(draft.applyBodyParts(json("[]"))).isEqualTo(ApplyResult.INVALID_VALUE);
		assertThat(draft.conditions()).isEmpty();

		assertThat(draft.applyBodyParts(json("[\"FACE\", \"left_hand\"]"))).isEqualTo(ApplyResult.OK);
		assertThat(draft.conditions()).containsOnlyKeys(BodyPart.FACE, BodyPart.LEFT_HAND);
		assertThat(draft.conditions().get(BodyPart.FACE)).containsEntry(Symptom.PAIN, 7);
		// 부위를 고르면 그 부분의 피부 모양을 묻는다.
		assertThat(draft.pendingFields()).contains("skinShape");
	}

	@Test
	void 고통도_가려움도_없으면_부위를_묻지_않는다() {
		ChatDraft draft = ChatDraft.empty();
		draft.apply(json("{\"painIndex\": 0, \"itchScore\": 0}"));
		assertThat(draft.pendingFields()).doesNotContain("bodyParts");

		// 고통이 없어도 가려우면 부위를 묻는다.
		ChatDraft itchy = ChatDraft.empty();
		itchy.apply(json("{\"painIndex\": 0, \"itchScore\": 4}"));
		assertThat(itchy.pendingFields()).contains("bodyParts");
	}

	@Test
	void 피부가_좋아졌고_가렵지_않으면_부위를_묻지_않는다() {
		ChatDraft draft = ChatDraft.empty();
		draft.apply(json("{\"skinTrend\": \"BETTER\", \"itchScore\": 0, \"painIndex\": 3}"));

		assertThat(draft.pendingFields()).doesNotContain("bodyParts");
	}

	@Test
	void 점수가_빠진_부위가_있거나_되묻는_중이면_고통_지수보다_symptoms를_먼저_묻는다() {
		ChatDraft draft = ChatDraft.empty();
		draft.apply(json("{\"conditions\": [{\"bodyPart\": \"ARM\"}]}"));
		assertThat(draft.askingField()).isEqualTo("symptoms");

		ChatDraft followUp = ChatDraft.empty();
		followUp.markFollowUp(true);
		assertThat(followUp.askingField()).isEqualTo("symptoms");
		// 되묻는 상태도 저장·복원된다.
		assertThat(ChatDraft.fromJson(mapper, followUp.toJson(mapper)).askingField()).isEqualTo("symptoms");
		followUp.markFollowUp(false);
		assertThat(followUp.askingField()).isEqualTo("skinTrend");
	}

	@Test
	void 비어_있거나_깨진_JSON은_빈_초안으로_시작한다() {
		assertThat(ChatDraft.fromJson(mapper, null).pendingFields()).contains("painIndex");
		assertThat(ChatDraft.fromJson(mapper, "  ").pendingFields()).contains("painIndex");
		assertThat(ChatDraft.fromJson(mapper, "{깨진 json").pendingFields()).contains("painIndex");
	}

	// ── 기획 시트의 필수 항목 ──

	@Test
	void 선택지로_고른_답을_반영하고_선택지에_없는_답은_받지_않는다() {
		ChatDraft draft = spring();

		assertThat(answer(draft, "skinTrend", "좋음")).isEqualTo(ApplyResult.INVALID_VALUE);
		assertThat(answer(draft, "skinTrend", "평소와 비슷")).isEqualTo(ApplyResult.OK);
		assertThat(answer(draft, "medicationUse", "처방 없음")).isEqualTo(ApplyResult.OK);
		assertThat(answer(draft, "moisturizer", "발랐음")).isEqualTo(ApplyResult.OK);

		assertThat(draft.toResponse().dailyCheck().skinTrend()).isEqualTo(SkinTrend.SAME);
		assertThat(draft.toResponse().dailyCheck().medicationUse()).isEqualTo(MedicationUse.NO_PRESCRIPTION);
		// 처방이 없으면 약을 복용하지 않은 것이다.
		assertThat(draft.toResponse().medication()).isFalse();
		assertThat(draft.toResponse().dailyCheck().moisturizer()).isTrue();
	}

	@Test
	void 피부_모양_붓기_긁은_자국_진물_피는_나타난_증상으로_기록한다() {
		ChatDraft draft = spring();
		draft.apply(json("{\"itchScore\": 3}"));

		assertThat(answer(draft, "skinShape", List.of("붉어짐", "갈라짐"))).isEqualTo(ApplyResult.OK);
		assertThat(answer(draft, "skinShape", List.of("반짝임"))).isEqualTo(ApplyResult.INVALID_VALUE);
		answer(draft, "swelling", "예");
		answer(draft, "scratch", "있음");
		answer(draft, "oozing", "둘 다");

		// 가려움이 1점 이상이면 가려움 증상도 기록한다.
		assertThat(draft.toResponse().skinSigns()).containsExactlyInAnyOrder(SkinSign.ERYTHEMA, SkinSign.FISSURE,
			SkinSign.EDEMA, SkinSign.EXCORIATION, SkinSign.OOZING, SkinSign.BLEEDING, SkinSign.ITCHING);
	}

	@Test
	void 피부_모양이_괜찮으면_증상_없이_답한_것으로_본다() {
		ChatDraft draft = spring();
		draft.apply(json("{\"painIndex\": 4}"));
		draft.applyBodyParts(json("[\"LEFT_THIGH\"]"));

		assertThat(answer(draft, "skinShape", List.of())).isEqualTo(ApplyResult.OK);

		assertThat(draft.pendingFields()).doesNotContain("skinShape");
		assertThat(draft.toResponse().skinSigns()).isNull();
	}

	@Test
	void 긁은_자국이_없으면_진물_피는_묻지_않는다() {
		ChatDraft draft = spring();

		answer(draft, "scratch", "없음");

		assertThat(draft.pendingFields()).doesNotContain("oozing");
	}

	@Test
	void 글로_말한_새_항목도_검증해서_반영한다() {
		ChatDraft draft = ChatDraft.empty();

		assertThat(draft.apply(json("{\"itchScore\": 11}"))).isEqualTo(ApplyResult.INVALID_SCORE);
		assertThat(draft.apply(json("{\"skinTrend\": \"최고\"}"))).isEqualTo(ApplyResult.INVALID_VALUE);
		assertThat(draft.apply(json("""
			{"skinTrend": "worse", "itchScore": 7, "oozing": "BLOOD", "sleepDisturbed": true, "medicationUse": "USED"}""")))
			.isEqualTo(ApplyResult.OK);

		assertThat(draft.toResponse().dailyCheck().skinTrend()).isEqualTo(SkinTrend.WORSE);
		assertThat(draft.toResponse().skinSigns()).contains(SkinSign.BLEEDING, SkinSign.ITCHING);
		assertThat(draft.toResponse().medication()).isTrue();
	}

	// ── 연결 질문 ──

	@Test
	void 나빠진_날은_원인을_묻는_연결_질문을_3개까지_묻는다() {
		ChatDraft draft = spring();

		answer(draft, "skinTrend", "평소보다 나빠짐");
		assertThat(draft.askingField()).isEqualTo("C01");
		answer(draft, "C01", "씻을 때");
		assertThat(draft.askingField()).isEqualTo("itchScore");

		draft.apply(json("{\"itchScore\": 7}"));
		assertThat(draft.askingField()).isEqualTo("C02");
		answer(draft, "C02", "운동 후");
		// 운동 후에 심해졌으면 땀 관리를 이어서 묻는다.
		assertThat(draft.askingField()).isEqualTo("C11");
		answer(draft, "C11", "바로");

		// 3개를 다 물었으므로 목을 골라도 목 질문은 묻지 않는다.
		draft.apply(json("{\"painIndex\": 5}"));
		draft.applyBodyParts(json("[\"NECK\"]"));
		assertThat(draft.askingField()).isEqualTo("skinShape");

		Map<String, String> expected = new LinkedHashMap<>();
		expected.put("C01", "씻을 때");
		expected.put("C02", "운동 후");
		expected.put("C11", "바로");
		assertThat(draft.toRecordRequest(LocalDate.of(2026, 4, 15)).dailyCheck().linkedAnswers())
			.containsExactlyEntriesOf(expected);
	}

	@Test
	void 비슷한_날은_연결_질문을_하나만_묻고_계절_질문은_그_계절에만_묻는다() {
		ChatDraft winter = ChatDraft.empty();
		winter.context(LocalDate.of(2026, 1, 10), Set.of());
		answer(winter, "skinTrend", "평소와 비슷");
		assertThat(winter.askingField()).isEqualTo("C14");
		answer(winter, "C14", "자주 켬");
		winter.apply(json("{\"itchScore\": 8}"));
		// 하나를 이미 물었으므로 가려움이 심해도 더 묻지 않는다.
		assertThat(winter.askingField()).isEqualTo("painIndex");

		ChatDraft summer = ChatDraft.empty();
		summer.context(LocalDate.of(2026, 7, 10), Set.of());
		answer(summer, "skinTrend", "평소와 비슷");
		assertThat(summer.askingField()).isEqualTo("C16");

		ChatDraft spring = spring();
		answer(spring, "skinTrend", "평소와 비슷");
		assertThat(spring.askingField()).isEqualTo("itchScore");
	}

	@Test
	void 나빠진_날은_우선순위가_낮은_질문을_묻지_않는다() {
		ChatDraft draft = ChatDraft.empty();
		draft.context(LocalDate.of(2026, 12, 1), Set.of());
		draft.apply(json("{\"skinTrend\": \"WORSE\", \"itchScore\": 2, \"painIndex\": 4}"));
		draft.skip("C01");

		draft.applyBodyParts(json("[\"TRUNK\"]"));

		// 겨울에 몸을 골랐지만 옷 소재(C15)는 우선순위가 낮아 묻지 않는다.
		assertThat(draft.askingField()).isEqualTo("skinShape");
	}

	@Test
	void 부위가_여러_곳이어도_부위_질문은_하나만_묻는다() {
		ChatDraft draft = spring();
		draft.apply(json("{\"skinTrend\": \"WORSE\", \"itchScore\": 2, \"painIndex\": 4}"));
		draft.skip("C01");
		draft.applyBodyParts(json("[\"FACE\", \"NECK\", \"LEFT_HAND\"]"));

		assertThat(draft.askingField()).isEqualTo("C04");
		answer(draft, "C04", "없음");

		assertThat(draft.askingField()).isEqualTo("skinShape");
	}

	@Test
	void 최근_7일_안에_새_제품을_물었으면_얼굴은_마스크를_묻는다() {
		ChatDraft draft = ChatDraft.empty();
		draft.context(LocalDate.of(2026, 4, 15), Set.of("C04"));
		draft.apply(json("{\"skinTrend\": \"SAME\", \"itchScore\": 2, \"painIndex\": 4}"));

		draft.applyBodyParts(json("[\"FACE\"]"));

		assertThat(draft.askingField()).isEqualTo("C13");
	}

	@Test
	void 연결_질문_답이_뒤의_항목을_대신하면_미리_채워_묻지_않는다() {
		ChatDraft draft = spring();
		draft.apply(json("{\"skinTrend\": \"WORSE\", \"itchScore\": 3, \"painIndex\": 4}"));
		draft.skip("C01");
		draft.applyBodyParts(json("[\"LEFT_THIGH\"]"));

		answer(draft, "skinShape", List.of("하얗게 일어남"));
		assertThat(draft.askingField()).isEqualTo("C06");
		answer(draft, "C06", "시간 지나서");
		answer(draft, "swelling", "아니요");
		answer(draft, "scratch", "있음");
		assertThat(draft.askingField()).isEqualTo("C08");
		answer(draft, "C08", "자다가 긁음");

		// 보습제(C06)와 수면 방해(C08 자다가)는 미리 채워져 묻지 않는다. 연결 질문 3개를 다 물어 C10도 묻지 않는다.
		assertThat(draft.toResponse().dailyCheck().moisturizer()).isTrue();
		assertThat(draft.toResponse().dailyCheck().sleepDisturbed()).isTrue();
		assertThat(draft.pendingFields()).doesNotContain("moisturizer", "sleepDisturbed");
		assertThat(draft.askingField()).isEqualTo("oozing");
	}

	@Test
	void 붉어졌다고_하면_붓기를_묻는_연결_질문으로_붓기를_채운다() {
		ChatDraft draft = spring();
		draft.apply(json("{\"skinTrend\": \"SAME\", \"itchScore\": 3, \"painIndex\": 4}"));
		draft.applyBodyParts(json("[\"LEFT_THIGH\"]"));

		answer(draft, "skinShape", List.of("붉어짐"));
		assertThat(draft.askingField()).isEqualTo("C07");
		answer(draft, "C07", "예");

		assertThat(draft.pendingFields()).doesNotContain("swelling");
		assertThat(draft.toResponse().skinSigns()).contains(SkinSign.EDEMA);
	}

	@Test
	void 직접_입력을_고르면_글로_받고_맞는지_확인한_뒤_기록한다() {
		ChatDraft draft = spring();
		draft.apply(json("{\"skinTrend\": \"WORSE\", \"itchScore\": 8}"));
		draft.skip("C01");
		answer(draft, "C02", "밥 먹고");
		assertThat(draft.askingField()).isEqualTo("C12");

		answer(draft, "C12", "직접 입력");
		assertThat(draft.askingField()).isEqualTo("C12_TEXT");
		assertThat(draft.awaitingText()).isTrue();
		assertThat(answer(draft, "C12_TEXT", "가".repeat(201))).isEqualTo(ApplyResult.INVALID_VALUE);

		answer(draft, "C12_TEXT", "  떡볶이 ");
		assertThat(draft.askingField()).isEqualTo("C12_CONFIRM");
		assertThat(ChatQuestions.question(draft, "C12_CONFIRM")).isEqualTo("음식 메모: ‘떡볶이’ 이렇게 기록할게요. 맞나요?");
		answer(draft, "C12_CONFIRM", "다시 적을게요");
		assertThat(draft.askingField()).isEqualTo("C12_TEXT");

		answer(draft, "C12_TEXT", "매운 떡볶이");
		answer(draft, "C12_CONFIRM", "맞아요");

		assertThat(draft.toResponse().dailyCheck().linkedAnswers()).containsEntry("C12", "매운 떡볶이");
		assertThat(draft.askingField()).isEqualTo("painIndex");
	}

	@Test
	void 직접_적다가_넘기면_그_연결_질문을_건너뛴다() {
		ChatDraft draft = spring();
		draft.apply(json("{\"skinTrend\": \"SAME\", \"itchScore\": 8}"));
		answer(draft, "C02", "밥 먹고");
		answer(draft, "C12", "직접 입력");

		assertThat(draft.skip("C12_TEXT")).isEqualTo(ApplyResult.OK);

		assertThat(draft.awaitingText()).isFalse();
		assertThat(draft.toResponse().dailyCheck().linkedAnswers()).doesNotContainKey("C12");
	}

	@Test
	void 연결_질문_진행_상태도_저장_복원된다() {
		ChatDraft draft = spring();
		draft.apply(json("{\"skinTrend\": \"WORSE\", \"itchScore\": 8}"));
		answer(draft, "C01", "자려고 누웠을 때");
		answer(draft, "C02", "밥 먹고");
		answer(draft, "C12", "직접 입력");
		answer(draft, "C12_TEXT", "치킨");

		ChatDraft restored = ChatDraft.fromJson(mapper, draft.toJson(mapper));

		assertThat(restored.toJson(mapper)).isEqualTo(draft.toJson(mapper));
		assertThat(restored.askingField()).isEqualTo("C12_CONFIRM");
	}

	@Test
	void 기록을_고치는_중에는_연결_질문을_묻지_않는다() {
		ChatDraft draft = spring();
		draft.apply(json("{\"skinTrend\": \"WORSE\", \"painIndex\": 3}"));

		draft.startEditing();

		assertThat(draft.askingField()).isNull();
	}

}
