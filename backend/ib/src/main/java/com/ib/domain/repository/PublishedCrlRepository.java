package com.ib.domain.repository;

import com.ib.domain.entity.PublishedCrl;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PublishedCrlRepository extends JpaRepository<PublishedCrl, Long> {

    Optional<PublishedCrl> findByCaCertificate_Id(Long caCertificateId);
}
