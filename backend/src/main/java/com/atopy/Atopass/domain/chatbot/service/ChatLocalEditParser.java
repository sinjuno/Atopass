package com.atopy.Atopass.domain.chatbot.service;

import com.atopy.Atopass.domain.record.entity.BodyPart;
import com.atopy.Atopass.domain.record.entity.SkinSign;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * LLM 없이, 자주 쓰는 수정 말투(예: "수면 시간을 6시간으로 고쳐줘", "고통 지수는 7점이야",
 * "약 안 먹었어", "가려움은 없어졌어요")를 알아듣는다. LLM이 쓰는 것과 같은 updates 형식을 만들어
 * {@link ChatDraft#apply}로 바로 넘긴다.
 *
 * <p>여러 항목이 한 문장에 섞여 있어도 각각 따로 찾아서 한 번에 반영한다(예: "고통은 3점이고
 * 가려움은 없어졌어요"). 아무 항목도 알아듣지 못하면 빈 값을 돌려주고, 호출하는 쪽에서 LLM으로 넘어간다.
 *
 * <p>ANTHROPIC_API_KEY가 설정되지 않은 배포 환경에서도 자주 쓰는 수정만큼은 바로 되게 하려고 만들었다.
 * 범위를 넘는 말(여러 날짜 비교, 복잡한 설명 요청 등)은 여전히 LLM이 필요하다.
 */
@Component
class ChatLocalEditParser {

	private static final Pattern PAIN_INDEX = Pattern.compile("고통[^0-9]{0,8}(\\d{1,2})\\s*점");
	private static final Pattern SLEEP_WITH_KEYWORD =
		Pattern.compile("(?:수면\\s*시간|잠)[^0-9]{0,8}(\\d{1,2}(?:\\.\\d)?)\\s*시간");
	private static final Pattern SLEEP_BARE =
		Pattern.compile("(\\d{1,2}(?:\\.\\d)?)\\s*시간\\s*(?:잤|잔|잠)");
	private static final Pattern MEDICATION_NO =
		Pattern.compile("약[^가-힣]{0,2}(안\\s*먹|안\\s*복용|먹지\\s*않|복용하지\\s*않|안했|안\\s*했)");
	private static final Pattern MEDICATION_YES =
		Pattern.compile("약[^가-힣]{0,4}(먹었|복용했|먹음|복용함)");
	private static final Pattern REMOVED_CONTEXT =
		Pattern.compile("없어졌|사라졌|나아졌|좋아졌|괜찮아졌|빠졌|가셨");
	private static final Pattern REMOVE_PART_CONTEXT = Pattern.compile("빼|제외|없애|지워");

	/** 증상 라벨의 괄호 설명을 뺀, 본문에서 찾기 쉬운 짧은 키워드들 */
	private static final Map<SkinSign, List<String>> SIGN_KEYWORDS = new EnumMap<>(SkinSign.class);
	static {
		SIGN_KEYWORDS.put(SkinSign.ERYTHEMA, List.of("홍반"));
		SIGN_KEYWORDS.put(SkinSign.EDEMA, List.of("부종", "구진"));
		SIGN_KEYWORDS.put(SkinSign.OOZING, List.of("진물", "딱지"));
		SIGN_KEYWORDS.put(SkinSign.EXCORIATION, List.of("긁은 자국", "긁은자국", "찰상", "긁힌"));
		SIGN_KEYWORDS.put(SkinSign.LICHENIFICATION, List.of("태선화"));
		SIGN_KEYWORDS.put(SkinSign.XEROSIS, List.of("건조증", "건조"));
		SIGN_KEYWORDS.put(SkinSign.ITCHING, List.of("가려움", "가렵"));
	}

	private final JsonMapper jsonMapper;

	ChatLocalEditParser(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	/**
	 * 알아들은 항목이 하나도 없으면 비어 있다.
	 *
	 * @param symptomsPending 지금 특정 부위의 증상을 되묻는 중(askingField가 symptoms)이면 true.
	 *                        이때는 "가려움" 같은 말이 어느 부위의 증상인지 이 규칙만으로는 알 수 없으므로
	 *                        증상 말투는 알아듣지 않고 LLM에 맡긴다(그 부위의 conditions에 바로 넣도록).
	 */
	Optional<ChatReplyParser.ModelOutput> parse(String text, boolean symptomsPending) {
		if (text == null || text.isBlank()) {
			return Optional.empty();
		}
		String trimmed = text.trim();
		ObjectNode updates = jsonMapper.createObjectNode();
		boolean matchedAny = false;

		Matcher pain = PAIN_INDEX.matcher(trimmed);
		if (pain.find()) {
			int score = Integer.parseInt(pain.group(1));
			if (score >= 0 && score <= 10) {
				updates.put("painIndex", score);
				matchedAny = true;
			}
		}

		Matcher sleepMatcher = SLEEP_WITH_KEYWORD.matcher(trimmed);
		boolean sleepFound = sleepMatcher.find();
		if (!sleepFound) {
			sleepMatcher = SLEEP_BARE.matcher(trimmed);
			sleepFound = sleepMatcher.find();
		}
		if (sleepFound) {
			double hours = Double.parseDouble(sleepMatcher.group(1));
			if (hours >= 0 && hours <= 24) {
				updates.put("sleepHours", hours);
				matchedAny = true;
			}
		}

		if (MEDICATION_NO.matcher(trimmed).find()) {
			updates.put("medication", false);
			matchedAny = true;
		} else if (MEDICATION_YES.matcher(trimmed).find()) {
			updates.put("medication", true);
			matchedAny = true;
		}

		if (!symptomsPending) {
			boolean removalContext = REMOVED_CONTEXT.matcher(trimmed).find();
			List<SkinSign> matchedSigns = SIGN_KEYWORDS.entrySet().stream()
				.filter(e -> e.getValue().stream().anyMatch(trimmed::contains))
				.map(Map.Entry::getKey)
				.toList();
			if (!matchedSigns.isEmpty()) {
				ArrayNode array = updates.putArray(removalContext ? "removeSkinSigns" : "skinSigns");
				matchedSigns.forEach(sign -> array.add(sign.name()));
				matchedAny = true;
			}
		}

		if (REMOVE_PART_CONTEXT.matcher(trimmed).find()) {
			List<BodyPart> matchedParts = java.util.Arrays.stream(BodyPart.values())
				.filter(part -> trimmed.contains(part.label()))
				.toList();
			if (!matchedParts.isEmpty()) {
				ArrayNode array = updates.putArray("removeBodyParts");
				matchedParts.forEach(part -> array.add(part.name()));
				matchedAny = true;
			}
		}

		if (!matchedAny) {
			return Optional.empty();
		}
		return Optional.of(new ChatReplyParser.ModelOutput(true, null, updates, false, false, false, null));
	}

}
