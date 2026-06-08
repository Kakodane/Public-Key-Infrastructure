package com.ib.controller;

import com.ib.dto.auth.GetCertificateResponse;
import com.ib.dto.certificate.*;
import com.ib.service.certificate.CertificateService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    @PreAuthorize("hasAnyRole('ADMIN', 'CA_USER', 'REGULAR_USER')")
    @PostMapping("/{id}/revoke")
    public ResponseEntity<GetCertificateResponse> revokeCertificate(
            @PathVariable Long id,
            @Valid @RequestBody RevokeCertificateRequest request
    ) {
        return ResponseEntity.ok(certificateService.revoke(id, request));
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'CA_USER', 'REGULAR_USER')")
	@GetMapping("/{id}/download")
	public ResponseEntity<byte[]> downloadCertificate(
			@PathVariable Long id,
			@RequestParam String format
	) {
		return toDownloadResponse(certificateService.downloadCertificate(id, format));
	}

	@PreAuthorize("hasAnyRole('ADMIN', 'CA_USER', 'REGULAR_USER')")
	@PostMapping("/{id}/download")
	public ResponseEntity<byte[]> downloadCertificateWithBody(
			@PathVariable Long id,
			@Valid @RequestBody DownloadCertificateRequest request
	) {
		return toDownloadResponse(certificateService.downloadCertificate(id, request));
	}

	private static ResponseEntity<byte[]> toDownloadResponse(CertificateDownload download) {
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + download.filename() + "\"")
				.contentType(MediaType.parseMediaType(download.contentType()))
				.body(download.data());
	}
}
