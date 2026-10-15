package com.atopy.Atopass.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

class ReportPdfGeneratorTest {

	private final ReportHtmlBuilder htmlBuilder = new ReportHtmlBuilder();
	private final ReportPdfGenerator generator = new ReportPdfGenerator();

	private String textOf(byte[] pdf) throws Exception {
		try (PDDocument document = PDDocument.load(pdf)) {
			return new PDFTextStripper().getText(document);
		}
	}

	@Test
	void PDF_파일을_만든다() {
		byte[] pdf = generator.generate(htmlBuilder.build(ReportSamples.content(false, "메모", List.of())));

		assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
		assertThat(pdf.length).isGreaterThan(1000);
	}

	@Test
	void 한글이_깨지지_않고_PDF_글자로_들어간다() throws Exception {
		byte[] pdf = generator.generate(htmlBuilder.build(ReportSamples.content(false, "저녁에 팔이 가려웠음", List.of())));

		String text = textOf(pdf);
		assertThat(text)
			.contains("진료 참고용 피부 기록")
			.contains("부위별 증상 점수 변화")
			.contains("가려움")
			.contains("저녁에 팔이 가려웠음")
			.contains("의료진의 판단을 대체하지 않습니다.");
	}

	@Test
	void 메모의_특수문자도_글자_그대로_들어간다() throws Exception {
		byte[] pdf = generator.generate(htmlBuilder.build(
			ReportSamples.content(false, "<script>alert(1)</script> & \"따옴표\"", List.of())));

		assertThat(textOf(pdf)).contains("<script>alert(1)</script>").contains("& \"따옴표\"");
	}

	@Test
	void 사진을_넣어도_PDF가_만들어진다() throws Exception {
		byte[] pdf = generator.generate(htmlBuilder.build(ReportSamples.content(true, null,
			List.of(new ReportPhoto("image/png", ReportSamples.TINY_PNG)))));

		assertThat(textOf(pdf)).contains("피부 사진");
		try (PDDocument document = PDDocument.load(pdf)) {
			assertThat(document.getNumberOfPages()).isGreaterThanOrEqualTo(1);
		}
	}

	@Test
	void 기록이_많으면_여러_쪽으로_나뉜다() throws Exception {
		ReportContent base = ReportSamples.content(false, null, List.of());
		List<ReportContent.DayEntry> manyDays = new java.util.ArrayList<>();
		for (int i = 0; i < 120; i++) {
			ReportContent.DayEntry day = base.days().get(i % 2);
			manyDays.add(new ReportContent.DayEntry(base.startDate().plusDays(i), day.conditions(),
				day.sleepHours(), day.alcohol(), day.outdoorActivity(), "메모 " + i, List.of()));
		}
		ReportContent content = new ReportContent(base.startDate(), base.endDate(), base.generatedDate(),
			manyDays.size(), false, base.symptomSummaries(), manyDays, base.lifestyle());

		byte[] pdf = generator.generate(htmlBuilder.build(content));

		try (PDDocument document = PDDocument.load(pdf)) {
			assertThat(document.getNumberOfPages()).isGreaterThan(3);
		}
	}

}
