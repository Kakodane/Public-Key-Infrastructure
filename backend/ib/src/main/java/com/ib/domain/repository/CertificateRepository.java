package com.ib.domain.repository;

import com.ib.domain.entity.Certificate;
import com.ib.domain.enums.CertificateStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CertificateRepository extends JpaRepository<Certificate, Long> {

	List<Certificate> findAllByOrganization_Id(Long organizationId);

	List<Certificate> findAllByOwner_Id(Long ownerUserId);

	List<Certificate> findAllByOrganization_IdAndStatus(Long organizationId, CertificateStatus status);

    List<Certificate> findAll();
}
