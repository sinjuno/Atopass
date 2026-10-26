package com.atopy.Atopass.domain.chatbot.service;

/** 상황 질문(P01~P07) 하나의 규칙. id가 같으면 "같은 상황 질문"으로 보고 3일 제외 규칙을 적용한다. */
interface SituationalQuestionRule {

	String id();

	/** 낮을수록 순위가 높다. 여러 규칙이 동시에 맞으면 가장 낮은 값을 고른다. */
	int priority();

	boolean matches(SituationalQuestionContext context);

}
