package com.atopy.Atopass.domain.record.entity;

/** 오늘 피부 상태를 평소와 비교한 느낌 (사용자의 주관적인 판단) */
public enum SkinTrend {
	BETTER("좋아짐"),
	SAME("평소와 비슷"),
	WORSE("평소보다 나빠짐");

	private final String label;

	SkinTrend(String label) {
		this.label = label;
	}

	/** 챗봇 선택지와 화면에 보여줄 한글 이름 */
	public String label() {
		return label;
	}

}
