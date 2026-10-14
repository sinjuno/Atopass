package com.atopy.Skinlog.domain.record.repository;

import com.atopy.Skinlog.domain.record.entity.BodyPart;
import com.atopy.Skinlog.domain.record.entity.Record;
import com.atopy.Skinlog.domain.record.entity.SkinCondition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class RecordRepositoryTest {

    @Autowired RecordRepository recordRepository;

    @Test
    void saveWithSkinConditions_roundTripsWithAssociation() {
        Record record = new Record(1L, LocalDate.of(2026, 10, 10));
        SkinCondition arm = new SkinCondition(BodyPart.ARM);
        arm.setItching(4);
        record.addSkinCondition(arm);

        Record saved = recordRepository.save(record);

        Record found = recordRepository.findById(saved.getId()).orElseThrow();
        assertThat(found.getSkinConditions()).hasSize(1);
        assertThat(found.getSkinConditions().get(0).getBodyPart()).isEqualTo(BodyPart.ARM);
        assertThat(found.getSkinConditions().get(0).getItching()).isEqualTo(4);
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
