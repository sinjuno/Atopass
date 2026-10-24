package com.atopy.Atopass.domain.record.repository;

import com.atopy.Atopass.domain.record.entity.ClinicQuestionNote;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClinicQuestionNoteRepository extends JpaRepository<ClinicQuestionNote, Long> {

	List<ClinicQuestionNote> findByMemberIdOrderByCreatedAtDesc(Long memberId);

}
