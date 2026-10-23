package com.atopy.Atopass.domain.chatbot.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.atopy.Atopass.domain.record.entity.SkinRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class SituationalQuestionSelectorTest {

	private final JsonMapper mapper = JsonMapper.builder().build();
	private final SituationalQuestionProperties properties = new SituationalQuestionProperties(2, 3);
	private final LocalDate today = LocalDate.of(2026, 10, 23);

	private ChatDraft draftWithPainIndex(int painIndex) {
		ChatDraft draft = ChatDraft.empty();
		draft.apply(mapper.readTree("{\"painIndex\": " + painIndex + "}"));
		return draft;
	}

	private SkinRecord recordWithPainIndex(int painIndex) {
		return SkinRecord.builder().recordDate(today.minusDays(1)).painIndex(painIndex).build();
	}

	@Test
	void 고통_지수가_어제보다_threshold_이상_오르면_P01이_선택된다() {
		SituationalQuestionContext context = new SituationalQuestionContext(
			draftWithPainIndex(6), Optional.of(recordWithPainIndex(3)));
		SituationalQuestionSelector selector = new SituationalQuestionSelector(
			List.of(new PainJumpRule(properties)), properties);

		assertThat(selector.select(context, Map.of(), today)).contains("P01");
	}

	@Test
	void 어제_기록이_없으면_P01이_선택되지_않는다() {
		SituationalQuestionContext context = new SituationalQuestionContext(draftWithPainIndex(6), Optional.empty());
		SituationalQuestionSelector selector = new SituationalQuestionSelector(
			List.of(new PainJumpRule(properties)), properties);

		assertThat(selector.select(context, Map.of(), today)).isEmpty();
	}

	@Test
	void 점수_차이가_threshold_미만이면_선택되지_않는다() {
		SituationalQuestionContext context = new SituationalQuestionContext(
			draftWithPainIndex(4), Optional.of(recordWithPainIndex(3)));
		SituationalQuestionSelector selector = new SituationalQuestionSelector(
			List.of(new PainJumpRule(properties)), properties);

		assertThat(selector.select(context, Map.of(), today)).isEmpty();
	}

	@Test
	void 최근_3일_이내에_나온_질문은_제외된다() {
		SituationalQuestionContext context = new SituationalQuestionContext(
			draftWithPainIndex(6), Optional.of(recordWithPainIndex(3)));
		SituationalQuestionSelector selector = new SituationalQuestionSelector(
			List.of(new PainJumpRule(properties)), properties);

		assertThat(selector.select(context, Map.of("P01", today.minusDays(2)), today)).isEmpty();
		assertThat(selector.select(context, Map.of("P01", today.minusDays(3)), today)).contains("P01");
	}

	@Test
	void 여러_규칙이_맞으면_순위가_높은_쪽을_고른다() {
		SituationalQuestionRule low = new SituationalQuestionRule() {
			public String id() {
				return "LOW";
			}

			public int priority() {
				return 2;
			}

			public boolean matches(SituationalQuestionContext context) {
				return true;
			}
		};
		SituationalQuestionRule high = new SituationalQuestionRule() {
			public String id() {
				return "HIGH";
			}

			public int priority() {
				return 1;
			}

			public boolean matches(SituationalQuestionContext context) {
				return true;
			}
		};
		SituationalQuestionContext context = new SituationalQuestionContext(draftWithPainIndex(6), Optional.empty());
		SituationalQuestionSelector selector = new SituationalQuestionSelector(List.of(low, high), properties);

		assertThat(selector.select(context, Map.of(), today)).contains("HIGH");
	}

}
