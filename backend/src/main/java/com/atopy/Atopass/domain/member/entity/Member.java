package com.atopy.Atopass.domain.member.entity;

import com.atopy.Atopass.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "members")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private String email;

	/** BCrypt 해시. 원문은 저장하지 않는다. */
	@Column(nullable = false)
	private String password;

	@Column(nullable = false, length = 50)
	private String nickname;

	/** 챗봇 이용(대화·기록 내용을 외부 LLM에 전달)에 동의한 시각. 동의하지 않았거나 철회했으면 null */
	private LocalDateTime llmConsentedAt;

	@Builder
	private Member(String email, String password, String nickname) {
		this.email = email;
		this.password = password;
		this.nickname = nickname;
	}

	public void changeNickname(String nickname) {
		this.nickname = nickname;
	}

	public void changePassword(String encodedPassword) {
		this.password = encodedPassword;
	}

	public boolean hasLlmConsent() {
		return llmConsentedAt != null;
	}

	/** 이미 동의했다면 처음 동의한 시각을 그대로 둔다. */
	public void agreeLlm(LocalDateTime now) {
		if (llmConsentedAt == null) {
			this.llmConsentedAt = now;
		}
	}

	public void withdrawLlmConsent() {
		this.llmConsentedAt = null;
	}

}
