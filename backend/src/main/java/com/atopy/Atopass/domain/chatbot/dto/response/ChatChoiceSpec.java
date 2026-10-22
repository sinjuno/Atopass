package com.atopy.Atopass.domain.chatbot.dto.response;

import java.util.List;

/**
 * 지금 묻는 항목의 선택지. 화면은 이 값대로 버튼을 그리고, 고른 선택지 글자를 그대로 답으로 보낸다.
 * 고통 지수·가려움 정도·부위·수면 시간·특이사항처럼 화면이 따로 그리는 항목은 보내지 않는다(null).
 *
 * @param label     항목 이름 (선택지를 내려 두었을 때 "○○ 고르기"로 보인다)
 * @param options   선택지 글자
 * @param multiple  여러 개를 고를 수 있는지. 아무것도 고르지 않고 완료하면 빈 배열로 보낸다.
 * @param noneLabel 여러 개 고르기에서 "하나도 없음"을 뜻하는 버튼 글자. 없으면 null
 * @param text      글로 직접 적는 단계인지. 선택지 없이 입력창에 적은 글이 답이 된다.
 */
public record ChatChoiceSpec(
	String label,
	List<String> options,
	boolean multiple,
	String noneLabel,
	boolean text
) {
}
