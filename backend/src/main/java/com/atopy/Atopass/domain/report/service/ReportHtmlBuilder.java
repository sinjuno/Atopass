package com.atopy.Atopass.domain.report.service;

import com.atopy.Atopass.domain.chatbot.service.LinkedQuestion;
import com.atopy.Atopass.domain.record.dto.response.DailyCheckResponse;
import com.atopy.Atopass.domain.record.entity.OutdoorActivity;
import com.atopy.Atopass.domain.record.entity.SkinSign;
import com.atopy.Atopass.domain.report.service.ReportContent.DayEntry;
import com.atopy.Atopass.domain.report.service.ReportContent.FlareDay;
import com.atopy.Atopass.domain.report.service.ReportContent.LifestyleSummary;
import com.atopy.Atopass.domain.report.service.ReportContent.ScoreJump;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
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
	private static final DateTimeFormatter DATE_WITH_DAY = DateTimeFormatter.ofPattern("yyyy.MM.dd (E)", Locale.KOREA);
	private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("MM.dd", Locale.KOREA);
	/** 기간 평균보다 이만큼(시간) 이상 적게 자면 "평소보다 짧은 수면"으로 본다 */
	private static final double SHORT_SLEEP_GAP = 1.5;

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
		flareSection(html, content);
		patternSection(html, content);
		notesSection(html, content.days());
		if (content.includePhotos()) {
			photoSection(html, content.days());
		}
		html.append("<p class=\"disclaimer\">").append(escape(DISCLAIMER)).append("</p>");
		html.append("</body></html>");
		return html.toString();
	}

	/**
	 * 제목·기간 요약. 고통 지수를 기록한 날이 있으면 오른쪽 위에 기간 고통 지수 그래프를 함께 둔다.
	 * 표 안의 width:100% 요소가 float와 겹칠 수 있어 float 대신 두 칸짜리 표로 나눈다.
	 */
	private void header(StringBuilder html, ReportContent content) {
		byte[] chart = ReportPainChart.png(content.startDate(), content.endDate(), content.days());
		if (chart != null) {
			html.append("<table class=\"header\"><tr><td>");
		}
		html.append("<h1>").append(TITLE).append("</h1>")
			.append("<table class=\"meta\">")
			.append(row("조회 기간", DATE.format(content.startDate()) + " ~ " + DATE.format(content.endDate())))
			.append(row("기록 수", content.recordCount() + "일"))
			.append(row("증상이 갑자기 심해진 날", content.flareDays().size() + "일"));
		String pain = painSummaryText(content.days());
		if (pain != null) {
			html.append(row("고통 지수 (기간 평균·최고)", pain));
		}
		html.append(row("평소 생활 (기간 평균)", usualLifestyleText(content.lifestyle())))
			.append(row("작성일", DATE.format(content.generatedDate())))
			.append("</table>");
		if (chart != null) {
			html.append("</td><td class=\"header-chart\">");
			painChart(html, chart, content);
			html.append("</td></tr></table>");
		}
	}

	/** 고통 지수 그래프 상자: 제목, 왼쪽 눈금(10·5·0), 그래프 이미지, 아래 시작·끝 날짜 */
	private void painChart(StringBuilder html, byte[] png, ReportContent content) {
		html.append("<div class=\"chart-box\"><p class=\"chart-title\">기간 고통 지수 (0~10)</p>")
			.append("<table class=\"chart\"><tr><td class=\"chart-scale\">")
			.append("<div class=\"scale-top\">10</div><div class=\"scale-mid\">5</div><div>0</div></td><td>")
			.append("<img class=\"chart\" alt=\"기간 고통 지수 그래프\" src=\"data:image/png;base64,")
			.append(Base64.getEncoder().encodeToString(png)).append("\"/></td></tr>")
			.append("<tr><td></td><td><table class=\"chart-dates\"><tr><td>").append(SHORT_DATE.format(content.startDate()))
			.append("</td><td class=\"end\">").append(SHORT_DATE.format(content.endDate())).append("</td></tr></table></td></tr>")
			.append("</table><p class=\"chart-note\">점선은 5점, 큰 점은 가장 높은 날입니다. 기록이 없는 날은 선이 끊깁니다.</p></div>");
	}

	/** 고통 지수를 기록한 날의 평균과 최고. 기록한 날이 없으면 null. 0도 기록한 값이다. */
	private String painSummaryText(List<DayEntry> days) {
		List<Integer> scores = days.stream().map(DayEntry::painIndex).filter(p -> p != null).toList();
		if (scores.isEmpty()) {
			return null;
		}
		double average = scores.stream().mapToInt(Integer::intValue).average().orElse(0);
		int max = scores.stream().mapToInt(Integer::intValue).max().orElse(0);
		return String.format(Locale.ROOT, "평균 %.1f · 최고 %d (%d일 기록)", average, max, scores.size());
	}

	private void flareSection(StringBuilder html, ReportContent content) {
		html.append("<h2>증상이 갑자기 심해진 날</h2>")
			.append("<p class=\"hint\">같은 부위·증상 또는 고통 지수가 직전 기록보다 ").append(ReportContentFactory.FLARE_JUMP)
			.append("점 이상 오른 날입니다. 점수는 환자가 느낀 주관적인 정도(0~10)이며 의료적인 중증도가 아닙니다. ")
			.append("평소와 달랐던 생활은 굵게 표시했습니다.</p>");
		if (content.flareDays().isEmpty()) {
			html.append("<p>이 기간에는 증상이 갑자기 심해진 날이 없습니다.</p>");
			return;
		}
		Double usualSleep = content.lifestyle().averageSleepHours();
		for (FlareDay flare : content.flareDays()) {
			DayEntry day = flare.day();
			html.append("<div class=\"flare\"><h3>").append(DATE_WITH_DAY.format(day.date())).append("</h3><ul>");
			for (ScoreJump jump : flare.jumps()) {
				html.append("<li>").append(escape(jumpLabel(jump))).append(" <b>").append(jump.previousScore())
					.append("점 → ").append(jump.score()).append("점</b> (").append(SHORT_DATE.format(jump.previousDate()))
					.append(" 기록 대비 +").append(jump.score() - jump.previousScore()).append(")</li>");
			}
			html.append("</ul><table class=\"meta\">")
				.append(rawRow("당일", lifestyleText(day, usualSleep)))
				.append(rawRow("전날", flare.dayBefore() == null ? "기록 없음" : lifestyleText(flare.dayBefore(), usualSleep)));
			String notes = flareNotes(flare);
			if (!notes.isEmpty()) {
				html.append(rawRow("메모", notes));
			}
			html.append("</table></div>");
		}
	}

	/**
	 * 심해진 날(당일 또는 전날)에 평소와 다른 생활이 몇 번 겹쳤는지 센다.
	 * 원인을 판단하지 않고, 함께 나타난 횟수와 기간 전체 횟수만 나란히 보여 준다.
	 */
	private void patternSection(StringBuilder html, ReportContent content) {
		List<FlareDay> flares = content.flareDays();
		if (flares.size() < 2) {
			return;
		}
		Double usualSleep = content.lifestyle().averageSleepHours();
		List<String> lines = new ArrayList<>();
		addPattern(lines, flares, "음주가", d -> Boolean.TRUE.equals(d.alcohol()),
			"기간 전체 음주 " + content.lifestyle().alcoholDays() + "일");
		addPattern(lines, flares, "평소보다 짧은 수면이", d -> isShortSleep(d, usualSleep), null);
		addPattern(lines, flares, "많은 야외활동이", d -> d.outdoorActivity() == OutdoorActivity.HIGH,
			"기간 전체 " + content.lifestyle().outdoorDays().getOrDefault(OutdoorActivity.HIGH, 0) + "일");
		if (lines.isEmpty()) {
			return;
		}
		html.append("<h2>반복해서 보인 점</h2>")
			.append("<p class=\"hint\">함께 나타난 횟수일 뿐이며, 원인을 뜻하지 않습니다.</p><ul>");
		lines.forEach(line -> html.append("<li>").append(escape(line)).append("</li>"));
		html.append("</ul>");
	}

	private void addPattern(List<String> lines, List<FlareDay> flares, String label, Predicate<DayEntry> matches,
		String total) {
		long count = flares.stream()
			.filter(f -> matches.test(f.day()) || (f.dayBefore() != null && matches.test(f.dayBefore())))
			.count();
		if (count >= 2) {
			lines.add("갑자기 심해진 " + flares.size() + "일 중 " + count + "일은 당일 또는 전날 " + label + " 있었습니다."
				+ (total == null ? "" : " (" + total + ")"));
		}
	}

	/**
	 * 수면·음주·야외활동 중 기록한 것만 보여 주고, 평소와 다른 것은 굵게 표시한다 (이스케이프된 XHTML)
	 * 하루 점검 항목은 진료 때 읽기 쉽도록 의학 용어를 함께 적는다. (예: 소양감(가려움) 7점)
	 */
	private String lifestyleText(DayEntry day, Double usualSleep) {
		List<String> parts = new ArrayList<>();
		DailyCheckResponse check = day.dailyCheck();
		if (check != null && check.skinTrend() != null) {
			parts.add("피부 상태 " + check.skinTrend().label());
		}
		if (check != null && check.itchScore() != null) {
			parts.add("소양감(가려움) " + check.itchScore() + "점");
		}
		if (check != null && check.sleepDisturbed() != null) {
			parts.add(check.sleepDisturbed() ? "<b>수면 장애(가려워서 잠 설침)</b>" : "수면 장애 없음");
		}
		if (day.sleepHours() != null) {
			String sleep = "수면 " + day.sleepHours().stripTrailingZeros().toPlainString() + "시간";
			parts.add(isShortSleep(day, usualSleep)
				? "<b>" + sleep + String.format(Locale.ROOT, " (평소 %.1f시간)", usualSleep) + "</b>" : sleep);
		}
		if (day.alcohol() != null) {
			parts.add(day.alcohol() ? "<b>음주 있음</b>" : "음주 없음");
		}
		if (day.outdoorActivity() != null) {
			String outdoor = "야외활동 " + outdoorText(day.outdoorActivity());
			parts.add(day.outdoorActivity() == OutdoorActivity.HIGH ? "<b>" + outdoor + "</b>" : outdoor);
		}
		if (check != null && check.moisturizer() != null) {
			parts.add("보습 관리 " + (check.moisturizer() ? "함" : "안 함"));
		}
		if (check != null && check.medicationUse() != null) {
			parts.add("처방 약 사용 " + check.medicationUse().label());
		} else if (day.medication() != null) {
			parts.add(day.medication() ? "약 복용" : "약 미복용");
		}
		if (day.skinSigns() != null && !day.skinSigns().isEmpty()) {
			parts.add("나타난 증상 " + escape(day.skinSigns().stream().map(SkinSign::label)
				.collect(java.util.stream.Collectors.joining(", "))));
		}
		if (check != null && check.linkedAnswers() != null) {
			check.linkedAnswers().forEach((id, answer) -> parts.add(escape(
				LinkedQuestion.find(id).map(LinkedQuestion::label).orElse(id) + " " + answer)));
		}
		return parts.isEmpty() ? "생활 기록 없음" : String.join(" · ", parts);
	}

	private String jumpLabel(ScoreJump jump) {
		return jump.bodyPart() == null || jump.symptom() == null
			? "고통 지수" : jump.bodyPart().label() + " " + jump.symptom().label();
	}

	private boolean isShortSleep(DayEntry day, Double usualSleep) {
		return usualSleep != null && day.sleepHours() != null
			&& day.sleepHours().doubleValue() <= usualSleep - SHORT_SLEEP_GAP;
	}

	private String flareNotes(FlareDay flare) {
		List<String> notes = new ArrayList<>();
		if (flare.dayBefore() != null && flare.dayBefore().notes() != null) {
			notes.add("전날: " + escape(flare.dayBefore().notes()));
		}
		if (flare.day().notes() != null) {
			notes.add("당일: " + escape(flare.day().notes()));
		}
		return String.join("<br/>", notes);
	}

	private String usualLifestyleText(LifestyleSummary lifestyle) {
		List<String> parts = new ArrayList<>();
		if (lifestyle.averageSleepHours() != null) {
			parts.add(String.format(Locale.ROOT, "수면 %.1f시간", lifestyle.averageSleepHours()));
		}
		if (lifestyle.alcoholRecordedDays() > 0) {
			parts.add("음주 " + lifestyle.alcoholDays() + "일");
		}
		return parts.isEmpty() ? "기록 없음" : String.join(" · ", parts);
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
		return rawRow(label, escape(value));
	}

	/** @param html 이미 이스케이프된 XHTML */
	private String rawRow(String label, String html) {
		return "<tr><th>" + escape(label) + "</th><td>" + html + "</td></tr>";
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
			table.header { margin-top: 0; }
			table.header td { vertical-align: top; padding: 0; }
			td.header-chart { width: 214pt; padding-left: 10pt; }
			.chart-box { border: 1px solid #c8d0dc; padding: 5pt 6pt 4pt 6pt; }
			.chart-title { font-size: 8.5pt; color: #1f3a5f; margin: 0 0 3pt 0; }
			table.chart { margin-top: 0; }
			table.chart td { padding: 0; vertical-align: top; }
			td.chart-scale { width: 12pt; padding-right: 3pt; font-size: 7pt; line-height: 8pt; color: #888888; text-align: right; }
			.scale-top, .scale-mid { height: 25pt; }
			img.chart { width: 174pt; height: 58pt; }
			table.chart-dates { margin-top: 1pt; }
			table.chart-dates td { font-size: 7pt; color: #888888; }
			table.chart-dates td.end { text-align: right; }
			.chart-note { font-size: 7pt; color: #888888; margin: 2pt 0 0 0; }
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
			h3 { font-size: 11pt; margin: 0 0 4pt 0; color: #1f3a5f; }
			ul { margin: 2pt 0 4pt 0; padding-left: 14pt; }
			li { margin: 1pt 0; }
			.flare { page-break-inside: avoid; margin: 8pt 0; padding: 6pt 8pt; border: 1px solid #c8d0dc; border-left: 3px solid #c0392b; }
			.photos { page-break-inside: avoid; margin-bottom: 8pt; }
			.photo-date { font-weight: bold; }
			img.photo { max-width: 160pt; max-height: 160pt; margin: 2pt 6pt 2pt 0; }
			.disclaimer { margin-top: 24pt; padding: 8pt; background-color: #f3f5f8; border: 1px solid #c8d0dc; font-size: 9pt; color: #444444; }
			""";
	}

}
