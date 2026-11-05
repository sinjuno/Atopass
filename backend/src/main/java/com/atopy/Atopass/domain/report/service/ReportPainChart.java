package com.atopy.Atopass.domain.report.service;

import com.atopy.Atopass.domain.report.service.ReportContent.DayEntry;
import java.awt.Color;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 보고서 첫 장에 넣는 기간 불편함 지수(0~10) 그래프를 PNG로 그린다. 가장 높은 날을 강조한다. */
final class ReportPainChart {

	static final int WIDTH = ReportLineChart.WIDTH;
	static final int HEIGHT = ReportLineChart.HEIGHT;

	private static final Color LINE = new Color(0xC0, 0x39, 0x2B);

	private ReportPainChart() {
	}

	/** 기간 안에 불편함 지수를 기록한 날이 하나도 없으면 null */
	static byte[] png(LocalDate startDate, LocalDate endDate, List<DayEntry> days) {
		Map<LocalDate, Integer> painByDate = new HashMap<>();
		days.stream().filter(d -> d.painIndex() != null).forEach(d -> painByDate.put(d.date(), d.painIndex()));
		List<Double> series = new ArrayList<>();
		for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
			Integer v = painByDate.get(d);
			series.add(v == null ? null : v.doubleValue());
		}
		return ReportLineChart.png(series, 10, 5, LINE, ReportLineChart.Highlight.MAX);
	}

}
