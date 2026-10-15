package com.atopy.Atopass.domain.record.storage;

import java.util.Optional;

/**
 * 사진 파일 저장소. 지금은 서버 디스크를 쓰지만, 이 인터페이스 뒤에 두어 나중에 S3 등으로 바꿀 수 있다.
 */
public interface PhotoStorage {

	/** 파일을 저장하고 저장 키(식별자)를 돌려준다. */
	String store(byte[] content, String extension);

	/** 저장 키로 파일을 읽는다. 없으면 비어 있다. */
	Optional<byte[]> load(String storageKey);

	/** 파일을 지운다. 이미 없어도 오류가 아니다. */
	void delete(String storageKey);

}
