package com.swiftdrop.auth.security;

public interface GoogleTokenVerifier {

    /**
     * Cryptographically validates the provided Google ID token.
     * Verifies signature, expiration, audience (client ID), issuer, and email verification status.
     *
     * @param idToken raw Google JWT ID token string
     * @return validated user claims
     * @throws com.swiftdrop.auth.exception.GoogleTokenValidationException if validation fails
     */
    GoogleUserClaims verify(String idToken);
}
