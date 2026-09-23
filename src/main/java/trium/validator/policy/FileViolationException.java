package trium.validator.policy;

import java.util.Objects;

// @formatter:off
/// Exception thrown when a file fails one or more security policy validation checks.
///
/// Carries a specific {@link Reason} detailing the exact nature of the policy violation,
/// together with whether the violation occurred under previously constrained validation criteria.
// @formatter:on
public class FileViolationException extends RuntimeException {
    public enum Reason {
        // --- Size ---
        MIN_SIZE_NOT_MET,
        MAX_SIZE_EXCEEDED,

        // --- MIME Type ---
        INVALID_MIME_TYPE,
        UNALLOWED_MIME_TYPE,

        // --- Extension --
        INVALID_EXTENSION,
        UNALLOWED_EXTENSION,

        // --- Format --
        INVALID_FORMAT,
        UNALLOWED_FORMAT
    }

    private final Reason reason;
    private final boolean constrained;

    public FileViolationException(Reason reason, boolean constrained) {
        this.reason = Objects.requireNonNull(reason, "reason must not be null");
        this.constrained = constrained;
    }

    public FileViolationException(Reason reason) {
        this(reason, false);
    }

    public Reason getReason() {
        return reason;
    }

    public boolean isConstrained() {
        return constrained;
    }
}
