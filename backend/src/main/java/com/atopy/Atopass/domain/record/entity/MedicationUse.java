package com.atopy.Atopass.domain.record.entity;

/** 처방받은 연고·약 사용 여부. 사용했는지 사실만 기록한다. */
public enum MedicationUse {
	USED("사용함"),
	NOT_USED("안 함"),
	NO_PRESCRIPTION("처방 없음");

	private final String label;

	MedicationUse(String label) {
		this.label = label;
	}

	/** 챗봇 선택지와 화면에 보여줄 한글 이름 */
	public String label() {
		return label;
	}

}
