package com.example.security.controller;

import com.example.security.dto.MfaEnableResponse;
import com.example.security.dto.MfaSetupResponse;
import com.example.security.dto.MfaStatusResponse;
import com.example.security.dto.MfaVerifyRequest;
import com.example.security.service.MfaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/mfa")
public class MfaController {
    private static final String PENDING_SECRET = "MFA_SETUP_SECRET";
    private static final String PENDING_SECRET_CREATED = "MFA_SETUP_SECRET_CREATED";
    private static final long SETUP_LIFETIME_SECONDS = 10 * 60;

    private final MfaService mfaService;

    public MfaController(MfaService mfaService) {
        this.mfaService = mfaService;
    }

    @GetMapping("/status")
    public MfaStatusResponse status(Authentication authentication) {
        return new MfaStatusResponse(mfaService.isEnabled(authentication.getName()));
    }

    @PostMapping("/setup")
    public MfaSetupResponse setup(Authentication authentication, HttpServletRequest request) {
        String secret = mfaService.newSecret();
        HttpSession session = request.getSession(true);
        session.setAttribute(PENDING_SECRET, secret);
        session.setAttribute(PENDING_SECRET_CREATED, Instant.now().getEpochSecond());
        return mfaService.setupResponse(authentication.getName(), secret);
    }

    @PostMapping("/enable")
    public MfaEnableResponse enable(
            Authentication authentication,
            @Valid @RequestBody MfaVerifyRequest request,
            HttpServletRequest httpRequest
    ) {
        HttpSession session = httpRequest.getSession(false);
        if (session == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MFA setup has expired");

        Object secretValue = session.getAttribute(PENDING_SECRET);
        Object createdValue = session.getAttribute(PENDING_SECRET_CREATED);
        if (!(secretValue instanceof String secret) || !(createdValue instanceof Long created)
                || Instant.now().getEpochSecond() - created > SETUP_LIFETIME_SECONDS) {
            clearSetup(session);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MFA setup has expired. Start setup again.");
        }

        if (!mfaService.verifySecret(secret, request.code())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "The Authy code is not valid");
        }

        List<String> recoveryCodes = mfaService.enable(authentication.getName(), secret);
        clearSetup(session);
        return new MfaEnableResponse(true, recoveryCodes);
    }

    @PostMapping("/disable")
    public MfaStatusResponse disable(Authentication authentication, @Valid @RequestBody MfaVerifyRequest request) {
        if (!mfaService.disable(authentication.getName(), request.code())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "The Authy/recovery code is not valid");
        }
        return new MfaStatusResponse(false);
    }

    private void clearSetup(HttpSession session) {
        session.removeAttribute(PENDING_SECRET);
        session.removeAttribute(PENDING_SECRET_CREATED);
    }
}
