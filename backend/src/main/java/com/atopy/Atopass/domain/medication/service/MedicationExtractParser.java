package com.atopy.Atopass.domain.medication.service;

import com.atopy.Atopass.domain.medication.dto.response.MedicationExtractResponse;
import com.atopy.Atopass.domain.medication.entity.MedicationTime;
import com.atopy.Atopass.domain.medication.entity.MedicationType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * LLM이 사진에서 읽어 준 글(JSON 배열)을 약 목록으로 바꾼다.
 * 약속한 칸(이름, 종류, 용량, 시간 원문, 시간대, 일수)만 꺼내므로, LLM이 환자 이름 같은 다른 칸을 보내와도 응답에 담기지 않는다.
 * 형식이 틀렸거나 읽은 약이 없으면 빈 목록을 돌려준다. 내용은 로그에 남기지 않는다.
 */
@Slf4j
@Component
public class MedicationExtractParser {

	private static final int MAX_NAME = 100;
	private static final int MAX_DOSE = 50;
	private static final int MAX_TIMES_TEXT = 100;
	private static final int MAX_DAYS = 365;

	private final JsonMapper jsonMapper;

	public MedicationExtractParser(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	public List<MedicationExtractResponse.Item> parse(String raw) {
		JsonNode root = readArray(raw);
		if (root == null) {
			return List.of();
		}
		Map<String, Draft> merged = new LinkedHashMap<>();
		for (JsonNode node : root) {
			Draft draft = toDraft(node);
			if (draft != null) {
				merged.merge(draft.key(), draft, Draft::mergeWith);
			}
		}
		return merged.values().stream().map(Draft::toItem).toList();
	}

	/** 코드 블록(```)이 붙어 와도 가장 바깥 [ ] 사이만 꺼내 읽는다. 배열이 아니면 null */
	private JsonNode readArray(String raw) {
		if (raw == null) {
			return null;
		}
		int start = raw.indexOf('[');
		int end = raw.lastIndexOf(']');
		if (start < 0 || end <= start) {
			return null;
		}
		try {
			JsonNode node = jsonMapper.readTree(raw.substring(start, end + 1));
			return node.isArray() ? node : null;
		} catch (RuntimeException e) {
			log.warn("약 봉투 인식 응답을 읽지 못했습니다: {}", e.getClass().getSimpleName());
			return null;
		}
	}

	private static Draft toDraft(JsonNode node) {
		if (!node.isObject()) {
			return null;
		}
		String name = text(node.get("name"), MAX_NAME);
		if (name == null) {
			return null;
		}
		MedicationType type = MedicationType.fromCode(lower(text(node.get("type"), 20))).orElse(null);
		TreeSet<MedicationTime> times = new TreeSet<>();
		JsonNode timesNode = node.get("times");
		if (timesNode != null && timesNode.isArray()) {
			for (JsonNode time : timesNode) {
				MedicationTime.fromCode(lower(text(time, 20))).ifPresent(times::add);
			}
		}
		return new Draft(name, type, text(node.get("dose"), MAX_DOSE), times,
			text(node.get("timesText"), MAX_TIMES_TEXT), days(node.get("days")));
	}

	/** 문자열 값만 받아 앞뒤 공백을 지우고 길이를 제한한다. 비었거나 문자열이 아니면 null */
	private static String text(JsonNode node, int maxLength) {
		if (node == null || !node.isString()) {
			return null;
		}
		String value = node.asString().trim();
		if (value.isEmpty()) {
			return null;
		}
		return value.length() > maxLength ? value.substring(0, maxLength) : value;
	}

	private static Integer days(JsonNode node) {
		if (node == null || !node.isIntegralNumber()) {
			return null;
		}
		int value = node.asInt();
		return value >= 1 && value <= MAX_DAYS ? value : null;
	}

	private static String lower(String value) {
		return value == null ? null : value.toLowerCase(Locale.ROOT);
	}

	/** 같은 약 이름(공백·대소문자 무시)이 여러 장에서 나오면 하나로 합친다. 먼저 나온 값을 우선하고 빈 값만 채운다. */
	private record Draft(String name, MedicationType type, String dose, TreeSet<MedicationTime> times,
		String timesText, Integer days) {

		String key() {
			return name.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
		}

		Draft mergeWith(Draft other) {
			TreeSet<MedicationTime> allTimes = new TreeSet<>(times);
			allTimes.addAll(other.times);
			return new Draft(name,
				type != null ? type : other.type,
				dose != null ? dose : other.dose,
				allTimes,
				timesText != null ? timesText : other.timesText,
				days != null ? days : other.days);
		}

		MedicationExtractResponse.Item toItem() {
			List<String> codes = new ArrayList<>();
			times.forEach(time -> codes.add(time.code()));
			return MedicationExtractResponse.Item.of(
				name, type == null ? null : type.code(), dose, codes, timesText, days);
		}

	}

}
