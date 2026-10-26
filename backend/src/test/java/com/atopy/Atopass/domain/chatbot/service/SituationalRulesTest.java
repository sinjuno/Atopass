package com.atopy.Atopass.domain.chatbot.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.atopy.Atopass.domain.record.entity.BodyPart;
import com.atopy.Atopass.domain.record.entity.SkinSign;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** 상황 질문 P02~P07의 조건 판정과 순위 */
class SituationalRulesTest {

	private final JsonMapper mapper = JsonMapper.builder().build();
	private final SituationalQuestionProperties properties = defaultProperties();
	private final LocalDate today = LocalDate.of(2026, 10, 23);

	static SituationalQuestionProperties defaultProperties() {
		return new SituationalQuestionProperties(2, 3, BigDecimal.valueOf(5), 4, 7, 4, 6, 3, 3, 3,
			new SituationalQuestionProperties.Priority(1, 2, 3, 4, 5, 6, 7));
	}

	private ChatDraft draft(String json) {
		return ChatDraft.fromJson(mapper, json);
	}

	private SituationalDay day(int daysAgo, Boolean medication, Set<BodyPart> parts, Set<SkinSign> signs) {
		return new SituationalDay(today.minusDays(daysAgo), 3, BigDecimal.valueOf(7), medication, parts, signs);
	}

	private SituationalDay painDay(int daysAgo, int painIndex) {
		return new SituationalDay(today.minusDays(daysAgo), painIndex, BigDecimal.valueOf(7), null, Set.of(), Set.of());
	}

	private ChatDraft painDraft(int painIndex) {
		return draft("{\"painIndex\": " + painIndex + "}");
	}

	private SituationalQuestionContext context(ChatDraft draft, SituationalDay... days) {
		return new SituationalQuestionContext(draft, today, List.of(days));
	}

	/** 1일 전부터 차례로, 약을 먹은 날 takenDays일 뒤에 안 먹은 날 notTakenDays일 */
	private SituationalDay[] medicationDays(int takenDays, int notTakenDays) {
		List<SituationalDay> days = new ArrayList<>();
		int daysAgo = 1;
		for (int i = 0; i < takenDays; i++) {
			days.add(day(daysAgo++, true, Set.of(), Set.of()));
		}
		for (int i = 0; i < notTakenDays; i++) {
			days.add(day(daysAgo++, false, Set.of(), Set.of()));
		}
		return days.toArray(SituationalDay[]::new);
	}

	// ── P02 ──

	@Test
	void P02는_어제_없던_증상이_오늘_있으면_그_증상_이름으로_묻는다() {
		ChatDraft draft = draft("""
			{"painIndex": 4, "skinSigns": ["ITCHING", "ERYTHEMA"],
			 "conditions": {"NECK": {"pain": 4}}}""");
		SituationalDay yesterday = day(1, null, Set.of(BodyPart.NECK), Set.of(SkinSign.ITCHING));

		assertThat(new NewSignOrPartRule(properties).matches(context(draft, yesterday))).isTrue();
		assertThat(NewSignOrPartRule.subject(draft, Optional.of(yesterday))).contains("홍반");
	}

	@Test
	void P02는_증상이_같아도_새_부위가_있으면_부위_이름으로_묻는다() {
		ChatDraft draft = draft("""
			{"painIndex": 4, "skinSigns": ["ITCHING"],
			 "conditions": {"NECK": {"pain": 4}, "FACE": {"pain": 4}}}""");
		SituationalDay yesterday = day(1, null, Set.of(BodyPart.NECK), Set.of(SkinSign.ITCHING));

		assertThat(NewSignOrPartRule.subject(draft, Optional.of(yesterday))).contains("얼굴 증상");
	}

	@Test
	void P02는_부위에_적은_증상도_어제_하루_증상과_비교한다() {
		// 어제 기록의 증상에는 부위 증상이 합쳐져 있으므로, 오늘 부위에만 적은 증상도 어제 있었으면 새것이 아니다.
		ChatDraft draft = draft("""
			{"painIndex": 4, "skinSigns": [],
			 "conditions": {"NECK": {"pain": 4, "skinSigns": ["XEROSIS"]}}}""");
		SituationalDay yesterday = day(1, null, Set.of(BodyPart.NECK), Set.of(SkinSign.XEROSIS));

		assertThat(new NewSignOrPartRule(properties).matches(context(draft, yesterday))).isFalse();
	}

	@Test
	void P02는_어제_기록이_없으면_나오지_않는다() {
		ChatDraft draft = draft("""
			{"painIndex": 4, "skinSigns": ["ITCHING"], "conditions": {"NECK": {"pain": 4}}}""");
		NewSignOrPartRule rule = new NewSignOrPartRule(properties);

		assertThat(rule.matches(context(draft))).isFalse();
		// 그저께 기록만 있어도 어제 기록이 없는 것과 같다.
		assertThat(rule.matches(context(draft, day(2, null, Set.of(), Set.of())))).isFalse();
	}

	@Test
	void 질문에_넣는_이름은_받침에_맞춰_이_가를_붙인다() {
		assertThat(NewSignOrPartRule.withSubjectParticle("홍반")).isEqualTo("홍반이");
		assertThat(NewSignOrPartRule.withSubjectParticle("배")).isEqualTo("배가");
		assertThat(NewSignOrPartRule.withSubjectParticle("부종/구진(결절)")).isEqualTo("부종/구진(결절)이");
		assertThat(NewSignOrPartRule.withSubjectParticle("오른다리 하박")).isEqualTo("오른다리 하박이");
	}

	// ── P04 ──

	@Test
	void P04는_수면_5시간_이하이고_고통_지수_4_이상일_때만_나온다() {
		ShortSleepRule rule = new ShortSleepRule(properties);

		assertThat(rule.matches(context(draft("{\"painIndex\": 4, \"sleepHours\": 5}")))).isTrue();
		assertThat(rule.matches(context(draft("{\"painIndex\": 4, \"sleepHours\": 5.5}")))).isFalse();
		assertThat(rule.matches(context(draft("{\"painIndex\": 3, \"sleepHours\": 4}")))).isFalse();
		assertThat(rule.matches(context(draft("{\"painIndex\": 6}")))).isFalse();
	}

	// ── P05 ──

	@Test
	void P05는_최근_7일_중_4일_이상_먹었는데_오늘_안_먹었으면_나온다() {
		MissedMedicationRule rule = new MissedMedicationRule(properties);
		ChatDraft notTaken = draft("{\"painIndex\": 3, \"medication\": false}");

		assertThat(rule.matches(context(notTaken, medicationDays(4, 3)))).isTrue();
		assertThat(rule.matches(context(notTaken, medicationDays(3, 4)))).isFalse();
	}

	@Test
	void P05는_오늘_먹었거나_답하지_않았으면_나오지_않는다() {
		MissedMedicationRule rule = new MissedMedicationRule(properties);

		assertThat(rule.matches(context(draft("{\"painIndex\": 3, \"medication\": true}"), medicationDays(7, 0))))
			.isFalse();
		assertThat(rule.matches(context(draft("{\"painIndex\": 3}"), medicationDays(7, 0)))).isFalse();
	}

	@Test
	void P05는_7일보다_오래된_복용은_세지_않는다() {
		MissedMedicationRule rule = new MissedMedicationRule(properties);
		ChatDraft notTaken = draft("{\"painIndex\": 3, \"medication\": false}");

		assertThat(rule.matches(context(notTaken,
			day(9, true, Set.of(), Set.of()), day(8, true, Set.of(), Set.of()),
			day(3, true, Set.of(), Set.of()), day(2, true, Set.of(), Set.of()), day(1, true, Set.of(), Set.of()))))
			.isFalse();
	}

	// ── P03 ──

	@Test
	void P03은_고통_지수_6_이상이_오늘_포함_3일_연속이면_나온다() {
		HighPainStreakRule rule = new HighPainStreakRule(properties);

		assertThat(rule.matches(context(painDraft(6), painDay(2, 7), painDay(1, 6)))).isTrue();
		// 오늘이 5점이면 아니다.
		assertThat(rule.matches(context(painDraft(5), painDay(2, 7), painDay(1, 6)))).isFalse();
		// 어제가 5점이면 연속이 끊긴다.
		assertThat(rule.matches(context(painDraft(8), painDay(2, 7), painDay(1, 5)))).isFalse();
	}

	@Test
	void P03은_사이에_기록하지_않은_날이_있으면_연속이_아니다() {
		HighPainStreakRule rule = new HighPainStreakRule(properties);

		// 그저께 기록이 없고 사흘 전·어제만 높다.
		assertThat(rule.matches(context(painDraft(7), painDay(3, 7), painDay(1, 7)))).isFalse();
		assertThat(rule.matches(context(painDraft(7), painDay(1, 7)))).isFalse();
	}

	// ── P06 ──

	@Test
	void P06은_직전_기록이_3일_이상_전이면_나온다() {
		LongGapRule rule = new LongGapRule(properties);

		assertThat(rule.matches(context(painDraft(3), painDay(3, 3)))).isTrue();
		assertThat(rule.matches(context(painDraft(3), painDay(5, 3), painDay(2, 3)))).isFalse();
		// 최근 기록 목록 밖(예: 20일 전)이어도 직전 기록 날짜로 판정한다.
		assertThat(rule.matches(new SituationalQuestionContext(painDraft(3), today, List.of(), today.minusDays(20))))
			.isTrue();
	}

	@Test
	void P06은_처음_기록하는_날에는_나오지_않는다() {
		assertThat(new LongGapRule(properties).matches(context(painDraft(3)))).isFalse();
	}

	// ── P07 ──

	@Test
	void P07은_고통_지수가_어제보다_3_이상_내리면_나온다() {
		PainDropRule rule = new PainDropRule(properties);

		assertThat(rule.matches(context(painDraft(4), painDay(1, 7)))).isTrue();
		assertThat(rule.matches(context(painDraft(5), painDay(1, 7)))).isFalse();
		// 올랐으면 아니다.
		assertThat(rule.matches(context(painDraft(7), painDay(1, 4)))).isFalse();
	}

	@Test
	void P07은_어제_기록이_없으면_나오지_않는다() {
		PainDropRule rule = new PainDropRule(properties);

		assertThat(rule.matches(context(painDraft(2)))).isFalse();
		assertThat(rule.matches(context(painDraft(2), painDay(2, 9)))).isFalse();
	}

	// ── 순위 ──

	@Test
	void P03_P06_P07의_순위는_P03_P06_P07_순서다() {
		SituationalQuestionSelector selector = new SituationalQuestionSelector(List.of(
			new PainDropRule(properties), new LongGapRule(properties), new HighPainStreakRule(properties)), properties);

		// 3일 연속 높음 + 오래전 기록은 없음 → P03
		assertThat(selector.select(context(painDraft(7), painDay(2, 7), painDay(1, 7)), Map.of(), today))
			.contains("P03");
		// 직전 기록이 4일 전 → P06 (어제 기록이 없으니 P07은 아님)
		assertThat(selector.select(context(painDraft(2), painDay(4, 9)), Map.of(), today)).contains("P06");
		// 어제보다 3점 내림 → P07, 최근에 나왔으면 아무것도 없음
		assertThat(selector.select(context(painDraft(2), painDay(1, 6)), Map.of(), today)).contains("P07");
		assertThat(selector.select(context(painDraft(2), painDay(1, 6)), Map.of("P07", today.minusDays(2)), today))
			.isEmpty();
	}

	@Test
	void 지난_기록은_P03_연속_일수보다_짧게_읽지_않는다() {
		SituationalQuestionProperties longStreak = new SituationalQuestionProperties(2, 3, BigDecimal.valueOf(5), 4,
			3, 2, 6, 10, 3, 3, new SituationalQuestionProperties.Priority(1, 2, 3, 4, 5, 6, 7));

		assertThat(longStreak.historyDays()).isEqualTo(9);
		assertThat(properties.historyDays()).isEqualTo(7);
	}

	@Test
	void 여러_조건이_맞으면_순위가_높은_P01이_P03보다_먼저다() {
		SituationalQuestionSelector selector = new SituationalQuestionSelector(List.of(
			new HighPainStreakRule(properties), new PainJumpRule(properties)), properties);
		SituationalQuestionContext context = context(painDraft(9), painDay(2, 6), painDay(1, 6));

		assertThat(selector.select(context, Map.of(), today)).contains("P01");
		assertThat(selector.select(context, Map.of("P01", today.minusDays(1)), today)).contains("P03");
	}


	@Test
	void 여러_조건이_맞으면_P02_P04_P05_순서로_하나만_고르고_최근에_나온_것은_건너뛴다() {
		// 새 증상(홍반) + 수면 4시간·고통 4(어제 3이라 P01은 아님) + 최근 4일 복용 후 오늘 안 먹음 → P02·P04·P05 모두 조건에 맞는다
		ChatDraft draft = draft("""
			{"painIndex": 4, "sleepHours": 4, "medication": false, "skinSigns": ["ERYTHEMA"],
			 "conditions": {"NECK": {"pain": 4}}}""");
		SituationalQuestionContext context = context(draft,
			day(4, true, Set.of(), Set.of()), day(3, true, Set.of(), Set.of()), day(2, true, Set.of(), Set.of()),
			day(1, true, Set.of(BodyPart.NECK), Set.of()));
		SituationalQuestionSelector selector = new SituationalQuestionSelector(List.of(
			new MissedMedicationRule(properties), new ShortSleepRule(properties),
			new NewSignOrPartRule(properties), new PainJumpRule(properties)), properties);

		assertThat(selector.select(context, Map.of(), today)).contains("P02");
		assertThat(selector.select(context, Map.of("P02", today.minusDays(1)), today)).contains("P04");
		assertThat(selector.select(context, Map.of("P02", today.minusDays(1), "P04", today.minusDays(2)), today))
			.contains("P05");
	}

}
