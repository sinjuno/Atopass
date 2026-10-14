package com.atopy.Skinlog.domain.record.repository;

import com.atopy.Skinlog.domain.record.entity.BodyPart;
import com.atopy.Skinlog.domain.record.entity.Record;
import com.atopy.Skinlog.domain.record.entity.SkinCondition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class RecordRepositoryTest {

    @Autowired RecordRepository recordRepository;
    @Autowired TestEntityManager entityManager;

    @Test
    void saveWithSkinConditions_roundTripsWithAssociationAfterReload() {
        Record record = new Record(1L, LocalDate.of(2026, 10, 10));
        SkinCondition arm = new SkinCondition(BodyPart.ARM);
        arm.setItching(4);
        SkinCondition face = new SkinCondition(BodyPart.FACE);
        face.setRedness(2);
        record.addSkinCondition(arm);
        record.addSkinCondition(face);

        Record saved = recordRepository.save(record);
        entityManager.flush();
        entityManager.clear();

        Record found = recordRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getSkinConditions()).hasSize(2);
        SkinCondition reloadedArm = found.getSkinConditions().stream()
                .filter(c -> c.getBodyPart() == BodyPart.ARM)
                .findFirst().orElseThrow();
        assertThat(reloadedArm.getItching()).isEqualTo(4);
        assertThat(reloadedArm.getRecord().getId()).isEqualTo(found.getId());
    }

    @Test
    void removingSkinCondition_orphanIsDeletedAfterReload() {
        Record record = new Record(1L, LocalDate.of(2026, 10, 11));
        SkinCondition arm = new SkinCondition(BodyPart.ARM);
        SkinCondition face = new SkinCondition(BodyPart.FACE);
        record.addSkinCondition(arm);
        record.addSkinCondition(face);
        Record saved = recordRepository.save(record);
        entityManager.flush();
        entityManager.clear();

        Record reloaded = recordRepository.findById(saved.getId()).orElseThrow();
        reloaded.getSkinConditions().remove(0);
        recordRepository.save(reloaded);
        entityManager.flush();
        entityManager.clear();

        Record found = recordRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getSkinConditions()).hasSize(1);
    }

    @Test
    void duplicateMemberIdAndRecordDate_violatesUniqueConstraint() {
        recordRepository.save(new Record(1L, LocalDate.of(2026, 10, 10)));
        entityManager.flush();

        assertThatThrownBy(() -> {
            recordRepository.save(new Record(1L, LocalDate.of(2026, 10, 10)));
            entityManager.flush();
        }).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void findByMemberIdAndRecordDateBetween_returnsOnlyMatchingRange() {
        recordRepository.save(new Record(1L, LocalDate.of(2026, 10, 1)));
        recordRepository.save(new Record(1L, LocalDate.of(2026, 10, 20)));

        var results = recordRepository.findByMemberIdAndRecordDateBetween(
                1L, LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 25));

        assertThat(results).hasSize(1);
    }
}
