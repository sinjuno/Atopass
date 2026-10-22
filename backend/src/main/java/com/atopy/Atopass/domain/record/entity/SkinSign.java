package com.atopy.Atopass.domain.record.entity;

/**
 * 피부에 나타난 증상(있음/없음). 부위마다, 그리고 하루 단위로 기록한다.
 * plain은 챗봇이 사용자의 일상 표현을 알아듣도록 프롬프트에 넣는 쉬운 설명이다.
 */
public enum SkinSign {
	ERYTHEMA("홍반", "피부가 붉어짐"),
	EDEMA("부종/구진(결절)", "피부가 붓거나 오돌토돌 솟아오름, 단단한 멍울"),
	OOZING("진물/딱지", "상처에서 진물이 나오거나 딱지가 앉음"),
	EXCORIATION("긁은 자국(찰상)", "긁어서 피부가 까지거나 긁힌 자국이 남음"),
	LICHENIFICATION("태선화", "오래 긁어서 피부가 두꺼워지고 거칠어짐, 주름이 깊어짐"),
	XEROSIS("건조증", "피부가 건조하고 당기거나 하얗게 일어남"),
	ITCHING("가려움", "피부가 가려움"),
	FISSURE("균열", "피부가 갈라지거나 틈이 생김"),
	BLEEDING("출혈", "긁거나 갈라진 곳에서 피가 남");

	private final String label;
	private final String plain;

	SkinSign(String label, String plain) {
		this.label = label;
		this.plain = plain;
	}

	/** 사용자에게 보여줄 한글 이름 */
	public String label() {
		return label;
	}

	/** 쉬운 말 설명 */
	public String plain() {
		return plain;
	}

}
