package com.ib.controller;

import com.ib.service.certificate.CrlService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/crl")
@RequiredArgsConstructor
public class CrlController {

    private final CrlService crlService;

    @GetMapping("/{caId}.crl")
    public ResponseEntity<byte[]> getCrl(@PathVariable Long caId) {
        byte[] der = crlService.serveCrl(caId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + caId + ".crl\"")
                .contentType(MediaType.parseMediaType("application/pkix-crl"))
                .body(der);
    }
}
