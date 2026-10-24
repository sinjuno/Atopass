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

/** 상황 질문 P02·P04·P05의 조건 판정과 순위 */
class SituationalRulesTest {

	private final JsonMapper mapper = JsonMapper.builder().build();
	private final SituationalQuestionProperties properties = defaultProperties();
	private final LocalDate today = LocalDate.of(2026, 10, 23);

	static SituationalQuestionProperties defaultProperties() {
		return new SituationalQuestionProperties(2, 3, BigDecimal.valueOf(5), 4, 7, 4,
			new SituationalQuestionProperties.Priority(1, 2, 4, 5));
	}

	private ChatDraft draft(String json) {
		return ChatDraft.fromJson(mapper, json);
	}

	private SituationalDay day(int daysAgo, Boolean medication, Set<BodyPart> parts, Set<SkinSign> signs) {
		return new SituationalDay(today.minusDays(daysAgo), 3, BigDecimal.valueOf(7), medication, parts, signs);
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

	// ── 순위 ──

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
