package com.ib.service.certificate;

public class CertificateIssuanceException extends RuntimeException {

	public CertificateIssuanceException(String message) {
		super(message);
	}

	public CertificateIssuanceException(String message, Throwable cause) {
		super(message, cause);
	}

}
