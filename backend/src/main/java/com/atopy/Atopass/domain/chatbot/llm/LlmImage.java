package com.atopy.Atopass.domain.chatbot.llm;

/**
 * LLM에 보낼 사진 한 장. 메모리에서만 다루고 어디에도 저장하지 않는다.
 *
 * @param mediaType image/jpeg 또는 image/png
 */
public record LlmImage(byte[] bytes, String mediaType) {
}
