package com.ib.domain.repository;

import com.ib.domain.entity.MasterKeyRotationJob;
import com.ib.domain.enums.MasterKeyRotationJobStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MasterKeyRotationJobRepository extends JpaRepository<MasterKeyRotationJob, Long> {

	Optional<MasterKeyRotationJob> findFirstByStatusOrderByStartedAtDesc(MasterKeyRotationJobStatus status);

	Optional<MasterKeyRotationJob> findFirstByStatus(MasterKeyRotationJobStatus status);
}
