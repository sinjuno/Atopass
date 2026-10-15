package com.atopy.Atopass.domain.report.entity;

import com.atopy.Atopass.domain.member.entity.Member;
import com.atopy.Atopass.global.entity.BaseTimeEntity;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 진료 참고용 보고서. 기간·옵션·생성일(메타데이터)은 영구 보존하고, PDF 파일은 생성 30일 뒤 삭제한다.
 * 파일이 없으면 다운로드할 때 다시 생성한다.
 */
@Getter
@Entity
@Table(name = "reports", indexes = @Index(name = "idx_reports_member", columnList = "member_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@Column(nullable = false)
	private LocalDate startDate;

	@Column(nullable = false)
	private LocalDate endDate;

	@Column(nullable = false)
	private boolean includePhotos;

	/** 저장된 PDF 파일명. 파일이 삭제되면 null */
	@Column(length = 100)
	private String fileName;

	private LocalDateTime fileExpiresAt;

	@Builder
	private Report(Member member, LocalDate startDate, LocalDate endDate, boolean includePhotos) {
		this.member = member;
		this.startDate = startDate;
		this.endDate = endDate;
		this.includePhotos = includePhotos;
	}

	public void attachFile(String fileName, LocalDateTime expiresAt) {
		this.fileName = fileName;
		this.fileExpiresAt = expiresAt;
	}

	public void detachFile() {
		this.fileName = null;
		this.fileExpiresAt = null;
	}

}
