package com.ib.crypto;

import com.ib.service.crypto.MasterKeyRotationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MasterKeyRotationScheduler {

	private static final Logger log = LoggerFactory.getLogger(MasterKeyRotationScheduler.class);

	private final CryptoProperties cryptoProperties;
	private final MasterKeyRotationService rotationService;

	public MasterKeyRotationScheduler(CryptoProperties cryptoProperties,
	                                  MasterKeyRotationService rotationService) {
		this.cryptoProperties = cryptoProperties;
		this.rotationService = rotationService;
	}

	@Scheduled(cron = "${crypto.master-key-rotation-cron}")
	public void rotateIfDue() {
		if (!cryptoProperties.masterKeyRotationEnabled()) {
			return;
		}
		log.info("Scheduled master key rotation triggered.");
		rotationService.rotate();
	}
}
