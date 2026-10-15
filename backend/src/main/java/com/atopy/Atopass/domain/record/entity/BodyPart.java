package com.atopy.Atopass.domain.record.entity;

public enum BodyPart {
	FACE("얼굴"),
	NECK("목"),
	CHEST("가슴"),
	BACK("등"),
	ABDOMEN("배"),
	ARM("팔"),
	HAND("손"),
	LEG("다리"),
	FOOT("발");

	private final String label;

	BodyPart(String label) {
		this.label = label;
	}

	/** 사용자에게 보여줄 한글 이름 */
	public String label() {
		return label;
	}
}
