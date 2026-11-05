package com.atopy.Atopass.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.atopy.Atopass.domain.chatbot.dto.response.SituationalAnswerItem;
import com.atopy.Atopass.domain.medication.dto.response.MedicationResponse;
import com.atopy.Atopass.domain.medication.entity.Medication;
import com.atopy.Atopass.domain.medication.entity.MedicationSource;
import com.atopy.Atopass.domain.medication.entity.MedicationTime;
import com.atopy.Atopass.domain.medication.entity.MedicationType;
import com.atopy.Atopass.domain.record.entity.BodyPart;
import com.atopy.Atopass.domain.record.entity.SkinSign;
import com.atopy.Atopass.domain.report.service.ReportContent.DayEntry;
import com.atopy.Atopass.domain.report.service.ReportContent.LifestyleSummary;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

class ReportHtmlBuilderTest {

	private final ReportHtmlBuilder builder = new ReportHtmlBuilder();

	@Test
	void 사용자가_입력한_특수문자는_이스케이프한다() {
		assertThat(ReportHtmlBuilder.escape("<b>굵게</b> & \"따옴표\" '작은따옴표'"))
			.isEqualTo("&lt;b&gt;굵게&lt;/b&gt; &amp; &quot;따옴표&quot; &#39;작은따옴표&#39;");
	}

	@Test
	void 줄바꿈은_유지하고_허용되지_않는_제어문자는_버린다() {
		assertThat(ReportHtmlBuilder.escape("첫 줄\r\n둘째 줄\u0000끝")).isEqualTo("첫 줄<br/>둘째 줄끝");
		assertThat(ReportHtmlBuilder.escape(null)).isEmpty();
	}

	@Test
	void 만든_문서는_올바른_XML이다() throws Exception {
		String html = builder.build(ReportSamples.content(false, "<script>alert(1)</script> & 메모", List.of()));

		parseAsXml(html);
	}

	@Test
	void 불편함_지수_요약과_심해진_날의_약_복용_나타난_증상을_보여_준다() {
		LocalDate start = LocalDate.of(2026, 3, 1);
		List<DayEntry> days = List.of(
			new DayEntry(start, new EnumMap<>(BodyPart.class), null, null, null, null, List.of(), 0, null, List.of()),
			new DayEntry(start.plusDays(1), new EnumMap<>(BodyPart.class), null, null, null, null, List.of(), 7,
				true, List.of(SkinSign.XEROSIS, SkinSign.EXCORIATION)));
		ReportContent content = new ReportContent(start, start.plusDays(30), start.plusDays(31), 2, false,
			ReportContentFactory.flareDays(days), days, new ReportContent.LifestyleSummary(null, 0, 0, 0, Map.of()));

		String html = builder.build(content);

		assertThat(html)
			// 0도 기록한 값이므로 평균에 들어간다: (0 + 7) / 2
			.contains("불편함 지수 (평균 · 최고)").contains("3.5<small> · 최고 7</small>")
			.contains("불편함 지수 0점 → 7점 (+7)")
			.contains("약 복용").contains("나타난 증상 건조증, 긁은 자국");
	}

	@Test
	void 불편함_지수를_기록한_날이_없으면_타일에_기록_없음이라고_보여주고_그래프도_그리지_않는다() {
		String html = builder.build(ReportSamples.content(false, "메모", List.of()));

		assertThat(html)
			.contains("<p class=\"tile-label\">불편함 지수 (평균 · 최고)</p><p class=\"tile-value\">기록 없음</p>")
			.doesNotContain("불편함 지수 (0~10) 그래프");
	}

	@Test
	void 불편함_지수를_기록했으면_기간_흐름_비교에_그래프를_넣는다() {
		LocalDate start = LocalDate.of(2026, 3, 1);
		List<DayEntry> days = List.of(
			new DayEntry(start, new EnumMap<>(BodyPart.class), null, null, null, null, List.of(), 2, null, List.of()));
		ReportContent content = new ReportContent(start, start.plusDays(30), start.plusDays(31), 1, false,
			List.of(), days, new ReportContent.LifestyleSummary(null, 0, 0, 0, Map.of()));

		String html = builder.build(content);

		assertThat(html)
			.contains("<h2>기간 흐름 비교</h2>")
			.contains("<p class=\"chart-title\">불편함 지수 (0~10)</p>")
			.contains("alt=\"불편함 지수 (0~10) 그래프\" src=\"data:image/png;base64,")
			// 수면 시간은 기록하지 않았으니 둘을 나란히 두는 표가 아니라 혼자 하나만 나온다
			.doesNotContain("table class=\"chart-pair\"")
			.doesNotContain("수면 시간 (0~12시간)");
	}

	@Test
	void 불편함_지수와_수면_시간이_모두_있으면_나란히_둔다() {
		LocalDate start = LocalDate.of(2026, 3, 1);
		List<DayEntry> days = List.of(
			new DayEntry(start, new EnumMap<>(BodyPart.class), new java.math.BigDecimal("6.0"), null, null, null,
				List.of(), 2, null, List.of()));
		ReportContent content = new ReportContent(start, start.plusDays(6), start.plusDays(7), 1, false,
			List.of(), days, new ReportContent.LifestyleSummary(6.0, 1, 0, 0, Map.of()));

		String html = builder.build(content);

		assertThat(html)
			.contains("table class=\"chart-pair\"")
			.contains("불편함 지수 (0~10)")
			.contains("수면 시간 (0~12시간)");
	}

	@Test
	void 필요한_구역과_면책_문구가_들어_있다() {
		String html = builder.build(ReportSamples.content(false, "약을 먹지 않았음", List.of()));

		assertThat(html)
			.contains("진료 참고용 피부 기록")
			.contains("2026.03.01 ~ 2026.03.31")
			.contains("증상이 갑자기 심해진 날")
			.contains("특이사항 및 메모")
			.contains("의료진의 판단을 대체하지 않습니다.")
			// 기간 전체를 요약한 표는 넣지 않는다.
			.doesNotContain("날짜별 피부 상태 기록")
			.doesNotContain("부위별 증상 점수 변화");
	}

	@Test
	void 갑자기_심해진_날에는_오른_점수와_당일_전날_생활을_함께_보여준다() {
		String html = builder.build(ReportSamples.content(false, "회식", List.of()));

		assertThat(html)
			.contains("<h3>2026.03.02 (월)</h3>")
			.contains("팔 불편함 4점 → 7점 (+3)")
			.contains("<span class=\"chip-label\">당일</span> <b>음주 있음</b>")
			.contains("<span class=\"chip-label\">전날</span> 수면 6시간 · 음주 없음 · 야외활동 보통")
			.contains("당일: 회식");
	}

	@Test
	void 같은_날_여러_부위가_함께_심해지면_배지를_모두_보여준다() {
		LocalDate start = LocalDate.of(2026, 3, 1);
		Map<BodyPart, Map<com.atopy.Atopass.domain.record.entity.Symptom, Integer>> day0 =
			new EnumMap<>(BodyPart.class);
		day0.put(BodyPart.ARM, new EnumMap<>(Map.of(com.atopy.Atopass.domain.record.entity.Symptom.PAIN, 1)));
		day0.put(BodyPart.LEG, new EnumMap<>(Map.of(com.atopy.Atopass.domain.record.entity.Symptom.PAIN, 1)));
		Map<BodyPart, Map<com.atopy.Atopass.domain.record.entity.Symptom, Integer>> day1 =
			new EnumMap<>(BodyPart.class);
		day1.put(BodyPart.ARM, new EnumMap<>(Map.of(com.atopy.Atopass.domain.record.entity.Symptom.PAIN, 5)));
		day1.put(BodyPart.LEG, new EnumMap<>(Map.of(com.atopy.Atopass.domain.record.entity.Symptom.PAIN, 6)));
		List<DayEntry> days = List.of(
			new DayEntry(start, day0, null, null, null, null, List.of()),
			new DayEntry(start.plusDays(1), day1, null, null, null, null, List.of()));
		ReportContent content = new ReportContent(start, start.plusDays(1), start.plusDays(2), 2, false,
			ReportContentFactory.flareDays(days), days, new ReportContent.LifestyleSummary(null, 0, 0, 0, Map.of()));

		String html = builder.build(content);

		assertThat(html)
			.contains("<span class=\"badge\">팔 불편함 1점 → 5점 (+4)</span>")
			.contains("<span class=\"badge\">다리 불편함 1점 → 6점 (+5)</span>");
	}

	@Test
	void 챗봇_답은_심해진_날_카드에_제목과_함께_보여_주고_이스케이프하며_특이사항_표에는_다시_넣지_않는다() throws Exception {
		LocalDate start = LocalDate.of(2026, 3, 1);
		List<SituationalAnswerItem> answers = List.of(
			new SituationalAnswerItem("P01", "고통이 심해진 때", "어제부터"),
			new SituationalAnswerItem("C02", "진료 때 물어볼 것", "연고 <계속> 써도 될까요?"));
		List<DayEntry> days = List.of(
			new DayEntry(start, new EnumMap<>(BodyPart.class), null, null, null, null, List.of(), 2, null, List.of()),
			new DayEntry(start.plusDays(1), new EnumMap<>(BodyPart.class), null, null, null, null, List.of(), 7, null,
				List.of(), answers));
		ReportContent content = new ReportContent(start, start.plusDays(30), start.plusDays(31), 2, false,
			ReportContentFactory.flareDays(days), days, new ReportContent.LifestyleSummary(null, 0, 0, 0, Map.of()));

		String html = builder.build(content);

		String lines = "고통이 심해진 때: 어제부터<br/>진료 때 물어볼 것: 연고 &lt;계속&gt; 써도 될까요?";
		assertThat(html)
			.contains("챗봇 답 · " + lines)
			// 이 날은 심해진 날 카드에서 이미 보여 줬으니 특이사항 표에는 다시 넣지 않는다.
			.doesNotContain("<td class=\"nowrap\">2026.03.02</td>")
			.contains("기록된 특이사항이 없습니다.");
		parseAsXml(html);
	}

	@Test
	void 심해진_날이_없으면_없다고_알려_준다() {
		ReportContent base = ReportSamples.content(false, null, List.of());
		ReportContent calm = new ReportContent(base.startDate(), base.endDate(), base.generatedDate(),
			base.recordCount(), false, List.of(), base.days(), base.lifestyle());

		assertThat(builder.build(calm)).contains("이 기간에는 증상이 갑자기 심해진 날이 없습니다.");
	}

	@Test
	void 나타난_증상이_있으면_많이_나타난_순서로_막대를_보여준다() {
		LocalDate start = LocalDate.of(2026, 3, 1);
		List<DayEntry> days = List.of(
			day(start, List.of(SkinSign.ITCHING)),
			day(start.plusDays(1), List.of(SkinSign.ITCHING)),
			day(start.plusDays(2), List.of(SkinSign.ITCHING, SkinSign.XEROSIS)));
		ReportContent content = new ReportContent(start, start.plusDays(2), start.plusDays(3), 3, false,
			List.of(), days, new ReportContent.LifestyleSummary(null, 0, 0, 0, Map.of()));

		String html = builder.build(content);

		assertThat(html).contains("<h2>기간 중 나타난 증상</h2>");
		assertThat(html.indexOf("가려움")).isLessThan(html.indexOf("건조증"));
		assertThat(html).contains("3일").contains("1일");
	}

	@Test
	void 나타난_증상이_없으면_증상_분포_구역이_없다() {
		String html = builder.build(ReportSamples.content(false, null, List.of()));

		assertThat(html).doesNotContain("<h2>기간 중 나타난 증상</h2>");
	}

	@Test
	void 복용_중인_약이_있으면_목록과_복용_현황을_보여준다() {
		LocalDate start = LocalDate.of(2026, 10, 1);
		List<SituationalAnswerItem> missedReason = List.of(
			new SituationalAnswerItem("P05", "약을 못 챙긴 이유", "깜빡함"));
		List<DayEntry> days = List.of(
			new DayEntry(start, new EnumMap<>(BodyPart.class), null, null, null, null, List.of(), null, true,
				List.of(), List.of()),
			new DayEntry(start.plusDays(1), new EnumMap<>(BodyPart.class), null, null, null, null, List.of(), null,
				false, List.of(), missedReason));
		List<MedicationResponse> medications = List.of(
			MedicationResponse.from(Medication.builder()
				.memberId(1L).name("세티리진정").type(MedicationType.ORAL).dose("1정")
				.times(List.of(MedicationTime.EVENING)).timesText(null)
				.startDate(LocalDate.of(2026, 9, 1)).endDate(null).memo(null)
				.otherCondition(false).source(MedicationSource.MYPAGE).build()),
			MedicationResponse.from(Medication.builder()
				.memberId(1L).name("항히스타민제").type(MedicationType.ORAL).dose("1정")
				.times(List.of(MedicationTime.BEDTIME)).timesText(null)
				.startDate(LocalDate.of(2026, 9, 1)).endDate(LocalDate.of(2026, 9, 20)).memo(null)
				.otherCondition(false).source(MedicationSource.SIGNUP).build()));
		ReportContent content = new ReportContent(start, start.plusDays(1), start.plusDays(2), 2, false,
			List.of(), days, new ReportContent.LifestyleSummary(null, 0, 0, 0, Map.of()), medications);

		String html = builder.build(content);

		assertThat(html)
			.contains("<h2>약 정보</h2>")
			.contains("기간 중 복용한 약")
			.contains("세티리진정").contains("먹는 약").contains("1정").contains("저녁")
			.contains("항히스타민제").contains("<span class=\"med-stopped\">(중단)</span>")
			.contains("복용 현황")
			.contains("기록한 2일 중 1일 복용, 1일 미복용입니다.")
			.contains("약을 못 챙겼어요 · 깜빡함");
	}

	@Test
	void 복용_약도_복용_기록도_없으면_약_정보_구역이_없다() {
		String html = builder.build(ReportSamples.content(false, null, List.of()));

		assertThat(html).doesNotContain("<h2>약 정보</h2>");
	}

	@Test
	void 사진_포함을_요청하지_않으면_사진_구역이_없다() {
		String html = builder.build(ReportSamples.content(false, null,
			List.of(new ReportPhoto("image/png", ReportSamples.TINY_PNG))));

		assertThat(html).doesNotContain("피부 사진</h2>").doesNotContain("class=\"photo\"");
	}

	@Test
	void 사진_포함을_요청하면_사진을_문서_안에_넣는다() {
		String html = builder.build(ReportSamples.content(true, null,
			List.of(new ReportPhoto("image/png", ReportSamples.TINY_PNG))));

		assertThat(html).contains("<h2>피부 사진</h2>").contains("src=\"data:image/png;base64,iVBORw0KGgo");
	}

	@Test
	void 사진을_요청했지만_사진이_없으면_없다고_알려_준다() {
		String html = builder.build(ReportSamples.content(true, null, List.of()));

		assertThat(html).contains("이 기간에 등록된 사진이 없습니다.");
	}

	private static DayEntry day(LocalDate date, List<SkinSign> skinSigns) {
		return new DayEntry(date, new EnumMap<>(BodyPart.class), null, null, null, null, List.of(), null, null,
			skinSigns);
	}

	private static void parseAsXml(String html) throws Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		// DTD를 내려받지 않고 읽는다.
		factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		factory.newDocumentBuilder().parse(new InputSource(new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8))));
	}

}
