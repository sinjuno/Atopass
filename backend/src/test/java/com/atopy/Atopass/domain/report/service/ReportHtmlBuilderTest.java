package com.atopy.Atopass.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.atopy.Atopass.domain.chatbot.dto.response.SituationalAnswerItem;
import com.atopy.Atopass.domain.record.entity.BodyPart;
import com.atopy.Atopass.domain.record.entity.SkinSign;
import com.atopy.Atopass.domain.report.service.ReportContent.DayEntry;
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

		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		// DTD를 내려받지 않고 읽는다.
		factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		factory.newDocumentBuilder().parse(new InputSource(new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8))));
	}

	@Test
	void 고통_지수_요약과_심해진_날의_약_복용_나타난_증상을_보여_준다() {
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
			.contains("불편함 지수 (기간 평균·최고)").contains("평균 3.5 · 최고 7 (2일 기록)")
			.contains("불편함 지수 <b>0점 → 7점</b>")
			.contains("약 복용").contains("나타난 증상 건조증, 긁은 자국");
	}

	@Test
	void 고통_지수를_기록한_날이_없으면_고통_지수_행과_그래프를_넣지_않는다() {
		String html = builder.build(ReportSamples.content(false, "메모", List.of()));

		assertThat(html).doesNotContain("불편함 지수 (기간 평균·최고)").doesNotContain("기간 불편함 지수 그래프");
	}

	@Test
	void 고통_지수를_기록했으면_오른쪽_위에_기간_고통_지수_그래프를_넣는다() {
		LocalDate start = LocalDate.of(2026, 3, 1);
		List<DayEntry> days = List.of(
			new DayEntry(start, new EnumMap<>(BodyPart.class), null, null, null, null, List.of(), 2, null, List.of()));
		ReportContent content = new ReportContent(start, start.plusDays(30), start.plusDays(31), 1, false,
			List.of(), days, new ReportContent.LifestyleSummary(null, 0, 0, 0, Map.of()));

		String html = builder.build(content);

		assertThat(html)
			.contains("<td class=\"header-chart\">")
			.contains("기간 불편함 지수 (0~10)")
			.contains("alt=\"기간 불편함 지수 그래프\" src=\"data:image/png;base64,")
			.contains("03.01").contains("03.31");
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
			.contains("팔 고통 <b>4점 → 7점</b> (03.01 기록 대비 +3)")
			.contains("<th>당일</th><td><b>음주 있음</b></td>")
			.contains("<th>전날</th><td>수면 6시간 · 음주 없음 · 야외활동 보통</td>")
			.contains("<th>메모</th><td>당일: 회식</td>");
	}

	@Test
	void 챗봇_답은_특이사항_표와_심해진_날에_제목과_함께_보여_주고_이스케이프한다() throws Exception {
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
			// 특이사항이 없어도 챗봇 답이 있는 날은 특이사항 표에 나온다.
			.contains("<td class=\"nowrap\">2026.03.02</td><td><div class=\"answers\">" + lines + "</div></td>")
			.contains("<th>챗봇 답</th><td>" + lines + "</td>")
			.doesNotContain("기록된 특이사항이 없습니다.");
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
		factory.newDocumentBuilder().parse(new InputSource(new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8))));
	}

	@Test
	void 심해진_날이_없으면_없다고_알려_준다() {
		ReportContent base = ReportSamples.content(false, null, List.of());
		ReportContent calm = new ReportContent(base.startDate(), base.endDate(), base.generatedDate(),
			base.recordCount(), false, List.of(), base.days(), base.lifestyle());

		assertThat(builder.build(calm)).contains("이 기간에는 증상이 갑자기 심해진 날이 없습니다.");
	}

	@Test
	void 사진_포함을_요청하지_않으면_사진_구역이_없다() {
		String html = builder.build(ReportSamples.content(false, null,
			List.of(new ReportPhoto("image/png", ReportSamples.TINY_PNG))));

		assertThat(html).doesNotContain("피부 사진</h2>").doesNotContain("data:image/png");
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

}
