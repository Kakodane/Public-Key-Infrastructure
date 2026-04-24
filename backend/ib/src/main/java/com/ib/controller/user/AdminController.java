package com.ib.controller.user;

import com.ib.dto.auth.GetCertificateResponse;
import com.ib.service.user.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {
    private final AdminService adminService;
    @GetMapping("/certificates")
    public ResponseEntity<List<GetCertificateResponse>> getCertificates(){
            List<GetCertificateResponse> certificateResponses= adminService.findAll();
            return ResponseEntity.ok(certificateResponses);
    }
}
