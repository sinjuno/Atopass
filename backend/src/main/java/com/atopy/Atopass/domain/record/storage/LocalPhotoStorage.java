package com.atopy.Atopass.domain.record.storage;

import com.atopy.Atopass.domain.report.storage.StorageProperties;
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
 * 서버 디스크에 사진을 저장한다. 파일명은 서버가 만든 UUID이고, 읽기·삭제 때도 그 형식만 허용해서
 * 사용자가 경로(../ 등)를 끼워 넣을 수 없게 한다.
 */
@Component
public class LocalPhotoStorage implements PhotoStorage {

	private static final Pattern STORAGE_KEY =
		Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png)");

	private final Path baseDir;

	public LocalPhotoStorage(StorageProperties properties) {
		this.baseDir = Path.of(properties.photoDir()).toAbsolutePath().normalize();
	}

	@Override
	public String store(byte[] content, String extension) {
		try {
			Files.createDirectories(baseDir);
			String storageKey = UUID.randomUUID() + "." + extension;
			Files.write(baseDir.resolve(storageKey), content);
			return storageKey;
		} catch (IOException e) {
			throw new UncheckedIOException("사진 파일을 저장하지 못했습니다.", e);
		}
	}

	@Override
	public Optional<byte[]> load(String storageKey) {
		if (!isValid(storageKey)) {
			return Optional.empty();
		}
		try {
			return Optional.of(Files.readAllBytes(baseDir.resolve(storageKey)));
		} catch (NoSuchFileException e) {
			return Optional.empty();
		} catch (IOException e) {
			throw new UncheckedIOException("사진 파일을 읽지 못했습니다.", e);
		}
	}

	@Override
	public void delete(String storageKey) {
		if (!isValid(storageKey)) {
			return;
		}
		try {
			Files.deleteIfExists(baseDir.resolve(storageKey));
		} catch (IOException e) {
			throw new UncheckedIOException("사진 파일을 삭제하지 못했습니다.", e);
		}
	}

	private boolean isValid(String storageKey) {
		return storageKey != null && STORAGE_KEY.matcher(storageKey).matches();
	}

}
