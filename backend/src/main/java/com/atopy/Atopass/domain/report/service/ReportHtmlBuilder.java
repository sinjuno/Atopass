package com.atopy.Atopass.domain.report.service;

import com.atopy.Atopass.domain.chatbot.dto.response.SituationalAnswerItem;
import com.atopy.Atopass.domain.medication.dto.response.MedicationResponse;
import com.atopy.Atopass.domain.medication.entity.MedicationTime;
import com.atopy.Atopass.domain.medication.entity.MedicationType;
import com.atopy.Atopass.domain.record.entity.OutdoorActivity;
import com.atopy.Atopass.domain.record.entity.SkinSign;
import com.atopy.Atopass.domain.report.service.ReportContent.DayEntry;
import com.atopy.Atopass.domain.report.service.ReportContent.FlareDay;
import com.atopy.Atopass.domain.report.service.ReportContent.ScoreJump;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumMap;
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
	private static final DateTimeFormatter DATE_WITH_DAY = DateTimeFormatter.ofPattern("yyyy.MM.dd (E)", Locale.KOREA);
	private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("MM.dd", Locale.KOREA);
	/** 기간 평균보다 이만큼(시간) 이상 적게 자면 "평소보다 짧은 수면"으로 본다 */
	private static final double SHORT_SLEEP_GAP = 1.5;

	private static final Map<MedicationType, String> MEDICATION_TYPE_LABELS = Map.of(
		MedicationType.ORAL, "먹는 약", MedicationType.TOPICAL, "바르는 약", MedicationType.OTHER, "기타");
	private static final Map<MedicationTime, String> MEDICATION_TIME_LABELS = Map.of(
		MedicationTime.MORNING, "아침", MedicationTime.LUNCH, "점심",
		MedicationTime.EVENING, "저녁", MedicationTime.BEDTIME, "자기 전");

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
		statTiles(html, content);
		chartSection(html, content);
		symptomDistribution(html, content);
		flareSection(html, content);
		medicationSection(html, content);
		List<LocalDate> flareDates = content.flareDays().stream().map(f -> f.day().date()).toList();
		notesSection(html, content.days(), flareDates);
		if (content.includePhotos()) {
			photoSection(html, content.days());
		}
		html.append("<p class=\"disclaimer\">").append(escape(DISCLAIMER)).append("</p>");
		html.append("</body></html>");
		return html.toString();
	}

	/** 제목·조회 기간·작성일. 면책 문구는 끝에도 한 번 더 두지만, 바로 눈에 띄도록 머리말에도 작게 둔다. */
	private void header(StringBuilder html, ReportContent content) {
		html.append("<table class=\"header\"><tr><td><h1>").append(TITLE).append("</h1>")
			.append("<p class=\"period\">")
			.append(escape(DATE.format(content.startDate()) + " ~ " + DATE.format(content.endDate())
				+ " · 작성일 " + DATE.format(content.generatedDate())))
			.append("</p></td><td class=\"header-note\">").append(escape(DISCLAIMER)).append("</td></tr></table>");
	}

	/** 기간을 한눈에 보는 요약 타일 4개: 기록 수·심해진 날·불편함 지수·약 복용 */
	private void statTiles(StringBuilder html, ReportContent content) {
		long periodDays = ChronoUnit.DAYS.between(content.startDate(), content.endDate()) + 1;
		long medicationDays = content.days().stream().filter(d -> d.medication() != null).count();
		long takenDays = content.days().stream().filter(d -> Boolean.TRUE.equals(d.medication())).count();

		html.append("<table class=\"tiles\"><tr>")
			.append(tile(false, "기록 수", content.recordCount() + "<small>일 / " + periodDays + "일</small>"))
			.append(tile(!content.flareDays().isEmpty(), "증상이 갑자기 심해진 날",
				content.flareDays().size() + "<small>일</small>"))
			.append(tile(false, "불편함 지수 (평균 · 최고)", painTileValue(content.days())))
			.append(tile(false, "약 복용", medicationDays == 0 ? "기록 없음"
				: takenDays + "<small>일 / " + medicationDays + "일</small>"))
			.append("</tr></table>");
	}

	private String tile(boolean alert, String label, String valueHtml) {
		return "<td class=\"" + (alert ? "tile tile-alert" : "tile") + "\">"
			+ "<p class=\"tile-label\">" + escape(label) + "</p>"
			+ "<p class=\"tile-value\">" + valueHtml + "</p></td>";
	}

	/** 불편함 지수를 기록한 날의 평균과 최고. 기록한 날이 없으면 "기록 없음". 0도 기록한 값이다. */
	private String painTileValue(List<DayEntry> days) {
		List<Integer> scores = days.stream().map(DayEntry::painIndex).filter(p -> p != null).toList();
		if (scores.isEmpty()) {
			return "기록 없음";
		}
		double average = scores.stream().mapToInt(Integer::intValue).average().orElse(0);
		int max = scores.stream().mapToInt(Integer::intValue).max().orElse(0);
		return String.format(Locale.ROOT, "%.1f<small> · 최고 %d</small>", average, max);
	}

	/**
	 * 불편함 지수와 수면 시간 그래프를 나란히 둬, 수면이 짧았던 날과 불편함이 심했던 날이 겹치는지 볼 수 있게 한다.
	 * 둘 다 기록이 없으면 구역 자체를 넣지 않는다.
	 */
	private void chartSection(StringBuilder html, ReportContent content) {
		byte[] painPng = ReportPainChart.png(content.startDate(), content.endDate(), content.days());
		byte[] sleepPng = ReportSleepChart.png(content.startDate(), content.endDate(), content.days());
		if (painPng == null && sleepPng == null) {
			return;
		}
		String startDate = SHORT_DATE.format(content.startDate());
		String endDate = SHORT_DATE.format(content.endDate());
		html.append("<h2>기간 흐름 비교</h2>")
			.append("<p class=\"hint\">불편함 지수와 수면 시간을 같은 기간으로 나란히 두어, ")
			.append("수면이 짧았던 날과 불편함이 심했던 날이 겹치는지 바로 볼 수 있습니다.</p>");
		if (painPng != null && sleepPng != null) {
			html.append("<table class=\"chart-pair\"><tr><td class=\"chart-cell\">");
			chartBox(html, "불편함 지수 (0~10)", painPng, 10, 5, startDate, endDate,
				painMinMaxText(content.days()), "점선은 5점, 큰 점은 가장 높은 날");
			html.append("</td><td class=\"chart-cell\">");
			chartBox(html, "수면 시간 (0~12시간)", sleepPng, 12, 6, startDate, endDate,
				sleepMinMaxText(content.days()), "점선은 6시간, 큰 점은 가장 적게 잔 날");
			html.append("</td></tr></table>");
		} else if (painPng != null) {
			chartBox(html, "불편함 지수 (0~10)", painPng, 10, 5, startDate, endDate,
				painMinMaxText(content.days()), "점선은 5점, 큰 점은 가장 높은 날");
		} else {
			chartBox(html, "수면 시간 (0~12시간)", sleepPng, 12, 6, startDate, endDate,
				sleepMinMaxText(content.days()), "점선은 6시간, 큰 점은 가장 적게 잔 날");
		}
	}

	/** 그래프에 그려진 기간 중 불편함 지수의 최저·최고. 기록한 날이 없으면 null (호출부에서 이미 걸러짐) */
	private String painMinMaxText(List<DayEntry> days) {
		List<Integer> scores = days.stream().map(DayEntry::painIndex).filter(p -> p != null).toList();
		if (scores.isEmpty()) {
			return null;
		}
		int min = scores.stream().mapToInt(Integer::intValue).min().orElse(0);
		int max = scores.stream().mapToInt(Integer::intValue).max().orElse(0);
		return "최저 " + min + "점 · 최고 " + max + "점";
	}

	/** 그래프에 그려진 기간 중 수면 시간의 최저·최고 */
	private String sleepMinMaxText(List<DayEntry> days) {
		List<java.math.BigDecimal> hours = days.stream().map(DayEntry::sleepHours).filter(h -> h != null).toList();
		if (hours.isEmpty()) {
			return null;
		}
		java.math.BigDecimal min = hours.stream().min(java.util.Comparator.naturalOrder()).orElseThrow();
		java.math.BigDecimal max = hours.stream().max(java.util.Comparator.naturalOrder()).orElseThrow();
		return "최저 " + min.stripTrailingZeros().toPlainString() + "시간 · 최고 "
			+ max.stripTrailingZeros().toPlainString() + "시간";
	}

	/** 그래프 상자: 제목, 최저·최고, 왼쪽 눈금(최댓값·가운데값·0), 그래프 이미지, 아래 시작·끝 날짜 */
	private void chartBox(StringBuilder html, String title, byte[] png, int maxScale, int midScale,
		String startDate, String endDate, String minMax, String note) {
		html.append("<div class=\"chart-box\"><p class=\"chart-title\">").append(escape(title)).append("</p>");
		if (minMax != null) {
			html.append("<p class=\"chart-minmax\">").append(escape(minMax)).append("</p>");
		}
		html.append("<table class=\"chart\"><tr><td class=\"chart-scale\">")
			.append("<div class=\"scale-top\">").append(maxScale).append("</div><div class=\"scale-mid\">")
			.append(midScale).append("</div><div>0</div></td><td>")
			.append("<img class=\"chart\" alt=\"").append(escape(title)).append(" 그래프\" src=\"data:image/png;base64,")
			.append(Base64.getEncoder().encodeToString(png)).append("\"/></td></tr>")
			.append("<tr><td></td><td><table class=\"chart-dates\"><tr><td>").append(startDate)
			.append("</td><td class=\"end\">").append(endDate).append("</td></tr></table></td></tr></table>")
			.append("<p class=\"chart-note\">").append(escape(note)).append("</p></div>");
	}

	/** 기간 중 나타난 증상 7가지를 많이 나타난 순서로 막대로 보여 준다. 한 번도 없었으면 구역을 넣지 않는다. */
	private void symptomDistribution(StringBuilder html, ReportContent content) {
		Map<SkinSign, Long> counts = new EnumMap<>(SkinSign.class);
		for (DayEntry day : content.days()) {
			if (day.skinSigns() == null) {
				continue;
			}
			for (SkinSign sign : day.skinSigns()) {
				counts.merge(sign, 1L, Long::sum);
			}
		}
		List<Map.Entry<SkinSign, Long>> sorted = counts.entrySet().stream()
			.sorted(Map.Entry.<SkinSign, Long>comparingByValue().reversed()
				.thenComparing(e -> e.getKey().ordinal()))
			.toList();
		if (sorted.isEmpty()) {
			return;
		}
		long max = sorted.get(0).getValue();
		html.append("<h2>기간 중 나타난 증상</h2>")
			.append("<p class=\"hint\">기록한 ").append(content.recordCount())
			.append("일 중 이 증상이 나타났다고 답한 날의 수입니다.</p><div class=\"bars\">");
		for (Map.Entry<SkinSign, Long> entry : sorted) {
			int percent = (int) Math.round(entry.getValue() * 100.0 / max);
			html.append("<table class=\"bar-row\"><tr><td class=\"bar-label\">")
				.append(escape(entry.getKey().label())).append("</td><td class=\"bar-track\">")
				.append("<table class=\"bar-fill-table\"><tr><td class=\"bar-fill\" style=\"width:")
				.append(percent).append("%;\"></td><td></td></tr></table></td><td class=\"bar-count\">")
				.append(entry.getValue()).append("일</td></tr></table>");
		}
		html.append("</div>");
	}

	/** 증상이 갑자기 심해진 날을 카드로 보여 준다. 메모·챗봇 답은 여기에만 두고 특이사항 표에는 다시 넣지 않는다. */
	private void flareSection(StringBuilder html, ReportContent content) {
		html.append("<h2>증상이 갑자기 심해진 날</h2>")
			.append("<p class=\"hint\">같은 부위·증상 또는 불편함 지수가 직전 기록보다 ").append(ReportContentFactory.FLARE_JUMP)
			.append("점 이상 오른 날입니다. 점수는 환자가 느낀 주관적인 정도(0~10)이며 의료적인 중증도가 아닙니다. ")
			.append("메모와 챗봇 답은 여기에만 있고, 아래 특이사항 표에는 다시 넣지 않습니다.</p>");
		if (content.flareDays().isEmpty()) {
			html.append("<p>이 기간에는 증상이 갑자기 심해진 날이 없습니다.</p>");
			return;
		}
		Double usualSleep = content.lifestyle().averageSleepHours();
		for (FlareDay flare : content.flareDays()) {
			DayEntry day = flare.day();
			html.append("<div class=\"flare\"><table class=\"flare-head\"><tr><td><h3>")
				.append(DATE_WITH_DAY.format(day.date())).append("</h3></td><td class=\"flare-badge-cell\">");
			for (ScoreJump jump : flare.jumps()) {
				html.append("<span class=\"badge\">").append(escape(jumpLabel(jump))).append(' ')
					.append(jump.previousScore()).append("점 → ").append(jump.score()).append("점 (+")
					.append(jump.score() - jump.previousScore()).append(")</span>");
			}
			html.append("</td></tr></table>")
				.append("<p class=\"chips\"><span class=\"chip-label\">당일</span> ")
				.append(lifestyleText(day, usualSleep)).append("</p>")
				.append("<p class=\"chips\"><span class=\"chip-label\">전날</span> ")
				.append(flare.dayBefore() == null ? "기록 없음" : lifestyleText(flare.dayBefore(), usualSleep))
				.append("</p>");
			String notes = flareNotes(flare);
			String answers = day.situationalAnswers().isEmpty() ? null : answersText(day.situationalAnswers());
			if (!notes.isEmpty() || answers != null) {
				html.append("<div class=\"flare-body\">");
				if (!notes.isEmpty()) {
					html.append(notes);
				}
				if (answers != null) {
					html.append(notes.isEmpty() ? "" : "<br/>")
						.append("<span class=\"answers\">챗봇 답 · ").append(answers).append("</span>");
				}
				html.append("</div>");
			}
			html.append("</div>");
		}
	}

	/**
	 * 약 정보: 기간과 겹치는 복용 중인 약 목록, 그리고 기록한 날의 복용 현황. 보여 줄 게 하나도 없으면
	 * 구역 전체를 넣지 않는다.
	 */
	private void medicationSection(StringBuilder html, ReportContent content) {
		boolean hasMedications = !content.medications().isEmpty();
		long medicationDays = content.days().stream().filter(d -> d.medication() != null).count();
		if (!hasMedications && medicationDays == 0) {
			return;
		}
		html.append("<h2>약 정보</h2>");
		if (hasMedications) {
			medicationListSection(html, content.medications());
		}
		if (medicationDays > 0) {
			medicationAdherenceSection(html, content.days(), medicationDays);
		}
	}

	/** 환자가 등록한 약 정보(이름·종류·용량·복용 시간·기간)를 표로 보여 준다. 중단한 약도 기간과 겹치면 함께 보여 준다. */
	private void medicationListSection(StringBuilder html, List<MedicationResponse> medications) {
		html.append("<h3 class=\"sub\">기간 중 복용한 약</h3>")
			.append("<p class=\"hint\">환자가 등록한 약 정보이며, 기간과 겹치는 약만 보여 줍니다.</p>")
			.append("<table class=\"grid\"><tr><th>이름</th><th>종류</th><th>용량</th><th>복용 시간</th><th>기간</th></tr>");
		for (MedicationResponse m : medications) {
			String type = MedicationType.fromCode(m.type()).map(MEDICATION_TYPE_LABELS::get).orElse(m.type());
			String times = m.times().stream()
				.map(code -> MedicationTime.fromCode(code).map(MEDICATION_TIME_LABELS::get).orElse(code))
				.collect(Collectors.joining(", "));
			String period = SHORT_DATE.format(m.startDate()) + " ~ "
				+ (m.endDate() == null ? "복용 중" : SHORT_DATE.format(m.endDate()));
			html.append("<tr><td>").append(escape(m.name())).append("</td><td>").append(escape(type))
				.append("</td><td>").append(m.dose() == null ? "-" : escape(m.dose())).append("</td><td>")
				.append(times.isEmpty() ? "-" : escape(times)).append("</td><td>").append(period)
				.append(m.endDate() != null ? " <span class=\"med-stopped\">(중단)</span>" : "")
				.append("</td></tr>");
		}
		html.append("</table>");
	}

	/** 기록한 날 중 약을 못 챙긴 날과 그 이유(상황 질문 P05 답)를 함께 보여 준다. 복용을 권하거나 판단하지 않는다. */
	private void medicationAdherenceSection(StringBuilder html, List<DayEntry> days, long medicationDays) {
		long takenDays = days.stream().filter(d -> Boolean.TRUE.equals(d.medication())).count();
		long missed = medicationDays - takenDays;
		html.append("<h3 class=\"sub\">복용 현황</h3>")
			.append("<p class=\"hint\">기록한 ").append(medicationDays).append("일 중 ").append(takenDays)
			.append("일 복용, ").append(missed).append("일 미복용입니다.</p>");
		for (DayEntry day : days) {
			if (Boolean.FALSE.equals(day.medication())) {
				String reason = day.situationalAnswers().stream()
					.filter(a -> "P05".equals(a.questionId())).map(SituationalAnswerItem::answer).findFirst()
					.orElse(null);
				html.append("<table class=\"note-row\"><tr><td class=\"note-date\">").append(DATE.format(day.date()))
					.append("</td><td class=\"note-text\">약을 못 챙겼어요")
					.append(reason == null ? "" : " · " + escape(reason)).append("</td></tr></table>");
			}
		}
	}

	/** 수면·음주·야외활동 중 기록한 것만 보여 주고, 평소와 다른 것은 굵게 표시한다 (이스케이프된 XHTML) */
	private String lifestyleText(DayEntry day, Double usualSleep) {
		List<String> parts = new ArrayList<>();
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
		if (day.medication() != null) {
			parts.add(day.medication() ? "약 복용" : "약 미복용");
		}
		if (day.skinSigns() != null && !day.skinSigns().isEmpty()) {
			parts.add("나타난 증상 " + escape(day.skinSigns().stream().map(SkinSign::label)
				.collect(Collectors.joining(", "))));
		}
		return parts.isEmpty() ? "생활 기록 없음" : String.join(" · ", parts);
	}

	/**
	 * Symptom.PAIN의 라벨("고통")은 분석 인사이트·챗봇 프롬프트 등 다른 곳에서도 그대로 쓰므로 그 라벨 자체는
	 * 바꾸지 않고, 이 보고서에서 부위별 점수를 부르는 말만 "불편함 지수"와 맞춰 "불편함"으로 통일한다.
	 */
	private String jumpLabel(ScoreJump jump) {
		return jump.bodyPart() == null || jump.symptom() == null
			? "불편함 지수" : jump.bodyPart().label() + " 불편함";
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

	/** 특이사항과, 챗봇이 그날 물어 받은 답(상황 질문·마무리 질문)을 날짜별로 함께 보여 준다.
	 * 증상이 갑자기 심해진 날의 메모·챗봇 답은 위 카드에서 이미 보여 줬으므로 excludeDates로 뺀다(겹침 방지). */
	private void notesSection(StringBuilder html, List<DayEntry> days, List<LocalDate> excludeDates) {
		List<DayEntry> withNotes = days.stream()
			.filter(d -> !excludeDates.contains(d.date()))
			.filter(d -> d.notes() != null || !d.situationalAnswers().isEmpty())
			.toList();
		html.append("<h2>특이사항 및 메모</h2>");
		if (withNotes.isEmpty()) {
			html.append("<p>기록된 특이사항이 없습니다.</p>");
			return;
		}
		html.append("<table class=\"grid\"><tr><th>날짜</th><th>내용</th></tr>");
		for (DayEntry day : withNotes) {
			html.append("<tr><td class=\"nowrap\">").append(DATE.format(day.date())).append("</td><td>");
			if (day.notes() != null) {
				html.append(escape(day.notes()));
			}
			if (!day.situationalAnswers().isEmpty()) {
				html.append("<div class=\"answers\">").append(answersText(day.situationalAnswers())).append("</div>");
			}
			html.append("</td></tr>");
		}
		html.append("</table>");
	}

	/** 챗봇 답을 "제목: 답" 한 줄씩 (이스케이프된 XHTML) */
	private String answersText(List<SituationalAnswerItem> answers) {
		List<String> lines = new ArrayList<>();
		for (SituationalAnswerItem answer : answers) {
			lines.add(escape(answer.title()) + ": " + escape(answer.answer()));
		}
		return String.join("<br/>", lines);
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
			@page { size: A4; margin: 15mm 14mm 18mm 14mm; @bottom-center { content: counter(page) " / " counter(pages); font-size: 8pt; color: #7b877f; } }
			body { font-family: 'NanumGothic', sans-serif; font-size: 9.5pt; line-height: 1.55; color: #26332d; }
			h1 { font-size: 21pt; line-height: 1.25; margin: 0 0 7pt 0; color: #315b47; letter-spacing: -0.4pt; }
			h2 { font-size: 13pt; margin: 16pt 0 6pt 0; color: #315b47; border-bottom: 1px solid #cbd9cc; padding-bottom: 4pt; page-break-after: avoid; }
			h3 { font-size: 11pt; margin: 0; color: #315b47; }
			h3.sub { margin: 10pt 0 4pt 0; page-break-after: avoid; }
			p { margin: 4pt 0; }
			.hint { font-size: 8.5pt; color: #6f7b73; }
			.period { font-size: 9pt; color: #6f7b73; margin: 0; }
			table { width: 100%; border-collapse: collapse; }
			tr { page-break-inside: avoid; }
			table.header td { vertical-align: bottom; padding: 0; }
			td.header-note { text-align: right; font-size: 8pt; color: #9aa89d; }

			table.tiles { border-collapse: separate; border-spacing: 6pt 0; margin: 6pt 0 2pt 0; }
			td.tile { width: 25%; background-color: #f1f4f1; border-radius: 6pt; padding: 8pt 9pt; vertical-align: top; }
			td.tile-alert { background-color: #fbeceb; }
			p.tile-label { font-size: 8pt; color: #64766a; margin: 0 0 3pt 0; }
			td.tile-alert p.tile-label { color: #b23b2e; }
			p.tile-value { font-size: 15pt; font-weight: bold; color: #26332d; margin: 0; }
			td.tile-alert p.tile-value { color: #b23b2e; }
			p.tile-value small { font-size: 8.5pt; font-weight: normal; color: #7c8b80; }

			table.chart-pair { border-collapse: separate; border-spacing: 8pt 0; }
			td.chart-cell { width: 50%; vertical-align: top; padding: 0; }
			.chart-box { border: 1px solid #dce6dc; background-color: #fbfcfa; padding: 7pt 8pt 6pt 8pt; margin-top: 2pt; }
			.chart-title { font-size: 9pt; font-weight: bold; color: #315b47; margin: 0 0 5pt 0; }
			.chart-minmax { font-size: 8pt; color: #4a5b50; margin: -3pt 0 5pt 0; }
			table.chart td { padding: 0; vertical-align: top; }
			td.chart-scale { width: 13pt; padding-right: 3pt; font-size: 7pt; line-height: 8pt; color: #7b877f; text-align: right; }
			.scale-top, .scale-mid { height: 25pt; }
			img.chart { width: 100%; height: 58pt; }
			table.chart-dates { margin-top: 1pt; }
			table.chart-dates td { font-size: 7pt; color: #7b877f; }
			table.chart-dates td.end { text-align: right; }
			.chart-note { font-size: 7pt; color: #7b877f; margin: 4pt 0 0 0; }

			.bars { border: 1px solid #dce5dc; border-radius: 6pt; padding: 8pt 10pt; margin: 2pt 0; }
			table.bar-row { margin: 3pt 0; }
			td.bar-label { width: 70pt; font-size: 9pt; color: #30404F; }
			td.bar-track { padding: 0 6pt; }
			table.bar-fill-table { height: 7pt; background-color: #e7ece6; border-radius: 4pt; }
			td.bar-fill { background-color: #315b47; border-radius: 4pt; }
			td.bar-count { width: 30pt; text-align: right; font-size: 8.5pt; color: #64766a; }

			.flare { page-break-inside: avoid; margin: 8pt 0; padding: 8pt 9pt; border: 1px solid #dce5dc; border-left: 3px solid #c0392b; background-color: #fbfcfa; }
			table.flare-head { margin: 0; }
			td.flare-badge-cell { text-align: right; }
			span.badge { display: inline-block; background-color: #fdeceb; color: #c0392b; font-size: 8.5pt; font-weight: bold; padding: 2pt 7pt; border-radius: 8pt; margin-left: 4pt; }
			p.chips { margin: 5pt 0; font-size: 9pt; }
			span.chip-label { display: inline-block; background-color: #eef1ee; color: #4a5b50; font-size: 8pt; font-weight: bold; padding: 1pt 7pt; border-radius: 8pt; margin-right: 4pt; }
			.flare-body { font-size: 8.5pt; color: #4a5b50; background-color: #f5f7f4; border-radius: 6pt; padding: 6pt 8pt; margin-top: 4pt; }
			.flare-body .answers { color: #64766a; }

			table.grid { border: 1px solid #dce5dc; margin-top: 4pt; }
			table.grid th { background-color: #edf3ec; border: 1px solid #dce5dc; padding: 6pt 7pt; text-align: left; color: #315b47; font-size: 8.5pt; }
			table.grid td { border: 1px solid #e1e8e1; padding: 6pt 7pt; vertical-align: top; }
			td.nowrap { white-space: nowrap; }
			span.med-stopped { color: #9aa89d; font-size: 8pt; }
			.answers { font-size: 8.5pt; color: #58675e; }
			td .answers { margin-top: 3pt; }

			table.note-row { margin: 4pt 0; border: 1px solid #dce5dc; border-radius: 6pt; }
			td.note-date { width: 56pt; padding: 6pt 8pt; font-size: 8.5pt; font-weight: bold; color: #315b47; vertical-align: top; }
			td.note-text { padding: 6pt 8pt 6pt 0; font-size: 9pt; color: #30404F; }

			.photos { page-break-inside: avoid; margin-bottom: 8pt; }
			.photo-date { font-weight: bold; color: #315b47; }
			img.photo { max-width: 160pt; max-height: 160pt; margin: 3pt 7pt 3pt 0; border: 1px solid #dce5dc; }
			.disclaimer { margin-top: 18pt; padding: 8pt 10pt; background-color: #f3f6f1; border: 1px solid #dce5dc; font-size: 8.5pt; color: #59675e; }
			""";
	}

}
