package com.atopy.Skinlog.domain.record.entity;

import com.atopy.Skinlog.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 기록에 첨부된 사진. Record 하위 리소스라 연관관계 대신 recordId만 들고 있다. */
@Getter
@Entity
@Table(name = "photos")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Photo extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private Long recordId;

	@Column(nullable = false)
	private String storageKey;

	@Column(nullable = false)
	private String originalFilename;

	@Column(nullable = false)
	private String contentType;

	@Column(nullable = false)
	private long size;

	@Builder
	private Photo(Long recordId, String storageKey, String originalFilename, String contentType, long size) {
		this.recordId = recordId;
		this.storageKey = storageKey;
		this.originalFilename = originalFilename;
		this.contentType = contentType;
		this.size = size;
	}

}
