package com.atopy.Atopass.domain.chatbot.service;

import com.atopy.Atopass.domain.chatbot.dto.response.SituationalAnswerItem;
import com.atopy.Atopass.domain.chatbot.entity.SituationalAnswer;
import com.atopy.Atopass.domain.chatbot.repository.SituationalAnswerRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 상황 질문(P01~P07)·마무리 질문(C02) 답을 기록 화면·재진 PDF에 보여 줄 글로 바꿔 읽는다.
 * 하루 안에서는 상황 질문을 먼저, 마무리 질문을 나중에 둔다(챗봇이 물은 순서).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SituationalAnswerReader {

	/** 질문별 제목 */
	private static final Map<String, String> TITLES = Map.of(
		"P01", "고통이 심해진 때",
		"P02", "새로 생긴 증상",
		"P03", "요즘 생활에서 달라진 것",
		"P04", "잠을 적게 잔 이유",
		"P05", "약을 못 챙긴 이유",
		"P06", "며칠 만의 기록, 그동안 피부",
		"P07", "좋아지기 전에 달라진 것");
	/** 선택지 답 코드의 글자. 챗봇 화면의 버튼 글자(ChatbotService의 SITUATIONAL_CHOICE_LABELS)와 같다. */
	private static final Map<String, Map<String, String>> CHOICE_LABELS = Map.of(
		"P01", Map.of("TODAY", "오늘", "SINCE_YESTERDAY", "어제부터", "FEW_DAYS_AGO", "2~3일 전"),
		"P04", Map.of("ITCH_WOKE", "가려워서 깸", "LATE_SLEEP", "늦게 잠", "BOTH", "둘 다", "OTHER", "기타"),
		"P05", Map.of("FORGOT", "깜빡함", "RAN_OUT", "다 떨어짐", "AWAY", "외출 중", "OTHER", "기타"),
		"P06", Map.of("SIMILAR", "비슷했어요", "BETTER", "좋아졌어요", "WORSE", "나빠졌어요"));
	/** C02 항목 이름과 제목. 이 순서대로 보여 준다. */
	private static final List<Map.Entry<String, String>> C02_ITEMS = List.of(
		Map.entry("productNote", "새로 쓴 제품"),
		Map.entry("unusualNote", "평소와 다른 일"),
		Map.entry("clinicQuestion", "진료 때 물어볼 것"));

	private final SituationalAnswerRepository answerRepository;
	private final JsonMapper jsonMapper;

	/** 그날의 답. 없으면 빈 목록 */
	public List<SituationalAnswerItem> forDay(Long memberId, LocalDate recordDate) {
		return items(answerRepository.findByMemberIdAndRecordDate(memberId, recordDate));
	}

	/** 기간 안의 답을 날짜별로. 답이 없는 날은 빠진다. */
	public Map<LocalDate, List<SituationalAnswerItem>> forPeriod(Long memberId, LocalDate startDate, LocalDate endDate) {
		Map<LocalDate, List<SituationalAnswer>> byDate = new TreeMap<>();
		for (SituationalAnswer answer : answerRepository.findByMemberIdAndRecordDateBetween(memberId, startDate, endDate)) {
			byDate.computeIfAbsent(answer.getRecordDate(), date -> new ArrayList<>()).add(answer);
		}
		Map<LocalDate, List<SituationalAnswerItem>> result = new TreeMap<>();
		byDate.forEach((date, answers) -> {
			List<SituationalAnswerItem> items = items(answers);
			if (!items.isEmpty()) {
				result.put(date, items);
			}
		});
		return result;
	}

	private List<SituationalAnswerItem> items(List<SituationalAnswer> answers) {
		List<SituationalAnswerItem> items = new ArrayList<>();
		answers.stream()
			.sorted(Comparator.comparing((SituationalAnswer a) -> "C02".equals(a.getQuestionId()))
				.thenComparing(SituationalAnswer::getQuestionId))
			.forEach(answer -> addItems(items, answer));
		return items;
	}

	private void addItems(List<SituationalAnswerItem> items, SituationalAnswer answer) {
		String id = answer.getQuestionId();
		JsonNode json;
		try {
			json = jsonMapper.readTree(answer.getAnswerJson());
		} catch (RuntimeException e) {
			// 형식이 깨진 답은 보여 주지 않는다.
			return;
		}
		if ("C02".equals(id)) {
			for (Map.Entry<String, String> item : C02_ITEMS) {
				String text = text(json, item.getKey());
				if (text != null) {
					items.add(new SituationalAnswerItem(id, item.getValue(), text));
				}
			}
			return;
		}
		String title = TITLES.get(id);
		String answerText = answerText(id, json);
		if (title != null && answerText != null) {
			items.add(new SituationalAnswerItem(id, title, answerText));
		}
	}

	/**
	 * 선택지 답은 버튼 글자로, 글 답은 적은 그대로. P02는 새로 생긴 증상 이름을 앞에 붙이고("홍반: ..."),
	 * P06은 고른 답 뒤에 덧붙인 글을 붙인다("좋아졌어요. ...", 챗봇 대화 내역과 같다).
	 */
	private static String answerText(String id, JsonNode json) {
		String code = text(json, "answer");
		String label = code == null || !CHOICE_LABELS.containsKey(id) ? null
			: CHOICE_LABELS.get(id).getOrDefault(code, code);
		String text = text(json, "text");
		if (label != null) {
			return text == null ? label : label + ". " + text;
		}
		if (text == null) {
			return null;
		}
		String subject = text(json, "subject");
		return subject == null ? text : subject + ": " + text;
	}

	private static String text(JsonNode json, String field) {
		JsonNode node = json.path(field);
		if (!node.isString() || node.asString().isBlank()) {
			return null;
		}
		return node.asString();
	}

}
