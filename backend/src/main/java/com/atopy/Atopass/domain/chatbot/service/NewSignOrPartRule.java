package com.atopy.Atopass.domain.chatbot.service;

import com.atopy.Atopass.domain.chatbot.dto.response.ChatDraftResponse;
import com.atopy.Atopass.domain.record.dto.response.SkinConditionResponse;
import com.atopy.Atopass.domain.record.entity.BodyPart;
import com.atopy.Atopass.domain.record.entity.SkinSign;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * P02: 어제 기록에 없던 증상이나 부위가 오늘 있으면 "어떻게 시작됐어요?"를 묻는다. 어제 기록이 없으면 묻지 않는다.
 * 질문에 넣는 이름은 새 증상이 있으면 그 증상, 없으면 새 부위 중 앞의 것 하나("목 증상")다.
 */
@Component
class NewSignOrPartRule implements SituationalQuestionRule {

	private final SituationalQuestionProperties properties;

	NewSignOrPartRule(SituationalQuestionProperties properties) {
		this.properties = properties;
	}

	public String id() {
		return "P02";
	}

	public int priority() {
		return properties.priority().p02();
	}

	public boolean matches(SituationalQuestionContext context) {
		return subject(context.draft(), context.yesterday()).isPresent();
	}

	/** 어제 없던 증상(먼저) 또는 부위의 이름. 어제 기록이 없거나 새로 생긴 게 없으면 비어 있다. */
	static Optional<String> subject(ChatDraft draft, Optional<SituationalDay> yesterday) {
		if (yesterday.isEmpty()) {
			return Optional.empty();
		}
		ChatDraftResponse today = draft.toResponse();
		Set<SkinSign> signs = EnumSet.noneOf(SkinSign.class);
		Set<BodyPart> parts = EnumSet.noneOf(BodyPart.class);
		if (today.skinSigns() != null) {
			signs.addAll(today.skinSigns());
		}
		for (SkinConditionResponse condition : today.skinConditions()) {
			parts.add(condition.bodyPart());
			signs.addAll(condition.skinSigns());
		}
		signs.removeAll(yesterday.get().skinSigns());
		parts.removeAll(yesterday.get().bodyParts());
		if (!signs.isEmpty()) {
			return Optional.of(signs.iterator().next().label());
		}
		// "어제 없던 목이 생겼네요"는 어색하므로 부위는 "목 증상"처럼 부른다.
		return parts.stream().findFirst().map(part -> part.label() + " 증상");
	}

	/** 이름 뒤에 받침에 맞는 "이/가"를 붙인다. 괄호 안 글자도 소리 내어 읽으므로 마지막 한글 글자로 정한다. */
	static String withSubjectParticle(String name) {
		for (int i = name.length() - 1; i >= 0; i--) {
			char c = name.charAt(i);
			if (c >= '가' && c <= '힣') {
				return name + ((c - '가') % 28 == 0 ? "가" : "이");
			}
		}
		return name + "이";
	}

}
