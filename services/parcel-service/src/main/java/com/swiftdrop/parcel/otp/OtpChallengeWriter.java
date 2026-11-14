package com.swiftdrop.parcel.otp;

import com.swiftdrop.parcel.entity.ParcelDeliveryOtpChallenge;
import com.swiftdrop.parcel.exception.CourierAssignmentMismatchException;
import com.swiftdrop.parcel.exception.ParcelNotFoundException;
import com.swiftdrop.parcel.exception.ParcelStatusConflictException;
import com.swiftdrop.parcel.repository.ParcelAssignmentRepository;
import com.swiftdrop.parcel.repository.ParcelDeliveryOtpChallengeRepository;
import com.swiftdrop.parcel.repository.ParcelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class OtpChallengeWriter {
    static final Duration OTP_TTL = Duration.ofMinutes(5);
    static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final short MAX_ATTEMPTS = 5;

    private final ParcelRepository parcelRepository;
    private final ParcelAssignmentRepository assignmentRepository;
    private final ParcelDeliveryOtpChallengeRepository challengeRepository;
    private final OtpCodeCryptography cryptography;
    private final Clock clock;

    public OtpChallengeWriter(ParcelRepository parcelRepository,
                              ParcelAssignmentRepository assignmentRepository,
                              ParcelDeliveryOtpChallengeRepository challengeRepository,
                              OtpCodeCryptography cryptography,
                              Clock clock) {
        this.parcelRepository = parcelRepository;
        this.assignmentRepository = assignmentRepository;
        this.challengeRepository = challengeRepository;
        this.cryptography = cryptography;
        this.clock = clock;
    }

    @Transactional
    PreparedOtpDelivery prepare(String parcelId, String trustedCourierId) {
        var parcel = parcelRepository.findByIdForUpdate(parcelId)
                .orElseThrow(() -> new ParcelNotFoundException(parcelId));
        var assignment = assignmentRepository.findActiveByParcelIdForUpdate(parcelId)
                .orElseThrow(() -> new ParcelStatusConflictException(
                        "Parcel has no current active courier assignment"));
        requireAssignedCourier(trustedCourierId, assignment.getCourierId());
        if (!assignment.getCourierId().equals(parcel.getCourierId())) {
            throw new ParcelStatusConflictException("Parcel courier and active assignment are inconsistent");
        }
        if (parcel.getRecipientPhone() == null || parcel.getRecipientPhone().isBlank()) {
            throw new ParcelStatusConflictException("Parcel has no usable recipient phone number");
        }

        Instant now = clock.instant();
        var latest = challengeRepository
                .findFirstByParcelIdAndAssignmentIdOrderByCreatedAtDescIdDesc(parcelId, assignment.getId());
        if (latest.isPresent() && latest.get().getCreatedAt().plus(RESEND_COOLDOWN).isAfter(now)) {
            throw new OtpChallengeCooldownException();
        }

        // Lock and invalidate outstanding challenges/proofs before inserting a replacement.
        var previousChallenges = challengeRepository.findUsableByParcelIdForUpdate(parcelId);
        previousChallenges.forEach(challenge -> challenge.invalidateAt(now));
        if (!previousChallenges.isEmpty()) {
            // Clear the partial unique index slot before inserting the replacement.
            challengeRepository.saveAllAndFlush(previousChallenges);
        }
        UUID challengeId = UUID.randomUUID();
        Instant expiresAt = now.plus(OTP_TTL);
        char[] code = cryptography.generateCode();
        boolean handedOff = false;
        byte[] codeHash = null;
        try {
            codeHash = cryptography.hash(challengeId, parcelId, assignment.getId(), trustedCourierId, code);
            challengeRepository.saveAndFlush(new ParcelDeliveryOtpChallenge(challengeId, parcelId,
                    assignment.getId(), trustedCourierId, codeHash, now, expiresAt));
            PreparedOtpDelivery prepared = new PreparedOtpDelivery(new OtpChallengeReceipt(challengeId, expiresAt,
                    now.plus(RESEND_COOLDOWN), maskPhone(parcel.getRecipientPhone())),
                    parcelId, parcel.getRecipientPhone(), code);
            handedOff = true;
            return prepared;
        } finally {
            if (codeHash != null) {
                java.util.Arrays.fill(codeHash, (byte) 0);
            }
            if (!handedOff) {
                java.util.Arrays.fill(code, '\0');
            }
        }
    }

    @Transactional
    OtpVerificationResult verify(String parcelId, UUID challengeId, String trustedCourierId,
                                 char[] candidateCode) {
        var parcel = parcelRepository.findByIdForUpdate(parcelId)
                .orElseThrow(() -> new ParcelNotFoundException(parcelId));
        var assignment = assignmentRepository.findActiveByParcelIdForUpdate(parcelId)
                .orElseThrow(() -> new ParcelStatusConflictException(
                        "Parcel has no current active courier assignment"));
        requireAssignedCourier(trustedCourierId, assignment.getCourierId());
        if (!assignment.getCourierId().equals(parcel.getCourierId())) {
            throw new ParcelStatusConflictException("Parcel courier and active assignment are inconsistent");
        }

        var found = challengeRepository.findByIdAndParcelIdForUpdate(challengeId, parcelId);
        if (found.isEmpty()) {
            return new OtpVerificationResult(challengeId, OtpVerificationOutcome.INVALID, null);
        }
        ParcelDeliveryOtpChallenge challenge = found.get();
        if (!challenge.getAssignmentId().equals(assignment.getId())
                || !challenge.getCourierId().equals(trustedCourierId)) {
            challenge.invalidateAt(clock.instant());
            challengeRepository.saveAndFlush(challenge);
            return new OtpVerificationResult(challengeId, OtpVerificationOutcome.INVALID, null);
        }
        if (!challenge.isUsable() || challenge.getOtpConsumedAt() != null) {
            return new OtpVerificationResult(challengeId, OtpVerificationOutcome.INVALID, null);
        }

        Instant now = clock.instant();
        if (!now.isBefore(challenge.getExpiresAt())) {
            challenge.invalidateAt(now);
            challengeRepository.saveAndFlush(challenge);
            return new OtpVerificationResult(challengeId, OtpVerificationOutcome.EXPIRED, null);
        }

        byte[] expectedHash = challenge.codeHmacForVerification();
        boolean matches;
        try {
            matches = cryptography.matches(expectedHash, challenge.getId(), challenge.getParcelId(),
                    challenge.getAssignmentId(), challenge.getCourierId(), candidateCode);
        } finally {
            java.util.Arrays.fill(expectedHash, (byte) 0);
        }
        if (!matches) {
            challenge.recordIncorrectAttempt(now);
            challengeRepository.saveAndFlush(challenge);
            OtpVerificationOutcome outcome = challenge.getAttemptCount() >= MAX_ATTEMPTS
                    ? OtpVerificationOutcome.EXHAUSTED : OtpVerificationOutcome.INVALID;
            return new OtpVerificationResult(challengeId, outcome, null);
        }

        challenge.verifyAndConsumeOtp(now);
        challengeRepository.saveAndFlush(challenge);
        return new OtpVerificationResult(challengeId, OtpVerificationOutcome.VERIFIED, now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void invalidateAfterDeliveryFailure(String parcelId, UUID challengeId) {
        parcelRepository.findByIdForUpdate(parcelId)
                .orElseThrow(() -> new ParcelNotFoundException(parcelId));
        assignmentRepository.findActiveByParcelIdForUpdate(parcelId);
        challengeRepository.findByIdAndParcelIdForUpdate(challengeId, parcelId)
                .ifPresent(challenge -> {
                    challenge.invalidateAt(clock.instant());
                    challengeRepository.saveAndFlush(challenge);
                });
    }

    private void requireAssignedCourier(String courierId, String activeCourierId) {
        if (courierId == null || courierId.isBlank()) {
            throw new IllegalArgumentException("A trusted courier identity is required");
        }
        if (!courierId.equals(activeCourierId)) {
            throw new CourierAssignmentMismatchException();
        }
    }

    private String maskPhone(String phone) {
        String digits = phone.replaceAll("\\D", "");
        int visible = Math.min(4, digits.length());
        return "*".repeat(Math.max(4, digits.length() - visible)) + digits.substring(digits.length() - visible);
    }
}
