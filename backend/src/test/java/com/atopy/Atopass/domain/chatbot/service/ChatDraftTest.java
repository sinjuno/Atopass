package com.atopy.Atopass.domain.chatbot.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.atopy.Atopass.domain.chatbot.service.ChatDraft.ApplyResult;
import com.atopy.Atopass.domain.record.entity.BodyPart;
import com.atopy.Atopass.domain.record.entity.OutdoorActivity;
import com.atopy.Atopass.domain.record.entity.SkinSign;
import com.atopy.Atopass.domain.record.entity.Symptom;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ChatDraftTest {

	private final JsonMapper mapper = JsonMapper.builder().build();

	private JsonNode json(String text) {
		return mapper.readTree(text);
	}

	@Test
	void 처음에는_고통_지수부터_묻는_순서로_항목이_남아_있다() {
		ChatDraft draft = ChatDraft.empty();

		assertThat(draft.pendingFields())
			.containsExactly("painIndex", "skinSigns", "bodyParts", "sleepHours", "medication", "notes");
		assertThat(draft.requiredSatisfied()).isFalse();
		assertThat(draft.readyForConfirmation(true)).isFalse();
	}

	@Test
	void 고통_지수를_반영하면_필수_항목이_채워진다() {
		ChatDraft draft = ChatDraft.empty();

		assertThat(draft.apply(json("{\"painIndex\": 6}"))).isEqualTo(ApplyResult.OK);

		assertThat(draft.toResponse().painIndex()).isEqualTo(6);
		assertThat(draft.requiredSatisfied()).isTrue();
		assertThat(draft.pendingFields()).containsExactly("skinSigns", "bodyParts", "sleepHours", "medication", "notes");
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
		assertThat(draft.pendingFields()).doesNotContain("medication");
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

		assertThat(draft.pendingFields().get(0)).isEqualTo("symptoms");
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
		assertThat(draft.pendingFields()).containsExactly("painIndex", "skinSigns", "bodyParts", "sleepHours", "medication");
	}

	@Test
	void 건너뛴_선택_항목은_더_묻지_않고_확인_단계로_넘어갈_수_있다() {
		ChatDraft draft = ChatDraft.empty();
		draft.apply(json("""
			{"painIndex": 3, "skipped": ["bodyParts", "sleepHours", "medication", "skinSigns", "notes", "unknown"]}"""));

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
		assertThat(restored.pendingFields()).containsExactly("symptoms");
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
		ChatDraft draft = ChatDraft.empty();
		assertThat(draft.askingField()).isEqualTo("painIndex");

		draft.apply(json("{\"painIndex\": 6, \"medication\": true}"));
		assertThat(draft.askingField()).isEqualTo("skinSigns");

		draft.apply(json("{\"skinSigns\": []}"));
		assertThat(draft.askingField()).isEqualTo("bodyParts");

		draft.apply(json("{\"skipped\": [\"bodyParts\"]}"));
		assertThat(draft.askingField()).isEqualTo("sleepHours");

		draft.apply(json("{\"skipped\": [\"sleepHours\", \"notes\"]}"));
		assertThat(draft.askingField()).isNull();
	}

	@Test
	void 고통_지수_증상_다음에_부위를_묻고_고른_부위에는_고통_지수와_증상이_들어간다() {
		ChatDraft draft = ChatDraft.empty();
		// 고통 지수가 없으면 부위를 반영하지 않는다.
		assertThat(draft.applyBodyParts(json("[\"FACE\"]"))).isEqualTo(ApplyResult.INVALID_VALUE);

		draft.apply(json("{\"painIndex\": 7}"));
		assertThat(draft.askingField()).isEqualTo("skinSigns");
		draft.apply(json("{\"skinSigns\": [\"ITCHING\", \"ERYTHEMA\"]}"));
		assertThat(draft.askingField()).isEqualTo("bodyParts");

		assertThat(draft.applyBodyParts(json("[\"FACE\", \"NOSE\"]"))).isEqualTo(ApplyResult.INVALID_VALUE);
		assertThat(draft.applyBodyParts(json("[]"))).isEqualTo(ApplyResult.INVALID_VALUE);
		assertThat(draft.conditions()).isEmpty();

		assertThat(draft.applyBodyParts(json("[\"FACE\", \"left_hand\"]"))).isEqualTo(ApplyResult.OK);
		assertThat(draft.conditions()).containsOnlyKeys(BodyPart.FACE, BodyPart.LEFT_HAND);
		assertThat(draft.conditions().get(BodyPart.FACE)).containsEntry(Symptom.PAIN, 7);
		// 방금 고른 증상이 고른 부위마다 그 부위의 증상으로 들어간다.
		assertThat(draft.toResponse().skinConditions())
			.allSatisfy(c -> assertThat(c.skinSigns()).containsExactly(SkinSign.ERYTHEMA, SkinSign.ITCHING));
		assertThat(draft.askingField()).isEqualTo("sleepHours");
	}

	@Test
	void 증상이_없다고_해도_고통이_있으면_부위를_묻고_부위에는_증상이_없다() {
		ChatDraft draft = ChatDraft.empty();
		draft.apply(json("{\"painIndex\": 4, \"skinSigns\": []}"));
		assertThat(draft.askingField()).isEqualTo("bodyParts");

		draft.applyBodyParts(json("[\"NECK\"]"));
		assertThat(draft.toResponse().skinConditions().get(0).skinSigns()).isEmpty();
	}

	@Test
	void 고통이_0점이어도_증상이_있으면_부위를_묻는다() {
		ChatDraft draft = ChatDraft.empty();
		draft.apply(json("{\"painIndex\": 0, \"skinSigns\": [\"XEROSIS\"]}"));
		assertThat(draft.askingField()).isEqualTo("bodyParts");

		draft.applyBodyParts(json("[\"FACE\"]"));
		assertThat(draft.conditions().get(BodyPart.FACE)).containsEntry(Symptom.PAIN, 0);
		assertThat(draft.toResponse().skinConditions().get(0).skinSigns()).containsExactly(SkinSign.XEROSIS);
	}

	@Test
	void 고통_지수가_0점이고_증상도_없으면_부위를_묻지_않는다() {
		ChatDraft draft = ChatDraft.empty();
		draft.apply(json("{\"painIndex\": 0, \"skinSigns\": []}"));

		assertThat(draft.pendingFields()).doesNotContain("bodyParts");
		assertThat(draft.askingField()).isEqualTo("sleepHours");
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
		assertThat(followUp.askingField()).isEqualTo("painIndex");
	}

	@Test
	void 비어_있거나_깨진_JSON은_빈_초안으로_시작한다() {
		assertThat(ChatDraft.fromJson(mapper, null).pendingFields()).contains("painIndex");
		assertThat(ChatDraft.fromJson(mapper, "  ").pendingFields()).contains("painIndex");
		assertThat(ChatDraft.fromJson(mapper, "{깨진 json").pendingFields()).contains("painIndex");
	}

}
