package com.atopy.Skinlog.domain.record.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

// TEMP STUB — 백1의 실제 피부 기록 엔티티 머지 시 교체
@Entity
@Table(name = "record", uniqueConstraints = @UniqueConstraint(columnNames = {"member_id", "record_date"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Record {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private LocalDate recordDate;

    @OneToMany(mappedBy = "record", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SkinCondition> skinConditions = new ArrayList<>();

    public Record(Long memberId, LocalDate recordDate) {
        this.memberId = memberId;
        this.recordDate = recordDate;
    }

    public void addSkinCondition(SkinCondition condition) {
        skinConditions.add(condition);
        condition.assignRecord(this);
    }
}
