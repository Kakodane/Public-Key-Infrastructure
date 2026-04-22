package com.ib.domain.repository;

import com.ib.domain.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {

	Optional<Organization> findByNameIgnoreCase(String name);
}
