package com.atopy.Atopass.domain.chatbot.dto.response;

/**
 * 기록 화면·재진 PDF에 보여 줄 상황 질문(P01~P07)·마무리 질문(C02) 답 한 줄.
 * C02는 적은 항목(새로 쓴 제품·평소와 다른 일·진료 때 물어볼 것)마다 한 줄씩이다.
 *
 * @param questionId 질문 ID (P01~P07, C02)
 * @param title      무엇에 대한 답인지 (예: "잠을 적게 잔 이유")
 * @param answer     답. 선택지는 화면의 버튼 글자, 글은 사용자가 적은 그대로
 */
public record SituationalAnswerItem(String questionId, String title, String answer) {
}
