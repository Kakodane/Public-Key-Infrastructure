package com.ib.service.user;

import com.ib.domain.entity.Certificate;
import com.ib.domain.entity.User;
import com.ib.domain.repository.CertificateRepository;
import com.ib.domain.repository.UserRepository;
import com.ib.dto.auth.GetCertificateResponse;
import com.ib.security.AuthenticatedUser;
import lombok.AllArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
@Transactional
public class RegularService {

    private final CertificateRepository certificateRepository;
    private final UserRepository userRepository;

    public List<GetCertificateResponse> findAll(){
        Authentication authentication= SecurityContextHolder.getContext().getAuthentication();
        AuthenticatedUser authenticatedUser = (AuthenticatedUser)authentication.getPrincipal();
        Optional<User> user= userRepository.findByEmailIgnoreCase(authenticatedUser.getUsername());
        List<Certificate> certificates= certificateRepository.findAllByOwner_Id(user.get().getId());
        List<GetCertificateResponse> certificateResponses= new ArrayList<>();
        for(Certificate certificate: certificates){
            GetCertificateResponse response= new GetCertificateResponse(certificate);
            certificateResponses.add(response);
        }
        return certificateResponses;
    }
}
