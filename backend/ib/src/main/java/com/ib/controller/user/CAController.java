package com.ib.controller.user;

import com.ib.dto.auth.GetCertificateResponse;
import com.ib.service.user.CAService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/ca")
@RequiredArgsConstructor
public class CAController {
    private final CAService caService;

    @GetMapping("/certificates")
    public ResponseEntity<List<GetCertificateResponse>> getCertificates(){
        List<GetCertificateResponse> certificateResponses= caService.findAll();
        return ResponseEntity.ok(certificateResponses);
    }
}
