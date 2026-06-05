package com.ib.domain.repository;

import com.ib.domain.entity.MasterKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MasterKeyRepository extends JpaRepository<MasterKey, Long> {

	Optional<MasterKey> findByVersion(int version);

	Optional<MasterKey> findByActiveTrue();
}
