package com.atopy.Atopass.domain.record.repository;

import com.atopy.Atopass.domain.record.entity.Photo;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PhotoRepository extends JpaRepository<Photo, Long> {

	List<Photo> findByRecordIdOrderByCreatedAtAsc(Long recordId);

	Optional<Photo> findByIdAndRecordId(Long id, Long recordId);

	long countByRecordId(Long recordId);

}
