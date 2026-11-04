package com.atopy.Atopass.domain.medication.repository;

import com.atopy.Atopass.domain.medication.entity.MedicationHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicationHistoryRepository extends JpaRepository<MedicationHistory, Long> {

	List<MedicationHistory> findByMedicationIdOrderByChangedAtDescIdDesc(Long medicationId);

}
