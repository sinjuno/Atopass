package com.atopy.Skinlog.domain.record.repository;

import com.atopy.Skinlog.domain.record.entity.Record;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface RecordRepository extends JpaRepository<Record, Long> {

    List<Record> findByMemberIdAndRecordDateBetween(Long memberId, LocalDate start, LocalDate end);

    Optional<Record> findByMemberIdAndRecordDate(Long memberId, LocalDate date);
}
