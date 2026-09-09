package com.example.security.security;

import com.example.security.model.AppUser;
import com.example.security.service.GoogleAccountService;
import com.example.security.service.MfaService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.Instant;

@Component
public class GoogleOAuthSuccessHandler implements AuthenticationSuccessHandler {
    public static final String LINK_USERNAME = "GOOGLE_LINK_USERNAME";
    public static final String LINK_CREATED = "GOOGLE_LINK_CREATED";
    private static final long LINK_LIFETIME_SECONDS = 5 * 60;

    private final GoogleAccountService googleAccounts;
    private final UserDetailsService userDetailsService;
    private final MfaService mfaService;
    private final LoginCompletionService loginCompletionService;
    private final SecurityAuditService auditService;
    private final String frontendBaseUrl;

    public GoogleOAuthSuccessHandler(GoogleAccountService googleAccounts,
                                     UserDetailsService userDetailsService,
                                     MfaService mfaService,
                                     LoginCompletionService loginCompletionService,
                                     SecurityAuditService auditService,
                                     @Value("${app.frontend-base-url}") String frontendBaseUrl) {
        this.googleAccounts = googleAccounts;
        this.userDetailsService = userDetailsService;
        this.mfaService = mfaService;
        this.loginCompletionService = loginCompletionService;
        this.auditService = auditService;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        if (!(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
            fail(request, response, null, "invalid_google_identity");
            return;
        }

        String subject = oidcUser.getSubject();
        String email = oidcUser.getEmail();
        boolean emailVerified = Boolean.TRUE.equals(oidcUser.getEmailVerified());
        HttpSession session = request.getSession(false);

        String linkUsername = validLinkUsername(session);
        clearLinkRequest(session);
        if (linkUsername != null) {
            try {
                AppUser linked = googleAccounts.link(linkUsername, subject, email, emailVerified);
                Authentication localAuthentication = localAuthentication(linked.getUsername());
                loginCompletionService.complete(localAuthentication, request, response, "google_account_linked");
                auditService.record("GOOGLE_LINK_SUCCESS", linked.getUsername(), linked.getUsername(), true,
                        "verified_email_match", request);
                redirect(response, "linked");
            } catch (IllegalArgumentException ex) {
                fail(request, response, linkUsername, "link_rejected");
            }
            return;
        }

        AppUser user = googleAccounts.findLinkedUser(subject).orElse(null);
        if (user == null) {
            fail(request, response, email, "not_linked");
            return;
        }

        Authentication localAuthentication = localAuthentication(user.getUsername());
        if (mfaService.isEnabled(user.getUsername())) {
            loginCompletionService.beginMfaChallenge(localAuthentication, request, response, "google_verified");
            redirect(response, "mfa");
            return;
        }

        loginCompletionService.complete(localAuthentication, request, response, "google");
        redirect(response, "success");
    }

    private Authentication localAuthentication(String username) {
        UserDetails details = userDetailsService.loadUserByUsername(username);
        return UsernamePasswordAuthenticationToken.authenticated(details, null, details.getAuthorities());
    }

    private String validLinkUsername(HttpSession session) {
        if (session == null) return null;
        Object username = session.getAttribute(LINK_USERNAME);
        Object created = session.getAttribute(LINK_CREATED);
        if (!(username instanceof String value) || !(created instanceof Long timestamp)) return null;
        return Instant.now().getEpochSecond() - timestamp <= LINK_LIFETIME_SECONDS ? value : null;
    }

    private void clearLinkRequest(HttpSession session) {
        if (session == null) return;
        session.removeAttribute(LINK_USERNAME);
        session.removeAttribute(LINK_CREATED);
    }

    private void fail(HttpServletRequest request, HttpServletResponse response, String actor, String reason)
            throws IOException {
        loginCompletionService.clearAuthentication(request, response);
        auditService.record("GOOGLE_LOGIN_FAILURE", actor, actor, false, reason, request);
        redirect(response, reason);
    }

    private void redirect(HttpServletResponse response, String result) throws IOException {
        String target = UriComponentsBuilder.fromUriString(frontendBaseUrl)
                .queryParam("google", result)
                .build().encode().toUriString();
        response.sendRedirect(target);
    }
}
