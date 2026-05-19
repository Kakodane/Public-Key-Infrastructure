package com.ib.controller;


import com.ib.dto.ForgotPasswordRequest;
import com.ib.dto.PasswordResetChallengeResponse;
import com.ib.dto.ResetPasswordRequest;
import com.ib.service.registration.PasswordResetService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/password-reset")
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    @PostMapping("/request")
    public ResponseEntity<Void> requestReset(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{token}/challenge")
    public ResponseEntity<PasswordResetChallengeResponse> getChallenge(@PathVariable String token) {
        return ResponseEntity.ok(passwordResetService.getResetChallenge(token));
    }

    @PostMapping("/complete")
    public ResponseEntity<Void> complete(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }
}
