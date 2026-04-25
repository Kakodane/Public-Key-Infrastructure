package com.ib.controller;

import com.ib.dto.auth.GetCertificateResponse;
import com.ib.dto.certificate.CreateEndEntityCertificateRequest;
import com.ib.dto.certificate.CreateIntermediateCertificateRequest;
import com.ib.dto.certificate.CreateRootCertificateRequest;
import com.ib.service.certificate.CertificateService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/certificates")
public class CertificateController {

	private final CertificateService certificateService;

	public CertificateController(CertificateService certificateService) {
		this.certificateService = certificateService;
	}

	@PreAuthorize("hasRole('ADMIN')")
	@PostMapping("/root")
	public ResponseEntity<GetCertificateResponse> createRootCertificate(
			@Valid @RequestBody CreateRootCertificateRequest request
	) {
		return ResponseEntity.ok(certificateService.issueRoot(request));
	}

	@PreAuthorize("hasAnyRole('ADMIN', 'CA_USER')")
	@PostMapping("/intermediate")
	public ResponseEntity<GetCertificateResponse> createIntermediateCertificate(
			@Valid @RequestBody CreateIntermediateCertificateRequest request
	) {
		return ResponseEntity.ok(certificateService.issueIntermediate(request));
	}


	@PreAuthorize("hasAnyRole('ADMIN', 'CA_USER', 'REGULAR_USER')")
	@PostMapping("/end-entity")
	public ResponseEntity<GetCertificateResponse> createEndEntityCertificate(
			@Valid @RequestBody CreateEndEntityCertificateRequest request
	) {
		return ResponseEntity.ok(certificateService.issueEndEntity(request));
	}
}
