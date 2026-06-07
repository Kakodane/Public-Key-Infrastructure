package com.ib.domain.repository;

import com.ib.domain.entity.Organization;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
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

	@Query("""
			SELECT COUNT(o) FROM Organization o
			WHERE o.encryptedOrgKey IS NOT NULL
			AND (o.masterKeyVersion IS NULL OR o.masterKeyVersion < :targetVersion)
			""")
	long countNeedingMasterKeyRotation(@Param("targetVersion") int targetVersion);

	@Query("""
			SELECT o FROM Organization o
			WHERE o.encryptedOrgKey IS NOT NULL
			AND (o.masterKeyVersion IS NULL OR o.masterKeyVersion < :targetVersion)
			""")
	List<Organization> findNeedingMasterKeyRotation(
			@Param("targetVersion") int targetVersion, Pageable pageable);
}
