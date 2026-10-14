package com.atopy.Skinlog.domain.record.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// TEMP STUB — 백1의 실제 피부 기록 엔티티 머지 시 교체
@Entity
@Table(name = "skin_condition")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SkinCondition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "record_id")
    private Record record;

    @Enumerated(EnumType.STRING)
    private BodyPart bodyPart;

    private Integer itching;
    private Integer stinging;
    private Integer redness;
    private Integer dryness;
    private Integer flaking;
    private Integer swelling;
    private Integer pain;

    public SkinCondition(BodyPart bodyPart) {
        this.bodyPart = bodyPart;
    }

    void assignRecord(Record record) {
        this.record = record;
    }

    public void setItching(Integer itching) {
        this.itching = itching;
    }

    public void setStinging(Integer stinging) {
        this.stinging = stinging;
    }

    public void setRedness(Integer redness) {
        this.redness = redness;
    }

    public void setDryness(Integer dryness) {
        this.dryness = dryness;
    }

    public void setFlaking(Integer flaking) {
        this.flaking = flaking;
    }

    public void setSwelling(Integer swelling) {
        this.swelling = swelling;
    }

    public void setPain(Integer pain) {
        this.pain = pain;
    }
}
