package com.ib.controller;


import com.ib.dto.auth.ActivateAccountRequest;
import com.ib.dto.auth.ActivationChallengeResponse;
import com.ib.dto.auth.RegisterRequest;
import com.ib.service.registration.RegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/registration")
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationService registrationService;

    @PostMapping
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        boolean response = registrationService.register(request);
        if(response) return new ResponseEntity<>( HttpStatus.CREATED);
        else return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }

    @GetMapping("/{token}/challenge")
    public ResponseEntity<ActivationChallengeResponse> getChallenge(@PathVariable String token) {
        return ResponseEntity.ok(registrationService.getActivationChallenge(token));
    }

    @PostMapping("/activate")
    public ResponseEntity<Void> activate(@Valid @RequestBody ActivateAccountRequest request) {
        registrationService.activate(request);
        return ResponseEntity.noContent().build();
    }
}