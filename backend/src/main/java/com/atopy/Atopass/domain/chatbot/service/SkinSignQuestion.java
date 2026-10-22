package com.atopy.Atopass.domain.chatbot.service;

import com.atopy.Atopass.domain.record.entity.SkinSign;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 사용자가 증상의 뜻이나 모양을 묻는 말인지 알아본다. (예: "태선화가 뭐예요?", "홍반은 어떻게 생겼어요?")
 * 증상 이름과 묻는 말이 함께 있을 때만 질문으로 보므로, "진물이 나요"처럼 증상을 알려 주는 말은 질문이 아니다.
 * LLM을 부르지 않고 알아볼 수 있는 질문만 여기서 받고, 일상 표현으로 물은 것은 LLM이 알아본다.
 */
final class SkinSignQuestion {

	/** 증상마다 사용자가 이름으로 부르는 말. 가려움은 예시 사진이 없고, 증상을 알려 주는 말과 헷갈리기 쉬워 넣지 않는다. */
	private static final Map<SkinSign, List<String>> NAMES = Map.of(
		SkinSign.ERYTHEMA, List.of("홍반"),
		SkinSign.EDEMA, List.of("부종", "구진", "결절"),
		SkinSign.OOZING, List.of("진물", "딱지"),
		SkinSign.EXCORIATION, List.of("찰상", "긁은 자국", "긁은자국"),
		SkinSign.LICHENIFICATION, List.of("태선화"),
		SkinSign.XEROSIS, List.of("건조증"));

	/** 뜻이나 모양을 묻는 말 */
	private static final List<String> ASKING = List.of(
		"뭐", "무엇", "무슨", "어떤", "생겼", "생긴", "모양", "뜻", "의미", "설명", "알려", "보여", "사진", "그림");

	private SkinSignQuestion() {
	}

	/** 증상을 묻는 말이면 그 증상. 증상이 여러 개면 먼저 나온 것이다. */
	static Optional<SkinSign> detect(String text) {
		if (text == null || ASKING.stream().noneMatch(text::contains)) {
			return Optional.empty();
		}
		SkinSign found = null;
		int foundAt = Integer.MAX_VALUE;
		for (Map.Entry<SkinSign, List<String>> entry : NAMES.entrySet()) {
			for (String name : entry.getValue()) {
				int at = text.indexOf(name);
				if (at >= 0 && at < foundAt) {
					found = entry.getKey();
					foundAt = at;
				}
			}
		}
		return Optional.ofNullable(found);
	}

	/** 증상의 뜻을 쉬운 말로 한 문장. (예: "'태선화'는 오래 긁어서 피부가 두꺼워지고 거칠어짐, 주름이 깊어짐을 뜻해요.") */
	static String explanation(SkinSign sign) {
		return "'" + sign.label() + "'" + topicParticle(sign.label()) + " " + sign.plain() + "을 뜻해요.";
	}

	/** 마지막 한글 글자에 받침이 있으면 "은", 없으면 "는" */
	private static String topicParticle(String word) {
		for (int i = word.length() - 1; i >= 0; i--) {
			char c = word.charAt(i);
			if (c >= '가' && c <= '힣') {
				return (c - '가') % 28 == 0 ? "는" : "은";
			}
		}
		return "은";
	}

}
