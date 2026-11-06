package com.atopy.Atopass.domain.report.service;

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
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * 보고서 첫 장에 넣는 기간 꺾은선 그래프(불편함 지수·수면 시간 등)를 PNG로 그리는 공통 로직.
 * PDF 변환기가 SVG를 그리지 못해 이미지로 만든다. 서버에 글꼴이 없어도 그릴 수 있도록 글자(눈금·날짜·제목)는
 * 넣지 않고 PDF 쪽(HTML)에서 쓴다.
 * 기록하지 않은 날은 선을 끊고, 앞뒤가 비어 있는 하루는 점으로 찍는다. 강조할 날(가장 높은 날 등)에는 큰 점을 찍는다.
 */
final class ReportLineChart {

	/** PDF에서 174pt × 58pt로 보이며, 선명하게 보이도록 4배로 그린다 */
	static final int WIDTH = 720;
	static final int HEIGHT = 240;

	private static final int LEFT = 12;
	private static final int RIGHT = 12;
	private static final int TOP = 14;
	private static final int BOTTOM = 14;
	private static final Color GRID = new Color(0xC8, 0xD0, 0xDC);

	/** 가장 눈에 띄어야 할 값에 큰 점을 찍는 기준 */
	enum Highlight {
		MAX, MIN
	}

	private ReportLineChart() {
	}

	/**
	 * series는 날짜 순서대로 둔 값이고, 기록하지 않은 날은 null이다. 값이 하나도 없으면 null을 돌려준다.
	 *
	 * @param maxScale 세로축 최댓값 (0부터 시작)
	 * @param midline 점선으로 그릴 가운데 눈금
	 */
	static byte[] png(List<Double> series, double maxScale, double midline, Color line, Highlight highlight) {
		if (series.stream().allMatch(v -> v == null)) {
			return null;
		}
		Color fill = new Color(line.getRed(), line.getGreen(), line.getBlue(), 40);

		BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		try {
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			drawGrid(g, maxScale, midline);
			drawSeries(g, series, maxScale, line, fill, highlight);
		} finally {
			g.dispose();
		}
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			ImageIO.write(image, "png", out);
			return out.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException("기간 그래프를 만들지 못했습니다.", e);
		}
	}

	/** 0·최댓값은 옅은 실선, 가운데 눈금은 점선 */
	private static void drawGrid(Graphics2D g, double maxScale, double midline) {
		g.setColor(GRID);
		for (double score : new double[] {0, midline, maxScale}) {
			int y = (int) Math.round(y(score, maxScale));
			g.setStroke(score == midline
				? new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[] {6f, 6f}, 0f)
				: new BasicStroke(2f));
			g.drawLine(LEFT, y, WIDTH - RIGHT, y);
		}
	}

	private static void drawSeries(Graphics2D g, List<Double> series, double maxScale, Color line, Color fill,
		Highlight highlight) {
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
				dot(g, x(i, count), y(series.get(i), maxScale), 6, line);
				continue;
			}
			Path2D.Double linePath = new Path2D.Double();
			Path2D.Double area = new Path2D.Double();
			int first = indexes.get(0);
			int last = indexes.get(indexes.size() - 1);
			area.moveTo(x(first, count), y(0, maxScale));
			for (int k = 0; k < indexes.size(); k++) {
				int i = indexes.get(k);
				double px = x(i, count);
				double py = y(series.get(i), maxScale);
				if (k == 0) {
					linePath.moveTo(px, py);
				} else {
					linePath.lineTo(px, py);
				}
				area.lineTo(px, py);
			}
			area.lineTo(x(last, count), y(0, maxScale));
			area.closePath();
			g.setColor(fill);
			g.fill(area);
			g.setColor(line);
			g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			g.draw(linePath);
		}

		// 강조할 날(여러 날이면 가장 최근) — MAX면 가장 높은 값, MIN이면 가장 낮은 값
		int picked = -1;
		for (int i = 0; i < count; i++) {
			if (series.get(i) == null) {
				continue;
			}
			boolean better = picked < 0
				|| (highlight == Highlight.MAX && series.get(i) >= series.get(picked))
				|| (highlight == Highlight.MIN && series.get(i) <= series.get(picked));
			if (better) {
				picked = i;
			}
		}
		dot(g, x(picked, count), y(series.get(picked), maxScale), 9, line);
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

	private static double y(double score, double maxScale) {
		double height = HEIGHT - TOP - BOTTOM;
		return TOP + height * (maxScale - score) / maxScale;
	}

}
