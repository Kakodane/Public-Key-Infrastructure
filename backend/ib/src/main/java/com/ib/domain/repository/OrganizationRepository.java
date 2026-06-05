package com.ib.domain.repository;

import com.ib.domain.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

	Optional<Organization> findByNameIgnoreCase(String name);

	@Modifying
	@Query("""
			UPDATE Organization o
			SET o.masterKeyVersion = :version
			WHERE o.encryptedOrgKey IS NOT NULL AND o.masterKeyVersion IS NULL
			""")
	int backfillMasterKeyVersionWhereMissing(@Param("version") int version);
}
