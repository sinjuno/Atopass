package com.atopy.Atopass.domain.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "refresh_tokens", indexes = @Index(name = "idx_refresh_tokens_member", columnList = "member_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	/** 리프레시 토큰 원문의 SHA-256 해시. 원문은 저장하지 않는다. */
	@Column(nullable = false, unique = true)
	private String tokenHash;

	@Column(nullable = false)
	private LocalDateTime expiresAt;

	@Column(nullable = false)
	private boolean revoked;

	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	@Builder
	private RefreshToken(Member member, String tokenHash, LocalDateTime expiresAt) {
		this.member = member;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
		this.revoked = false;
		this.createdAt = LocalDateTime.now();
	}

	public boolean isUsable(LocalDateTime now) {
		return !revoked && expiresAt.isAfter(now);
	}

	public void revoke() {
		this.revoked = true;
	}

}
