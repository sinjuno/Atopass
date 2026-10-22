package com.atopy.Atopass.domain.chatbot.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 선택지 버튼으로 고른 답. 챗봇이 지금 묻는 항목(askingField)에만 답할 수 있다.
 *
 * @param field 답하는 항목 (지금 묻는 askingField)
 * @param value 고른 값. 고통 지수·가려움 정도는 0~10 정수, 부위는 부위 이름 배열(고통 지수가 그 부위의 점수가 된다), 수면 시간은 0~24 숫자.
 *              선택지(choice)가 있는 항목은 고른 선택지 글자이고, 여러 개 고르는 항목은 글자 배열("하나도 없음"이면 빈 배열)이다.
 *              연결 질문을 글로 적는 단계(C12_TEXT 등)는 적은 글이다. skip이 true이면 보내지 않는다.
 *              항목마다 형식이 달라 그대로 받고, 서버가 초안에 반영할 때 검증한다.
 * @param skip  "넘어갈게요/없어요". 고통 지수 말고는 모두 건너뛸 수 있다.
 */
public record ChatChoiceRequest(
	@NotBlank(message = "답할 항목을 알려 주세요.")
	String field,

	Object value,

	Boolean skip
) {

	public boolean skipped() {
		return Boolean.TRUE.equals(skip);
	}

}
