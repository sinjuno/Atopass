package com.atopy.Atopass.domain.report.service;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * XHTML을 PDF로 바꾼다. 한글이 깨지지 않도록 나눔고딕(OFL 라이선스)을 PDF에 포함한다.
 */
@Component
class ReportPdfGenerator {

	private static final String FONT_FAMILY = "NanumGothic";
	private static final String FONT_PATH = "fonts/NanumGothic-Regular.ttf";

	/** 폰트는 한 번만 읽어 둔다. (약 2MB) */
	private final byte[] fontBytes;

	ReportPdfGenerator() {
		try (InputStream in = new ClassPathResource(FONT_PATH).getInputStream()) {
			this.fontBytes = in.readAllBytes();
		} catch (IOException e) {
			throw new UncheckedIOException("보고서용 폰트를 읽지 못했습니다: " + FONT_PATH, e);
		}
	}

	byte[] generate(String xhtml) {
		try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			PdfRendererBuilder builder = new PdfRendererBuilder();
			builder.useFastMode();
			// 굵은 글씨(700)도 같은 폰트 파일을 쓴다. 굵기 대신 글자 크기와 색으로 강조한다.
			builder.useFont(() -> new ByteArrayInputStream(fontBytes), FONT_FAMILY, 400, FontStyle.NORMAL, true);
			builder.useFont(() -> new ByteArrayInputStream(fontBytes), FONT_FAMILY, 700, FontStyle.NORMAL, true);
			builder.withHtmlContent(xhtml, null);
			builder.toStream(out);
			builder.run();
			return out.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException("보고서 PDF를 만들지 못했습니다.", e);
		} catch (Exception e) {
			throw new IllegalStateException("보고서 PDF를 만들지 못했습니다.", e);
		}
	}

}
