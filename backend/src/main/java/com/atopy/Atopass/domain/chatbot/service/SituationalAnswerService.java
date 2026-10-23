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

/** 상황 질문(P01~P05)·마무리 질문(C02)의 답을 메인 기록과 별도로 저장한다. */
@Service
@RequiredArgsConstructor
class SituationalAnswerService {

	private final SituationalAnswerRepository answerRepository;
	private final ClinicQuestionNoteRepository clinicQuestionNoteRepository;
	private final MemberRepository memberRepository;
	private final JsonMapper jsonMapper;

	/** 선택지로 답하는 상황 질문(P01·P04·P05)의 답. 예: {"answer":"TODAY"} */
	@Transactional
	void saveChoice(Long memberId, LocalDate recordDate, String questionId, String answerCode) {
		ObjectNode json = jsonMapper.createObjectNode();
		json.put("answer", answerCode);
		save(memberId, recordDate, questionId, json);
	}

	/** 글로 답하는 상황 질문(P02)의 답. subject는 질문에 넣은 이름(예: 새로 생긴 증상)이다. 예: {"subject":"홍반","text":"..."} */
	@Transactional
	void saveText(Long memberId, LocalDate recordDate, String questionId, String subject, String text) {
		ObjectNode json = jsonMapper.createObjectNode();
		if (subject != null) {
			json.put("subject", subject);
		}
		json.put("text", text);
		save(memberId, recordDate, questionId, json);
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

	/** 같은 (회원, 날짜, 질문) 답이 이미 있으면(예: 그날 기록을 지우고 다시 기록한 경우) 새로 만들지 않고 바꾼다. */
	private void save(Long memberId, LocalDate recordDate, String questionId, ObjectNode json) {
		String answerJson = jsonMapper.writeValueAsString(json);
		answerRepository.findByMemberIdAndRecordDateAndQuestionId(memberId, recordDate, questionId)
			.ifPresentOrElse(
				existing -> existing.replaceAnswer(answerJson),
				() -> {
					Member member = memberRepository.getReferenceById(memberId);
					answerRepository.save(SituationalAnswer.create(member, recordDate, questionId, answerJson));
				});
	}

}
