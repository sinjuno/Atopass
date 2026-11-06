package com.atopy.Atopass.domain.report.service;

import com.atopy.Atopass.domain.report.service.ReportContent.DayEntry;
import java.awt.Color;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 보고서의 기간 흐름 비교에 넣는 수면 시간(0~12시간) 그래프를 PNG로 그린다. 가장 적게 잔 날을 강조한다.
 * 불편함 지수 그래프와 나란히 둬, 수면이 짧았던 날과 불편함이 심했던 날이 겹치는지 바로 볼 수 있게 한다.
 */
final class ReportSleepChart {

	static final int WIDTH = ReportLineChart.WIDTH;
	static final int HEIGHT = ReportLineChart.HEIGHT;

	private static final Color LINE = new Color(0x3F, 0x6F, 0xAE);

	private ReportSleepChart() {
	}

	/** 기간 안에 수면 시간을 기록한 날이 하나도 없으면 null */
	static byte[] png(LocalDate startDate, LocalDate endDate, List<DayEntry> days) {
		Map<LocalDate, BigDecimal> sleepByDate = new HashMap<>();
		days.stream().filter(d -> d.sleepHours() != null).forEach(d -> sleepByDate.put(d.date(), d.sleepHours()));
		List<Double> series = new ArrayList<>();
		for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
			BigDecimal v = sleepByDate.get(d);
			series.add(v == null ? null : v.doubleValue());
		}
		return ReportLineChart.png(series, 12, 6, LINE, ReportLineChart.Highlight.MIN);
	}

}
