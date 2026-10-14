package com.atopy.Skinlog.domain.chatbot.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.atopy.Skinlog.domain.chatbot.service.ChatDraft.ApplyResult;
import com.atopy.Skinlog.domain.record.entity.BodyPart;
import com.atopy.Skinlog.domain.record.entity.OutdoorActivity;
import com.atopy.Skinlog.domain.record.entity.SkinSign;
import com.atopy.Skinlog.domain.record.entity.Symptom;
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
			.containsExactly("painIndex", "sleepHours", "medication", "skinSigns", "notes");
		assertThat(draft.requiredSatisfied()).isFalse();
		assertThat(draft.readyForConfirmation(true)).isFalse();
	}

	@Test
	void 고통_지수를_반영하면_필수_항목이_채워진다() {
		ChatDraft draft = ChatDraft.empty();

		assertThat(draft.apply(json("{\"painIndex\": 6}"))).isEqualTo(ApplyResult.OK);

		assertThat(draft.toResponse().painIndex()).isEqualTo(6);
		assertThat(draft.requiredSatisfied()).isTrue();
		assertThat(draft.pendingFields()).containsExactly("sleepHours", "medication", "skinSigns", "notes");
	}

	@Test
	void 고통_지수는_1에서_10_정수만_허용한다() {
		assertThat(ChatDraft.empty().apply(json("{\"painIndex\": 0}"))).isEqualTo(ApplyResult.INVALID_PAIN_INDEX);
		assertThat(ChatDraft.empty().apply(json("{\"painIndex\": 11}"))).isEqualTo(ApplyResult.INVALID_PAIN_INDEX);
		assertThat(ChatDraft.empty().apply(json("{\"painIndex\": 4.5}"))).isEqualTo(ApplyResult.INVALID_PAIN_INDEX);
		assertThat(ChatDraft.empty().apply(json("{\"painIndex\": \"많이\"}"))).isEqualTo(ApplyResult.INVALID_VALUE);
		assertThat(ChatDraft.empty().apply(json("{\"painIndex\": 1}"))).isEqualTo(ApplyResult.OK);
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

		assertThat(draft.toResponse().skinSigns()).containsExactly(SkinSign.XEROSIS, SkinSign.LICHENIFICATION);
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
		assertThat(ChatDraft.empty().apply(json("{\"skinSigns\": [\"ITCHING\"]}"))).isEqualTo(ApplyResult.INVALID_VALUE);
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
			{"painIndex": 5, "conditions": [{"bodyPart": "ARM", "symptoms": {"itching": 4, "redness": 0}}]}"""));

		assertThat(result).isEqualTo(ApplyResult.OK);
		assertThat(draft.conditions().get(BodyPart.ARM))
			.containsEntry(Symptom.ITCHING, 4)
			.containsEntry(Symptom.REDNESS, 0);
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
			{"conditions": [{"bodyPart": "ARM", "symptoms": {"itching": 4}}]}"""));

		// 뒤의 점수가 잘못이면 앞의 sleepHours도 반영되지 않아야 한다.
		ApplyResult result = draft.apply(json("""
			{"sleepHours": 6, "conditions": [{"bodyPart": "ARM", "symptoms": {"dryness": 11}}]}"""));

		assertThat(result).isEqualTo(ApplyResult.INVALID_SCORE);
		assertThat(draft.conditions().get(BodyPart.ARM)).doesNotContainKey(Symptom.DRYNESS);
		assertThat(draft.pendingFields()).contains("sleepHours");
	}

	@Test
	void 음수_점수와_소수_점수도_거부한다() {
		assertThat(ChatDraft.empty().apply(json("""
			{"conditions": [{"bodyPart": "ARM", "symptoms": {"itching": -1}}]}"""))).isEqualTo(ApplyResult.INVALID_SCORE);
		assertThat(ChatDraft.empty().apply(json("""
			{"conditions": [{"bodyPart": "ARM", "symptoms": {"itching": 4.5}}]}"""))).isEqualTo(ApplyResult.INVALID_SCORE);
	}

	@Test
	void 모르는_부위나_증상_이름은_값_오류다() {
		assertThat(ChatDraft.empty().apply(json("""
			{"conditions": [{"bodyPart": "TAIL", "symptoms": {"itching": 4}}]}"""))).isEqualTo(ApplyResult.INVALID_VALUE);
		assertThat(ChatDraft.empty().apply(json("""
			{"conditions": [{"bodyPart": "ARM", "symptoms": {"hunger": 4}}]}"""))).isEqualTo(ApplyResult.INVALID_VALUE);
		assertThat(ChatDraft.empty().apply(json("""
			{"conditions": [{"bodyPart": "ARM", "symptoms": {"itching": "많이"}}]}"""))).isEqualTo(ApplyResult.INVALID_VALUE);
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
		assertThat(draft.pendingFields()).containsExactly("painIndex", "sleepHours", "medication", "skinSigns");
	}

	@Test
	void 건너뛴_선택_항목은_더_묻지_않고_확인_단계로_넘어갈_수_있다() {
		ChatDraft draft = ChatDraft.empty();
		draft.apply(json("""
			{"painIndex": 3, "skipped": ["sleepHours", "medication", "skinSigns", "notes", "unknown"]}"""));

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
	void 같은_부위의_증상은_합쳐진다() {
		ChatDraft draft = ChatDraft.empty();
		draft.apply(json("""
			{"conditions": [{"bodyPart": "ARM", "symptoms": {"itching": 4}}]}"""));
		draft.apply(json("""
			{"conditions": [{"bodyPart": "ARM", "symptoms": {"redness": 2, "itching": 6}}]}"""));

		assertThat(draft.conditions().get(BodyPart.ARM))
			.containsEntry(Symptom.ITCHING, 6)
			.containsEntry(Symptom.REDNESS, 2);
	}

	@Test
	void JSON으로_저장했다_복원해도_같다() {
		ChatDraft draft = ChatDraft.empty();
		draft.apply(json("""
			{"conditions": [{"bodyPart": "ARM", "symptoms": {"itching": 4}}, {"bodyPart": "FACE"}],
			 "painIndex": 7, "sleepHours": 6, "medication": true, "skinSigns": ["PAPULE", "ERYTHEMA"],
			 "alcohol": false, "outdoorActivity": "LOW", "notes": "메모", "skipped": ["notes"]}"""));

		ChatDraft restored = ChatDraft.fromJson(mapper, draft.toJson(mapper));

		assertThat(restored.toJson(mapper)).isEqualTo(draft.toJson(mapper));
		assertThat(restored.toResponse().skinSigns()).containsExactly(SkinSign.ERYTHEMA, SkinSign.PAPULE);
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
	void 비어_있거나_깨진_JSON은_빈_초안으로_시작한다() {
		assertThat(ChatDraft.fromJson(mapper, null).pendingFields()).contains("painIndex");
		assertThat(ChatDraft.fromJson(mapper, "  ").pendingFields()).contains("painIndex");
		assertThat(ChatDraft.fromJson(mapper, "{깨진 json").pendingFields()).contains("painIndex");
	}

}
