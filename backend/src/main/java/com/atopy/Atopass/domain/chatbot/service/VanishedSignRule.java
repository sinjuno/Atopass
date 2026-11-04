package com.atopy.Atopass.domain.chatbot.service;

import com.atopy.Atopass.domain.chatbot.dto.response.ChatDraftResponse;
import com.atopy.Atopass.domain.record.dto.response.SkinConditionResponse;
import com.atopy.Atopass.domain.record.entity.SkinSign;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * P09: 어제 기록에 있던 증상이 오늘은 없으면 "달라진 게 있었어요?"를 묻는다(P02의 반대).
 * 어제 기록이 없거나, 오늘 나타난 증상을 아직 묻지 않았으면 묻지 않는다. 질문에 넣는 이름은 사라진 증상 중 앞의 것 하나다.
 */
@Component
class VanishedSignRule implements SituationalQuestionRule {

	private final SituationalQuestionProperties properties;

	VanishedSignRule(SituationalQuestionProperties properties) {
		this.properties = properties;
	}

	public String id() {
		return "P09";
	}

	public int priority() {
		return properties.priority().p09();
	}

	public boolean matches(SituationalQuestionContext context) {
		return subject(context.draft(), context.yesterday()).isPresent();
	}

	/** 어제 있었는데 오늘 없는 증상의 이름. 어제 기록이 없거나 사라진 게 없으면 비어 있다. */
	static Optional<String> subject(ChatDraft draft, Optional<SituationalDay> yesterday) {
		if (yesterday.isEmpty()) {
			return Optional.empty();
		}
		ChatDraftResponse today = draft.toResponse();
		if (today.skinSigns() == null) {
			// 오늘 나타난 증상을 아직 묻지 않았으면 사라졌는지 알 수 없다.
			return Optional.empty();
		}
		Set<SkinSign> todaySigns = EnumSet.noneOf(SkinSign.class);
		todaySigns.addAll(today.skinSigns());
		for (SkinConditionResponse condition : today.skinConditions()) {
			todaySigns.addAll(condition.skinSigns());
		}
		Set<SkinSign> vanished = EnumSet.noneOf(SkinSign.class);
		vanished.addAll(yesterday.get().skinSigns());
		vanished.removeAll(todaySigns);
		return vanished.stream().findFirst().map(SkinSign::label);
	}

}
