package com.ib.controller.user;

import com.ib.dto.auth.GetCertificateResponse;
import com.ib.dto.user.SetPasswordRequest;
import com.ib.service.user.CAService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ca")
@RequiredArgsConstructor
public class CAController {
    private final CAService caService;

    @PreAuthorize("hasRole('CA_USER')")
    @GetMapping("/certificates")
    public ResponseEntity<List<GetCertificateResponse>> getCertificates() {
        return ResponseEntity.ok(caService.findAll());
    }

    @PutMapping("/password")
    public ResponseEntity<Void> setPassword(@Valid @RequestBody SetPasswordRequest request) {
        caService.setPassword(request);
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
