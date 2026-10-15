package com.atopy.Atopass.domain.record.entity;

import java.util.Optional;

/** 업로드된 파일의 실제 형식을 매직 바이트로 판별한다. 클라이언트가 보낸 Content-Type/확장자는 믿지 않는다. */
public enum PhotoType {

	JPEG("image/jpeg", "jpg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
	PNG("image/png", "png", new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});

	private final String contentType;
	private final String extension;
	private final byte[] signature;

	PhotoType(String contentType, String extension, byte[] signature) {
		this.contentType = contentType;
		this.extension = extension;
		this.signature = signature;
	}

	public String contentType() {
		return contentType;
	}

	public String extension() {
		return extension;
	}

	public static Optional<PhotoType> detect(byte[] content) {
		for (PhotoType type : values()) {
			if (startsWith(content, type.signature)) {
				return Optional.of(type);
			}
		}
		return Optional.empty();
	}

	private static boolean startsWith(byte[] content, byte[] signature) {
		if (content.length < signature.length) {
			return false;
		}
		for (int i = 0; i < signature.length; i++) {
			if (content[i] != signature[i]) {
				return false;
			}
		}
		return true;
	}

}
