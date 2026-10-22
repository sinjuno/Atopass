package com.atopy.Atopass.domain.chatbot.service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 연결 질문 풀. 필수 항목 사이에 들어가는 부가 질문이다. (기획 시트 "연결 질문 풀" 기준)
 * 질문 문장과 선택지는 여기서만 고른다. 언제 묻는지(출현 조건·선택 규칙)는 {@link ChatDraft}가 정한다.
 *
 * @param label    기록에 남길 이름 (예: 불편 시간대)
 * @param options  선택지. 한 개만 고른다.
 * @param freeText 고르면 글로 직접 적게 하는 선택지. 없으면 null
 */
public enum LinkedQuestion {
	C01("언제 제일 신경 쓰였어요?", "불편 시간대", Priority.HIGH,
		List.of("씻을 때", "옷 입을 때", "일·공부할 때", "자려고 누웠을 때"), null),
	C02("가려움이 확 심해진 순간이 있었어요?", "악화 순간", Priority.HIGH,
		List.of("밥 먹고", "운동 후", "더운 곳에서", "딱히 없음"), null),
	C03("목에 오늘 계속 닿은 게 있었나요?", "접촉 요인", Priority.NORMAL,
		List.of("목도리·옷깃", "머리카락", "땀", "없음"), null),
	C04("오늘 얼굴에 새로 바른 게 있어요?", "새 제품", Priority.NORMAL,
		List.of("없음", "있음(사진 올리기)", "직접 입력"), "직접 입력"),
	C05("오늘 손을 평소보다 많이 씻었어요?", "손 씻기 빈도", Priority.NORMAL,
		List.of("비슷", "더 많이", "잘 모르겠어요"), null),
	C06("씻고 나서 보습제는 바로 발랐어요?", "보습 시점", Priority.HIGH,
		List.of("바로", "시간 지나서", "안 바름"), null),
	C07("만지면 뜨겁거나 부은 느낌도 있어요?", "붓기·열감", Priority.NORMAL,
		List.of("예", "아니요"), null),
	C08("알고 긁었어요, 모르게 긁었어요?", "긁는 상황", Priority.HIGH,
		List.of("알고 긁음", "모르게 긁음", "자다가 긁음"), null),
	C09("언제 처음 봤어요?", "발견 시점", Priority.NORMAL,
		List.of("아침에 일어나서", "낮에", "씻다가"), null),
	C10("평소보다 늦게 잤어요?", "취침 시간 변화", Priority.NORMAL,
		List.of("예", "아니요"), null),
	C11("땀 흘린 뒤 바로 씻었어요?", "땀 관리", Priority.NORMAL,
		List.of("바로", "시간 지나서", "못 씻음"), null),
	C12("평소랑 다르게 먹은 게 있었어요?", "음식 메모", Priority.NORMAL,
		List.of("매운 음식", "술", "처음 먹는 음식", "직접 입력"), "직접 입력"),
	C13("오늘 마스크를 오래 썼어요?", "마스크 착용", Priority.LOW,
		List.of("안 씀", "잠깐", "반나절 이상"), null),
	C14("요즘 난방을 켜고 지내요?", "실내 난방", Priority.LOW,
		List.of("자주 켬", "가끔", "안 켬"), null),
	C15("오늘 니트나 까슬한 옷을 입었어요?", "옷 소재", Priority.LOW,
		List.of("예", "아니요"), null),
	C16("요즘 에어컨을 오래 켜고 지내요?", "에어컨 사용", Priority.LOW,
		List.of("자주 켬", "가끔", "안 켬"), null);

	/** 원인 탐색형(높음)은 나빠진 날 먼저, 순환형(낮음)은 비슷한 날에 묻는다. */
	enum Priority {
		HIGH, NORMAL, LOW
	}

	private static final Map<String, LinkedQuestion> BY_ID = Arrays.stream(values())
		.collect(Collectors.toMap(Enum::name, Function.identity()));

	private final String text;
	private final String label;
	private final Priority priority;
	private final List<String> options;
	private final String freeText;

	LinkedQuestion(String text, String label, Priority priority, List<String> options, String freeText) {
		this.text = text;
		this.label = label;
		this.priority = priority;
		this.options = options;
		this.freeText = freeText;
	}

	public static Optional<LinkedQuestion> find(String id) {
		return Optional.ofNullable(id == null ? null : BY_ID.get(id));
	}

	String text() {
		return text;
	}

	/** 기록에 남길 이름 (예: 불편 시간대) */
	public String label() {
		return label;
	}

	Priority priority() {
		return priority;
	}

	List<String> options() {
		return options;
	}

	/** 이 답을 고르면 글로 직접 적는지 */
	boolean asksFreeText(String answer) {
		return freeText != null && freeText.equals(answer);
	}

}
