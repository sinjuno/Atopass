package com.atopy.Atopass.domain.report.service;

import java.util.List;

/**
 * 보고서에 넣을 기록별 사진을 가져오는 창구. 사진 기능을 구현하는 쪽에서 이 인터페이스를 구현해
 * {@link EmptyReportPhotoSource}를 대체하면 보고서에 사진이 포함된다.
 */
public interface ReportPhotoSource {

	/** 기록에 연결된 사진. 없으면 빈 목록 */
	List<ReportPhoto> photosOf(Long recordId);

}
