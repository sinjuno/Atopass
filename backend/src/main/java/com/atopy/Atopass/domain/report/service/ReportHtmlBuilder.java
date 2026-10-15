package com.atopy.Atopass.domain.report.service;

import com.atopy.Atopass.domain.record.entity.BodyPart;
import com.atopy.Atopass.domain.record.entity.OutdoorActivity;
import com.atopy.Atopass.domain.record.entity.Symptom;
import com.atopy.Atopass.domain.report.service.ReportContent.DayEntry;
import com.atopy.Atopass.domain.report.service.ReportContent.LifestyleSummary;
import com.atopy.Atopass.domain.report.service.ReportContent.SymptomSummary;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 보고서 내용을 PDF로 바꾸기 직전의 XHTML로 만든다. PDF 변환기는 올바른 XML 형식만 읽으므로
 * 사용자가 입력한 모든 글(메모 등)은 반드시 이스케이프한다.
 */
@Component
class ReportHtmlBuilder {

	static final String TITLE = "진료 참고용 피부 기록";
	static final String DISCLAIMER =
		"이 기록은 환자가 입력한 상태 변화를 정리한 자료이며, 의료진의 판단을 대체하지 않습니다.";

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.KOREA);

	String build(ReportContent content) {
		StringBuilder html = new StringBuilder(8192);
		html.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
			.append("<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" ")
			.append("\"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">\n")
			.append("<html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"ko\"><head>")
			.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\"/>")
			.append("<title>").append(TITLE).append("</title>")
			.append("<style>").append(css()).append("</style></head><body>");

		header(html, content);
		symptomSection(html, content.symptomSummaries());
		daySection(html, content.days());
		lifestyleSection(html, content.lifestyle());
		notesSection(html, content.days());
		if (content.includePhotos()) {
			photoSection(html, content.days());
		}
		html.append("<p class=\"disclaimer\">").append(escape(DISCLAIMER)).append("</p>");
		html.append("</body></html>");
		return html.toString();
	}

	private void header(StringBuilder html, ReportContent content) {
		html.append("<h1>").append(TITLE).append("</h1>")
			.append("<table class=\"meta\">")
			.append(row("조회 기간", DATE.format(content.startDate()) + " ~ " + DATE.format(content.endDate())))
			.append(row("기록 수", content.recordCount() + "일"))
			.append(row("작성일", DATE.format(content.generatedDate())))
			.append("</table>");
	}

	private void symptomSection(StringBuilder html, List<SymptomSummary> summaries) {
		html.append("<h2>부위별 증상 점수 변화</h2>")
			.append("<p class=\"hint\">점수는 환자가 느낀 주관적인 정도(0~10)입니다. 의료적인 중증도가 아닙니다.</p>");
		if (summaries.isEmpty()) {
			html.append("<p>기록된 증상 점수가 없습니다.</p>");
			return;
		}
		html.append("<table class=\"grid\"><tr><th>부위</th><th>증상</th><th>기록 수</th><th>평균</th>")
			.append("<th>최저</th><th>최고</th><th>처음 → 마지막</th></tr>");
		for (SymptomSummary s : summaries) {
			html.append("<tr><td>").append(escape(s.bodyPart().label())).append("</td>")
				.append("<td>").append(escape(s.symptom().label())).append("</td>")
				.append("<td class=\"num\">").append(s.count()).append("</td>")
				.append("<td class=\"num\">").append(String.format(Locale.ROOT, "%.1f", s.average())).append("</td>")
				.append("<td class=\"num\">").append(s.min()).append("</td>")
				.append("<td class=\"num\">").append(s.max()).append("</td>")
				.append("<td>").append(s.firstScore()).append("점 (").append(DATE.format(s.firstDate()))
				.append(") → ").append(s.lastScore()).append("점 (").append(DATE.format(s.lastDate()))
				.append(")</td></tr>");
		}
		html.append("</table>");
	}

	private void daySection(StringBuilder html, List<DayEntry> days) {
		html.append("<h2>날짜별 피부 상태 기록</h2>")
			.append("<table class=\"grid\"><tr><th>날짜</th><th>부위별 증상 점수</th><th>수면</th>")
			.append("<th>음주</th><th>야외활동</th></tr>");
		for (DayEntry day : days) {
			html.append("<tr><td class=\"nowrap\">").append(DATE.format(day.date())).append("</td><td>")
				.append(conditionsText(day.conditions())).append("</td>")
				.append("<td>").append(day.sleepHours() == null ? "-"
					: day.sleepHours().stripTrailingZeros().toPlainString() + "시간").append("</td>")
				.append("<td>").append(day.alcohol() == null ? "-" : day.alcohol() ? "있음" : "없음").append("</td>")
				.append("<td>").append(outdoorText(day.outdoorActivity())).append("</td></tr>");
		}
		html.append("</table>");
	}

	private void lifestyleSection(StringBuilder html, LifestyleSummary lifestyle) {
		html.append("<h2>수면 및 생활 정보</h2><table class=\"meta\">")
			.append(row("평균 수면", lifestyle.averageSleepHours() == null ? "기록 없음"
				: String.format(Locale.ROOT, "%.1f시간 (%d일 기록)", lifestyle.averageSleepHours(),
					lifestyle.sleepRecordedDays())))
			.append(row("음주", lifestyle.alcoholRecordedDays() == 0 ? "기록 없음"
				: lifestyle.alcoholDays() + "일 (" + lifestyle.alcoholRecordedDays() + "일 기록 중)"))
			.append(row("야외활동", lifestyle.outdoorDays().isEmpty() ? "기록 없음"
				: lifestyle.outdoorDays().entrySet().stream()
					.map(e -> outdoorText(e.getKey()) + " " + e.getValue() + "일")
					.collect(Collectors.joining(", "))))
			.append("</table>");
	}

	private void notesSection(StringBuilder html, List<DayEntry> days) {
		List<DayEntry> withNotes = days.stream().filter(d -> d.notes() != null).toList();
		html.append("<h2>특이사항 및 메모</h2>");
		if (withNotes.isEmpty()) {
			html.append("<p>기록된 특이사항이 없습니다.</p>");
			return;
		}
		html.append("<table class=\"grid\"><tr><th>날짜</th><th>내용</th></tr>");
		for (DayEntry day : withNotes) {
			html.append("<tr><td class=\"nowrap\">").append(DATE.format(day.date())).append("</td><td>")
				.append(escape(day.notes())).append("</td></tr>");
		}
		html.append("</table>");
	}

	private void photoSection(StringBuilder html, List<DayEntry> days) {
		List<DayEntry> withPhotos = days.stream().filter(d -> !d.photos().isEmpty()).toList();
		html.append("<h2>피부 사진</h2>");
		if (withPhotos.isEmpty()) {
			html.append("<p>이 기간에 등록된 사진이 없습니다.</p>");
			return;
		}
		for (DayEntry day : withPhotos) {
			html.append("<div class=\"photos\"><p class=\"photo-date\">").append(DATE.format(day.date()))
				.append("</p>");
			for (ReportPhoto photo : day.photos()) {
				html.append("<img class=\"photo\" alt=\"피부 사진\" src=\"data:")
					.append(escape(photo.contentType())).append(";base64,")
					.append(Base64.getEncoder().encodeToString(photo.data())).append("\"/>");
			}
			html.append("</div>");
		}
	}

	private String conditionsText(Map<BodyPart, Map<Symptom, Integer>> conditions) {
		return conditions.entrySet().stream()
			.map(e -> escape(e.getKey().label()) + ": " + e.getValue().entrySet().stream()
				.map(s -> escape(s.getKey().label()) + " " + s.getValue())
				.collect(Collectors.joining(", ")))
			.collect(Collectors.joining("<br/>"));
	}

	private String outdoorText(OutdoorActivity activity) {
		if (activity == null) {
			return "-";
		}
		return switch (activity) {
			case LOW -> "적음";
			case MEDIUM -> "보통";
			case HIGH -> "많음";
		};
	}

	private String row(String label, String value) {
		return "<tr><th>" + escape(label) + "</th><td>" + escape(value) + "</td></tr>";
	}

	/** XML에서 의미가 있는 문자를 모두 바꾼다. 줄바꿈은 보이도록 유지한다. */
	static String escape(String text) {
		if (text == null) {
			return "";
		}
		StringBuilder out = new StringBuilder(text.length() + 16);
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			switch (c) {
				case '&' -> out.append("&amp;");
				case '<' -> out.append("&lt;");
				case '>' -> out.append("&gt;");
				case '"' -> out.append("&quot;");
				case '\'' -> out.append("&#39;");
				case '\n' -> out.append("<br/>");
				case '\r' -> {
				}
				default -> {
					// XML 1.0에서 허용되지 않는 제어 문자는 버린다.
					if (c >= 0x20 || c == '\t') {
						out.append(c);
					}
				}
			}
		}
		return out.toString();
	}

	private String css() {
		return """
			@page { size: A4; margin: 18mm 15mm 20mm 15mm; @bottom-center { content: counter(page) " / " counter(pages); font-size: 9pt; color: #666666; } }
			body { font-family: 'NanumGothic', sans-serif; font-size: 10pt; color: #222222; }
			h1 { font-size: 20pt; margin: 0 0 8pt 0; color: #1f3a5f; }
			h2 { font-size: 13pt; margin: 18pt 0 6pt 0; color: #1f3a5f; border-bottom: 1px solid #1f3a5f; padding-bottom: 3pt; }
			p { margin: 4pt 0; }
			.hint { font-size: 8.5pt; color: #666666; }
			table { width: 100%; border-collapse: collapse; margin-top: 4pt; }
			table.meta th { width: 22%; text-align: left; font-weight: normal; color: #555555; padding: 2pt 4pt; }
			table.meta td { padding: 2pt 4pt; }
			table.grid th { background-color: #e8eef6; border: 1px solid #b8c4d6; padding: 4pt; text-align: left; }
			table.grid td { border: 1px solid #c8d0dc; padding: 4pt; vertical-align: top; }
			td.num { text-align: right; }
			td.nowrap { white-space: nowrap; }
			tr { page-break-inside: avoid; }
			.photos { page-break-inside: avoid; margin-bottom: 8pt; }
			.photo-date { font-weight: bold; }
			img.photo { max-width: 160pt; max-height: 160pt; margin: 2pt 6pt 2pt 0; }
			.disclaimer { margin-top: 24pt; padding: 8pt; background-color: #f3f5f8; border: 1px solid #c8d0dc; font-size: 9pt; color: #444444; }
			""";
	}

}
