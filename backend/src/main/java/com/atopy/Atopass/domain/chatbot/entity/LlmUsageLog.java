package com.atopy.Atopass.domain.chatbot.entity;

import com.atopy.Atopass.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 챗봇 메시지가 아닌 LLM 호출(약 봉투 사진 읽기 등) 한 번. 하루 이용 한도를 챗봇 메시지와 합쳐 세려고 남긴다.
 * 내용은 저장하지 않는다.
 */
@Getter
@Entity
@Table(name = "llm_usage_logs", indexes = @Index(name = "idx_llm_usage_member_created", columnList = "memberId, createdAt"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LlmUsageLog extends BaseTimeEntity {

	public static final String KIND_MEDICATION_EXTRACT = "MEDICATION_EXTRACT";

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long memberId;

	@Column(nullable = false, length = 30)
	private String kind;

	public static LlmUsageLog of(Long memberId, String kind) {
		LlmUsageLog log = new LlmUsageLog();
		log.memberId = memberId;
		log.kind = kind;
		return log;
	}

}
