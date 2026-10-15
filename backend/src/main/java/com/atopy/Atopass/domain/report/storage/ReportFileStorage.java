package com.atopy.Atopass.domain.report.storage;

import java.util.Optional;

/**
 * 보고서 PDF 저장소. 지금은 서버 디스크를 쓰지만, 이 인터페이스 뒤에 두어 나중에 S3 등으로 바꿀 수 있다.
 */
public interface ReportFileStorage {

	/** PDF를 저장하고 파일명(식별자)을 돌려준다. */
	String store(byte[] pdf);

	/** 파일명으로 PDF를 읽는다. 없으면 비어 있다. */
	Optional<byte[]> load(String fileName);

	/** 파일을 지운다. 이미 없어도 오류가 아니다. */
	void delete(String fileName);

}
