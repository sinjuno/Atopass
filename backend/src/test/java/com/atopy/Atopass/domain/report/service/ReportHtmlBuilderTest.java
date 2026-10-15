package com.atopy.Atopass.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
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
	void 필요한_구역과_면책_문구가_들어_있다() {
		String html = builder.build(ReportSamples.content(false, "약을 먹지 않았음", List.of()));

		assertThat(html)
			.contains("진료 참고용 피부 기록")
			.contains("2026.03.01 ~ 2026.03.31")
			.contains("부위별 증상 점수 변화")
			.contains("날짜별 피부 상태 기록")
			.contains("수면 및 생활 정보")
			.contains("특이사항 및 메모")
			.contains("의료진의 판단을 대체하지 않습니다.");
	}

	@Test
	void 부위와_증상_점수를_한글로_보여준다() {
		String html = builder.build(ReportSamples.content(false, null, List.of()));

		assertThat(html)
			.contains("팔: 가려움 4, 붉어짐 3")
			.contains("<td>팔</td><td>가려움</td>")
			.contains("<td class=\"num\">5.0</td>")
			.contains("4점 (2026.03.01) → 6점 (2026.03.02)");
	}

	@Test
	void 기록하지_않은_값은_빈칸_표시로_두고_임의의_값을_채우지_않는다() {
		String html = builder.build(ReportSamples.content(false, null, List.of()));

		// 둘째 날: 수면과 야외활동은 기록하지 않았다.
		assertThat(html).contains("<td>-</td><td>있음</td><td>-</td>");
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
