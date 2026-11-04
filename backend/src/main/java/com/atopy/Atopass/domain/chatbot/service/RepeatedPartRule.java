package com.atopy.Atopass.domain.chatbot.service;

import com.atopy.Atopass.domain.chatbot.dto.response.ChatDraftResponse;
import com.atopy.Atopass.domain.record.dto.response.SkinConditionResponse;
import com.atopy.Atopass.domain.record.entity.BodyPart;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * P12: 같은 부위가 오늘을 포함해 최근 며칠(설정값) 중 여러 날(설정값 이상) 불편했으면 "언제부터예요?"를 묻는다.
 * 부위가 여러 개 맞으면 가장 많이 기록된 부위(같으면 앞의 부위) 하나를 묻는다. 오늘 그 부위를 고르지 않았으면 묻지 않는다.
 * 불편함 지수가 높은 날이 이어지는 경우(P03)는 순위가 더 높아 그쪽이 먼저다.
 */
@Component
class RepeatedPartRule implements SituationalQuestionRule {

	private final SituationalQuestionProperties properties;

	RepeatedPartRule(SituationalQuestionProperties properties) {
		this.properties = properties;
	}

	public String id() {
		return "P12";
	}

	public int priority() {
		return properties.priority().p12();
	}

	public boolean matches(SituationalQuestionContext context) {
		return subject(context.draft(), context.today(), context.recentDays(), properties.repeatedPartMinDays(),
			properties.repeatedPartLookbackDays()).isPresent();
	}

	/**
	 * 오늘 포함 lookbackDays일 중 minDays일 이상 불편했던 부위의 이름. 없으면 비어 있다.
	 * 기록하지 않은 날은 그 부위가 없었던 날로 센다.
	 */
	static Optional<String> subject(
		ChatDraft draft, LocalDate today, List<SituationalDay> recentDays, int minDays, int lookbackDays) {
		ChatDraftResponse todayDraft = draft.toResponse();
		Map<BodyPart, Integer> counts = new EnumMap<>(BodyPart.class);
		for (SkinConditionResponse condition : todayDraft.skinConditions()) {
			counts.merge(condition.bodyPart(), 1, Integer::sum);
		}
		if (counts.isEmpty()) {
			return Optional.empty();
		}
		LocalDate from = today.minusDays(lookbackDays - 1L);
		for (SituationalDay day : recentDays) {
			if (day.date().isBefore(from)) {
				continue;
			}
			for (BodyPart part : day.bodyParts()) {
				// 오늘 고른 부위만 센다
				counts.computeIfPresent(part, (key, count) -> count + 1);
			}
		}
		return counts.entrySet().stream()
			.filter(entry -> entry.getValue() >= minDays)
			.max(Map.Entry.<BodyPart, Integer>comparingByValue().thenComparing(Map.Entry.comparingByKey(
				java.util.Comparator.reverseOrder())))
			.map(entry -> entry.getKey().label());
	}

}
