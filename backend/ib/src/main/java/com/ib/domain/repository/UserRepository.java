package com.ib.domain.repository;

import com.ib.domain.entity.User;
import com.ib.domain.enums.Role;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmailIgnoreCase(String email);

	boolean existsByEmailIgnoreCase(String email);

	List<User> findAllByRole(Role role);

	List<User> findAllByOrganization_Id(Long organizationId);

	@Modifying
	@Query("""
			UPDATE User u
			SET u.totpMasterKeyVersion = :version
			WHERE u.encryptedTotpSecret IS NOT NULL AND u.totpMasterKeyVersion IS NULL
			""")
	int backfillTotpMasterKeyVersionWhereMissing(@Param("version") int version);

	@Query("""
			SELECT COUNT(u) FROM User u
			WHERE u.encryptedTotpSecret IS NOT NULL
			AND (u.totpMasterKeyVersion IS NULL OR u.totpMasterKeyVersion < :targetVersion)
			""")
	long countNeedingTotpMasterKeyRotation(@Param("targetVersion") int targetVersion);

	@Query("""
			SELECT u FROM User u
			WHERE u.encryptedTotpSecret IS NOT NULL
			AND (u.totpMasterKeyVersion IS NULL OR u.totpMasterKeyVersion < :targetVersion)
			""")
	List<User> findNeedingTotpMasterKeyRotation(
			@Param("targetVersion") int targetVersion, Pageable pageable);
}
