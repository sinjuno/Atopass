package com.atopy.Atopass.domain.chatbot.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.atopy.Atopass.domain.chatbot.service.ChatDraft.ApplyResult;
import com.atopy.Atopass.domain.record.entity.BodyPart;
import com.atopy.Atopass.domain.record.entity.OutdoorActivity;
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
	void 처음에는_부위가_필요한_상태다() {
		ChatDraft draft = ChatDraft.empty();

		assertThat(draft.pendingFields()).containsExactly("bodyPart", "sleepHours", "lifestyle", "notes");
		assertThat(draft.requiredSatisfied()).isFalse();
		assertThat(draft.readyForConfirmation(true)).isFalse();
	}

	@Test
	void 부위와_점수를_반영한다() {
		ChatDraft draft = ChatDraft.empty();

		ApplyResult result = draft.apply(json("""
			{"conditions": [{"bodyPart": "ARM", "symptoms": {"itching": 4, "redness": 0}}]}"""));

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
			{"conditions": [{"bodyPart": "LEG"}]}"""));

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
			{"alcohol": true, "outdoorActivity": "medium", "notes": "  약을 먹지 않았음  "}"""));

		assertThat(draft.toResponse().lifestyle().alcohol()).isTrue();
		assertThat(draft.toResponse().lifestyle().outdoorActivity()).isEqualTo(OutdoorActivity.MEDIUM);
		assertThat(draft.toResponse().notes()).isEqualTo("약을 먹지 않았음");
		assertThat(draft.pendingFields()).containsExactly("bodyPart", "sleepHours");
	}

	@Test
	void 건너뛴_선택_항목은_더_묻지_않고_확인_단계로_넘어갈_수_있다() {
		ChatDraft draft = ChatDraft.empty();
		draft.apply(json("""
			{"conditions": [{"bodyPart": "ARM", "symptoms": {"itching": 4}}],
			 "skipped": ["sleepHours", "lifestyle", "notes", "unknown"]}"""));

		assertThat(draft.pendingFields()).isEmpty();
		assertThat(draft.readyForConfirmation(false)).isTrue();
	}

	@Test
	void 선택_항목이_남아_있어도_사용자가_마치겠다고_하면_확인_단계로_넘어간다() {
		ChatDraft draft = ChatDraft.empty();
		draft.apply(json("""
			{"conditions": [{"bodyPart": "ARM", "symptoms": {"itching": 4}}]}"""));

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
			 "sleepHours": 6, "alcohol": false, "outdoorActivity": "LOW", "notes": "메모", "skipped": ["notes"]}"""));

		ChatDraft restored = ChatDraft.fromJson(mapper, draft.toJson(mapper));

		assertThat(restored.toJson(mapper)).isEqualTo(draft.toJson(mapper));
		assertThat(restored.pendingFields()).containsExactly("symptoms");
	}

	@Test
	void 비어_있거나_깨진_JSON은_빈_초안으로_시작한다() {
		assertThat(ChatDraft.fromJson(mapper, null).pendingFields()).contains("bodyPart");
		assertThat(ChatDraft.fromJson(mapper, "  ").pendingFields()).contains("bodyPart");
		assertThat(ChatDraft.fromJson(mapper, "{깨진 json").pendingFields()).contains("bodyPart");
	}

}
