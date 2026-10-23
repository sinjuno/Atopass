package com.atopy.Atopass.domain.chatbot.service;

import com.atopy.Atopass.domain.chatbot.entity.SituationalAnswer;
import com.atopy.Atopass.domain.chatbot.repository.SituationalAnswerRepository;
import com.atopy.Atopass.domain.member.entity.Member;
import com.atopy.Atopass.domain.member.repository.MemberRepository;
import com.atopy.Atopass.domain.record.entity.ClinicQuestionNote;
import com.atopy.Atopass.domain.record.repository.ClinicQuestionNoteRepository;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** 상황 질문(P01 등)·마무리 질문(C02)의 답을 메인 기록과 별도로 저장한다. */
@Service
@RequiredArgsConstructor
class SituationalAnswerService {

	private final SituationalAnswerRepository answerRepository;
	private final ClinicQuestionNoteRepository clinicQuestionNoteRepository;
	private final MemberRepository memberRepository;
	private final JsonMapper jsonMapper;

	@Transactional
	void saveP01(Long memberId, LocalDate recordDate, String answerCode) {
		ObjectNode json = jsonMapper.createObjectNode();
		json.put("answer", answerCode);
		save(memberId, recordDate, "P01", json);
	}

	/** productNote·unusualNote·clinicQuestion 중 null이 아닌 것만 저장한다. clinicQuestion은 재진 질문 목록에도 남긴다. */
	@Transactional
	void saveC02(Long memberId, LocalDate recordDate, String productNote, String unusualNote, String clinicQuestion) {
		ObjectNode json = jsonMapper.createObjectNode();
		if (productNote != null) {
			json.put("productNote", productNote);
		}
		if (unusualNote != null) {
			json.put("unusualNote", unusualNote);
		}
		if (clinicQuestion != null) {
			json.put("clinicQuestion", clinicQuestion);
		}
		save(memberId, recordDate, "C02", json);
		if (clinicQuestion != null && !clinicQuestion.isBlank()) {
			Member member = memberRepository.getReferenceById(memberId);
			clinicQuestionNoteRepository.save(ClinicQuestionNote.create(member, recordDate, clinicQuestion));
		}
	}

	private void save(Long memberId, LocalDate recordDate, String questionId, ObjectNode json) {
		Member member = memberRepository.getReferenceById(memberId);
		answerRepository.save(SituationalAnswer.create(member, recordDate, questionId, jsonMapper.writeValueAsString(json)));
	}

}
