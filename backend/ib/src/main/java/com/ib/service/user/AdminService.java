package com.ib.service.user;

import com.ib.domain.entity.Certificate;
import com.ib.domain.repository.CertificateRepository;
import com.ib.dto.auth.GetCertificateResponse;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
@Transactional
public class AdminService {
    private final CertificateRepository certificateRepository;

    public List<GetCertificateResponse> findAll(){
        List<Certificate> certificates= certificateRepository.findAll();
        List<GetCertificateResponse> certificateResponses= new ArrayList<>();
        for(Certificate certificate: certificates){
            GetCertificateResponse response= new GetCertificateResponse(certificate);
            certificateResponses.add(response);
        }
        return certificateResponses;
    }
}
