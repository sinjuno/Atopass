package com.atopy.Atopass.domain.chatbot.service;

import com.atopy.Atopass.domain.record.entity.SkinSign;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 기록 모드에서 LLM이 돌려준 JSON을 읽는다.
 * 코드블록이나 설명이 섞여 있어도 첫 '{'부터 마지막 '}'까지를 JSON으로 보고, 읽을 수 없으면 비어 있는 결과를 돌려준다.
 */
@Component
@RequiredArgsConstructor
class ChatReplyParser {

	/**
	 * @param understood 입력을 이해했는지 (생략하면 이해한 것으로 본다)
	 * @param reply      사용자에게 보여줄 문장. 없으면 null
	 * @param updates    이번 메시지에서 새로 알게 된 값. 없으면 null
	 * @param finished   사용자가 입력을 마치려 하는지
	 * @param followUp   reply가 부위나 증상 점수를 되묻는 질문인지
	 * @param explain    사용자가 증상 같은 용어의 뜻을 물어서 reply가 설명인지 (기록은 바뀌지 않는다)
	 * @param sign       설명한 것이 증상이면 그 증상. 모르는 이름이거나 없으면 null
	 */
	record ModelOutput(boolean understood, String reply, JsonNode updates, boolean finished, boolean followUp,
		boolean explain, SkinSign sign) {
	}

	private final JsonMapper jsonMapper;

	Optional<ModelOutput> parse(String raw) {
		if (raw == null) {
			return Optional.empty();
		}
		int start = raw.indexOf('{');
		int end = raw.lastIndexOf('}');
		if (start < 0 || end <= start) {
			return Optional.empty();
		}
		try {
			JsonNode root = jsonMapper.readTree(raw.substring(start, end + 1));
			if (!root.isObject()) {
				return Optional.empty();
			}
			boolean understood = !root.path("understood").isBoolean() || root.path("understood").booleanValue();
			String reply = root.path("reply").isString() && !root.path("reply").asString().isBlank()
				? root.path("reply").asString().trim()
				: null;
			JsonNode updates = root.path("updates").isObject() ? root.path("updates") : null;
			boolean finished = root.path("finished").isBoolean() && root.path("finished").booleanValue();
			boolean followUp = root.path("followUp").isBoolean() && root.path("followUp").booleanValue();
			boolean explain = root.path("explain").isBoolean() && root.path("explain").booleanValue();
			return Optional.of(new ModelOutput(understood, reply, updates, finished, followUp, explain,
				sign(root.path("sign"))));
		} catch (RuntimeException e) {
			return Optional.empty();
		}
	}

	private static SkinSign sign(JsonNode node) {
		if (!node.isString()) {
			return null;
		}
		try {
			return SkinSign.valueOf(node.asString().trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

}
