package com.ib.service.certificate;

import com.ib.domain.entity.Certificate;
import com.ib.domain.enums.CertificateType;
import com.ib.domain.repository.CertificateRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CrlRefreshScheduler {

    private static final Logger log = LoggerFactory.getLogger(CrlRefreshScheduler.class);

    private final CertificateRepository certificateRepository;
    private final CrlService crlService;

    @Scheduled(cron = "${app.crl.refresh-cron}")
    public void refreshAllCrls() {
        log.info("Scheduled CRL refresh triggered.");
        for (Certificate ca : certificateRepository.findAllByTypeNot(CertificateType.END_ENTITY)) {
            try {
                crlService.regenerateForCa(ca.getId());
            } catch (Exception e) {
                log.warn("Failed to refresh CRL for CA certificate {}: {}", ca.getId(), e.getMessage());
            }
        }
    }
}