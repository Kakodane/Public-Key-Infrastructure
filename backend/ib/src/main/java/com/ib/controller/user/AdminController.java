package com.ib.controller.user;

import com.ib.dto.auth.GetCertificateResponse;
import com.ib.dto.user.*;
import com.ib.service.user.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    private final AdminService adminService;

    @GetMapping("/certificates")
    public ResponseEntity<List<GetCertificateResponse>> getCertificates() {
        return ResponseEntity.ok(adminService.findAll());
    }

    @PostMapping("/ca-user")
    public ResponseEntity<CreateCAUserResponse> create(@Valid @RequestBody CreateCAUserRequest request) {
        CreateCAUserResponse response = adminService.createCAUser(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/organization")
    public ResponseEntity<CreateOrganizationResponse> createOrganization(@Valid @RequestBody CreateOrganizationRequest request) {
        CreateOrganizationResponse response = adminService.createOrganization(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
    @GetMapping("/organizations")
    public ResponseEntity<List<GetOrgsResponse>> getOrgs() {
        return ResponseEntity.ok(adminService.getOrgs());
    }
}
