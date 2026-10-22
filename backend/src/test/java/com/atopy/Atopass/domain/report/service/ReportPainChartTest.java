package com.atopy.Atopass.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.atopy.Atopass.domain.record.entity.BodyPart;
import com.atopy.Atopass.domain.report.service.ReportContent.DayEntry;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ReportPainChartTest {

	private static final LocalDate START = LocalDate.of(2026, 7, 1);

	private static DayEntry day(LocalDate date, Integer pain) {
		return new DayEntry(date, new EnumMap<>(BodyPart.class), null, null, null, null, List.of(), pain, null, List.of());
	}

	@Test
	void 고통_지수를_기록한_날이_없으면_그래프를_만들지_않는다() {
		assertThat(ReportPainChart.png(START, START.plusDays(6), List.of(day(START, null)))).isNull();
		assertThat(ReportPainChart.png(START, START.plusDays(6), List.of())).isNull();
	}

	@Test
	void 기간_고통_지수를_PNG로_그린다() throws Exception {
		List<DayEntry> days = List.of(day(START, 3), day(START.plusDays(1), 7), day(START.plusDays(4), 5));

		byte[] png = ReportPainChart.png(START, START.plusDays(6), days);

		BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
		assertThat(image.getWidth()).isEqualTo(ReportPainChart.WIDTH);
		assertThat(image.getHeight()).isEqualTo(ReportPainChart.HEIGHT);
	}

	@Test
	void 하루만_기록해도_그래프를_그린다() {
		assertThat(ReportPainChart.png(START, START, List.of(day(START, 0)))).isNotEmpty();
	}

}
