package com.atopy.Atopass.domain.report.service;

import com.atopy.Atopass.domain.report.service.ReportContent.DayEntry;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 * 보고서 첫 장 오른쪽 위에 넣는 기간 불편함 지수(0~10) 그래프를 PNG로 그린다.
 * PDF 변환기가 SVG를 그리지 못해 이미지로 만든다. 서버에 글꼴이 없어도 그릴 수 있도록 글자(눈금·날짜·제목)는 넣지 않고 PDF 쪽(HTML)에서 쓴다.
 * 기록하지 않은 날은 선을 끊고, 앞뒤가 비어 있는 하루는 점으로 찍는다. 가장 높은 날에는 큰 점을 찍는다.
 */
final class ReportPainChart {

	/** PDF에서 180pt × 60pt로 보이며, 선명하게 보이도록 4배로 그린다 */
	static final int WIDTH = 720;
	static final int HEIGHT = 240;

	private static final int MAX_SCORE = 10;
	private static final int LEFT = 12;
	private static final int RIGHT = 12;
	private static final int TOP = 14;
	private static final int BOTTOM = 14;
	private static final Color LINE = new Color(0xC0, 0x39, 0x2B);
	private static final Color FILL = new Color(0xC0, 0x39, 0x2B, 40);
	private static final Color GRID = new Color(0xC8, 0xD0, 0xDC);

	private ReportPainChart() {
	}

	/** 기간 안에 불편함 지수를 기록한 날이 하나도 없으면 null */
	static byte[] png(LocalDate startDate, LocalDate endDate, List<DayEntry> days) {
		Map<LocalDate, Integer> painByDate = new HashMap<>();
		days.stream().filter(d -> d.painIndex() != null).forEach(d -> painByDate.put(d.date(), d.painIndex()));
		if (painByDate.isEmpty()) {
			return null;
		}
		List<Integer> series = new ArrayList<>();
		for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
			series.add(painByDate.get(d));
		}

		BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		try {
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			drawGrid(g);
			drawSeries(g, series);
		} finally {
			g.dispose();
		}
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			ImageIO.write(image, "png", out);
			return out.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException("불편함 지수 그래프를 만들지 못했습니다.", e);
		}
	}

	/** 0·10점은 옅은 실선, 5점(보통)은 점선 */
	private static void drawGrid(Graphics2D g) {
		g.setColor(GRID);
		for (int score : new int[] {0, 5, 10}) {
			int y = (int) Math.round(y(score));
			g.setStroke(score == 5
				? new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] {6f, 6f}, 0f)
				: new BasicStroke(2f));
			g.drawLine(LEFT, y, WIDTH - RIGHT, y);
		}
	}

	private static void drawSeries(Graphics2D g, List<Integer> series) {
		// 이어진 구간(기록이 연속된 날들)마다 따로 그린다.
		List<List<Integer>> runs = new ArrayList<>();
		List<Integer> run = null;
		for (int i = 0; i < series.size(); i++) {
			if (series.get(i) == null) {
				run = null;
				continue;
			}
			if (run == null) {
				run = new ArrayList<>();
				runs.add(run);
			}
			run.add(i);
		}

		int count = series.size();
		for (List<Integer> indexes : runs) {
			if (indexes.size() == 1) {
				int i = indexes.get(0);
				dot(g, x(i, count), y(series.get(i)), 6, LINE);
				continue;
			}
			Path2D.Double line = new Path2D.Double();
			Path2D.Double area = new Path2D.Double();
			int first = indexes.get(0);
			int last = indexes.get(indexes.size() - 1);
			area.moveTo(x(first, count), y(0));
			for (int k = 0; k < indexes.size(); k++) {
				int i = indexes.get(k);
				double px = x(i, count);
				double py = y(series.get(i));
				if (k == 0) {
					line.moveTo(px, py);
				} else {
					line.lineTo(px, py);
				}
				area.lineTo(px, py);
			}
			area.lineTo(x(last, count), y(0));
			area.closePath();
			g.setColor(FILL);
			g.fill(area);
			g.setColor(LINE);
			g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.draw(line);
		}

		// 가장 높은 날(여러 날이면 가장 최근)에 큰 점
		int peak = -1;
		for (int i = 0; i < count; i++) {
			if (series.get(i) != null && (peak < 0 || series.get(i) >= series.get(peak))) {
				peak = i;
			}
		}
		dot(g, x(peak, count), y(series.get(peak)), 9, LINE);
	}

	private static void dot(Graphics2D g, double x, double y, double r, Color color) {
		g.setColor(Color.WHITE);
		g.fill(new Ellipse2D.Double(x - r - 2, y - r - 2, (r + 2) * 2, (r + 2) * 2));
		g.setColor(color);
		g.fill(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
	}

	private static double x(int index, int count) {
		double width = WIDTH - LEFT - RIGHT;
		return count <= 1 ? LEFT + width / 2 : LEFT + width * index / (count - 1);
	}

	private static double y(int score) {
		double height = HEIGHT - TOP - BOTTOM;
		return TOP + height * (MAX_SCORE - score) / MAX_SCORE;
	}

}
