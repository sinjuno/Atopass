package com.atopy.Atopass.domain.report.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 서버 디스크에 PDF를 저장한다. 파일명은 서버가 만든 UUID이고, 읽기·삭제 때도 그 형식만 허용해서
 * 사용자가 경로(../ 등)를 끼워 넣을 수 없게 한다.
 */
@Component
public class LocalReportFileStorage implements ReportFileStorage {

	private static final Pattern FILE_NAME = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.pdf");

	private final Path baseDir;

	public LocalReportFileStorage(StorageProperties properties) {
		this.baseDir = Path.of(properties.reportDir()).toAbsolutePath().normalize();
	}

	@Override
	public String store(byte[] pdf) {
		try {
			Files.createDirectories(baseDir);
			String fileName = UUID.randomUUID() + ".pdf";
			Files.write(baseDir.resolve(fileName), pdf);
			return fileName;
		} catch (IOException e) {
			throw new UncheckedIOException("보고서 파일을 저장하지 못했습니다.", e);
		}
	}

	@Override
	public Optional<byte[]> load(String fileName) {
		if (!isValid(fileName)) {
			return Optional.empty();
		}
		try {
			return Optional.of(Files.readAllBytes(baseDir.resolve(fileName)));
		} catch (NoSuchFileException e) {
			return Optional.empty();
		} catch (IOException e) {
			throw new UncheckedIOException("보고서 파일을 읽지 못했습니다.", e);
		}
	}

	@Override
	public void delete(String fileName) {
		if (!isValid(fileName)) {
			return;
		}
		try {
			Files.deleteIfExists(baseDir.resolve(fileName));
		} catch (IOException e) {
			throw new UncheckedIOException("보고서 파일을 삭제하지 못했습니다.", e);
		}
	}

	private boolean isValid(String fileName) {
		return fileName != null && FILE_NAME.matcher(fileName).matches();
	}

}
