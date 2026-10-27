package com.atopy.Atopass.domain.chatbot.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 선택지 버튼으로 고른 답. 챗봇이 지금 묻는 항목(askingField)에만 답할 수 있다.
 *
 * @param field 답하는 항목 (painIndex, bodyParts, sleepHours, medication, skinSigns, notes, signImage)
 * @param value 고른 값. 불편함 지수는 0~10 정수, 부위는 부위 이름 배열(불편함 지수가 그 부위의 점수가, 방금 고른 증상이 그 부위의 증상이 된다), 수면 시간은 0~24 숫자, 약 복용은 true/false,
 *              나타난 증상은 증상 이름 배열("없음"이면 빈 배열), 증상 예시 사진(signImage)은 보여 줄지 true/false.
 *              skip이 true이면 보내지 않는다.
 *              항목마다 형식이 달라 그대로 받고, 서버가 초안에 반영할 때 검증한다.
 * @param skip  "넘어갈게요/없어요". 부위, 수면 시간, 약 복용, 나타난 증상, 특이사항만 건너뛸 수 있다.
 *              증상 예시 사진(signImage)을 건너뛰면 "괜찮아요"(보여 주지 않음)와 같다.
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
