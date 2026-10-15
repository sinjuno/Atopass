package com.atopy.Atopass.domain.chatbot.llm;

import com.atopy.Atopass.domain.chatbot.entity.ChatRole;

/** LLM에 보내는 대화 한 턴 */
public record ChatTurn(ChatRole role, String content) {
}
