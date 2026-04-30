package com.ib.controller.user;

import com.ib.dto.auth.GetCertificateResponse;
import com.ib.service.user.RegularService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/regular")
@RequiredArgsConstructor
@PreAuthorize("hasRole('REGULAR_USER')")
public class RegularController {
    private final RegularService regularService;

    @GetMapping("/certificates")
    public ResponseEntity<List<GetCertificateResponse>> getCertificates(){
        List<GetCertificateResponse> certificateResponses= regularService.findAll();
        return ResponseEntity.ok(certificateResponses);
    }
}
