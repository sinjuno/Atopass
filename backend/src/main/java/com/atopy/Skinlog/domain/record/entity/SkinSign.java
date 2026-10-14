package com.atopy.Skinlog.domain.record.entity;

/** 피부에 나타난 증상(있음/없음). 부위별 증상 점수와 별개로 하루 단위로 기록한다. */
public enum SkinSign {
	XEROSIS("피부건조증"),
	ERYTHEMA("홍반"),
	SCALING("각질"),
	PAPULE("구진"),
	EXCORIATION("긁은 자국"),
	NODULE("결절"),
	LICHENIFICATION("태선화");

	private final String label;

	SkinSign(String label) {
		this.label = label;
	}

	/** 사용자에게 보여줄 한글 이름 */
	public String label() {
		return label;
	}
}
