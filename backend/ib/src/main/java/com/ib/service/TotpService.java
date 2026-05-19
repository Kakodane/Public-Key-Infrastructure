package com.ib.service;

import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.qr.QrData;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.springframework.stereotype.Service;

@Service
public class TotpService {

	private static final String ISSUER = "PKI";
	private static final int TIME_PERIOD_DISCREPANCY = 1;

	private final SecretGenerator secretGenerator;
	private final DefaultCodeVerifier codeVerifier;

	public TotpService() {
		this.secretGenerator = new DefaultSecretGenerator();
		this.codeVerifier = new DefaultCodeVerifier(new DefaultCodeGenerator(), new SystemTimeProvider());
		codeVerifier.setAllowedTimePeriodDiscrepancy(TIME_PERIOD_DISCREPANCY);
	}

	public String generateSecret() {
		return secretGenerator.generate();
	}

	public String generateQrCodeUri(String secret, String email) {
		QrData data = new QrData.Builder()
				.label(ISSUER + ":" + email)
				.secret(secret)
				.issuer(ISSUER)
				.algorithm(HashingAlgorithm.SHA1)
				.build();
		return data.getUri();
	}

	public boolean verifyCode(String secret, String code) {
		return codeVerifier.isValidCode(secret, code);
	}
}
