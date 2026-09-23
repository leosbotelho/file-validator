package trium.validator.policy;

import static trium.validator.policy.FileViolationException.Reason.MAX_SIZE_EXCEEDED;
import static trium.validator.policy.FileViolationException.Reason.MIN_SIZE_NOT_MET;

import java.util.Objects;
import java.util.Optional;

import trium.validator.FileCombinators;

// @formatter:off
/// Defines the file size security limits.
///
/// File size policies can be combined
/// using {@link #intersect}, {@link #span}, and {@link #exactlyOne}.
///
/// The combinations can also be guarded using {@link FileCombinators#guard}.
///
/// A file size policy is considered dummy when no file size restriction is enforced,
/// as indicated by {@link #isDummy()}.
// @formatter:on
public record FileSize(
        long min,
        long max) {
    /// Indicates that no maximum file size limit is enforced.
    public static final long MAX_UNLIMITED = Long.MAX_VALUE;

    /// Represents an unconstrained file size range.
    public static final FileSize DUMMY = new FileSize(0, MAX_UNLIMITED);

    /// Use {@link #of(long, long)} instead of this constructor for efficiency.
    ///
    /// @param min the minimum allowed file size
    /// @param max the maximum allowed file size
    /// @throws IllegalArgumentException if {@code min} is negative,
    ///                                  if {@code min} is {@link #MAX_UNLIMITED},
    ///                                  or if {@code max} is less than {@code min}
    public FileSize {
        if (min < 0) {
            throw new IllegalArgumentException("min must not be negative");
        }
        if (min == MAX_UNLIMITED) {
            throw new IllegalArgumentException("min must not be unlimited");
        }
        if (max < min) {
            throw new IllegalArgumentException("max must not be less than min");
        }
    }

    /// Use this method instead of the constructor for efficiency.
    ///
    /// @see #FileSize(long, long)
    /// @see #DUMMY
    public static FileSize of(long min, long max) {
        if (min == 0 && max == MAX_UNLIMITED) {
            return DUMMY;
        }
        return new FileSize(min, max);
    }

    // @formatter:off
    /// Indicates whether no file size restriction is enforced.
    ///
    /// @return {@code true} if the minimum size is zero and the maximum size is {@link #MAX_UNLIMITED}
    // @formatter:on
    public boolean isDummy() {
        return min == 0 && max == MAX_UNLIMITED;
    }

    // @formatter:off
    /// Validates the actual file size against the configured limits.
    ///
    /// @param size the file size to validate
    /// @throws FileViolationException if {@code size} is below the minimum allowed size
    ///                                ({@link FileViolationException.Reason#MIN_SIZE_NOT_MET}),
    ///                                or if {@code size} exceeds the maximum allowed size
    ///                                ({@link FileViolationException.Reason#MAX_SIZE_EXCEEDED})
    // @formatter:on
    public void validate(long size) {
        if (size < min) {
            throw new FileViolationException(MIN_SIZE_NOT_MET);
        }
        if (size > max) {
            throw new FileViolationException(MAX_SIZE_EXCEEDED);
        }
    }

    /// Intersects two file size policies by applying the stricter constraints.
    ///
    /// The resulting minimum is the greater of the two minimums,
    /// and the resulting maximum is the lesser of the two maximums.
    ///
    /// @param a the first
    /// @param b the second
    /// @return the intersection of the two file size policies,
    ///         or {@link Optional#empty()} if the policies do not overlap
    /// @throws NullPointerException if {@code a} or {@code b} is {@code null}
    public static Optional<FileSize> intersect(FileSize a, FileSize b) {
        Objects.requireNonNull(a, "a must not be null");
        Objects.requireNonNull(b, "b must not be null");

        long min = Math.max(a.min(), b.min());
        long max = Math.min(a.max(), b.max());

        if (min > max) {
            return Optional.empty();
        }

        return Optional.of(new FileSize(min, max));
    }

    /// Spans two file size policies by producing
    /// the smallest interval that contains both.
    ///
    /// The resulting minimum is the lesser of the two minimums,
    /// and the resulting maximum is the greater of the two maximums.
    ///
    /// @param a the first
    /// @param b the second
    /// @return the span of the two file size policies
    /// @throws NullPointerException if {@code a} or {@code b} is {@code null}
    /// @see #optionalOfSpan
    public static FileSize span(FileSize a, FileSize b) {
        Objects.requireNonNull(a, "a must not be null");
        Objects.requireNonNull(b, "b must not be null");

        return new FileSize(
                Math.min(a.min(), b.min()),
                Math.max(a.max(), b.max()));
    }

    /// ~
    ///
    /// @see #span
    public static Optional<FileSize> optionalOfSpan(FileSize a, FileSize b) {
        return Optional.of(span(a, b));
    }

    // @formatter:off
    /// Returns the non-dummy file size policy
    /// when exactly one of the policies is non-dummy.
    ///
    /// @param a the first
    /// @param b the second
    /// @return the non-dummy file size policy,
    ///         or {@link Optional#empty()} if both policies are dummy or both are non-dummy
    /// @throws NullPointerException if {@code a} or {@code b} is {@code null}
    // @formatter:on
    public static Optional<FileSize> exactlyOne(FileSize a, FileSize b) {
        Objects.requireNonNull(a, "a must not be null");
        Objects.requireNonNull(b, "b must not be null");

        if (a.isDummy() == b.isDummy()) {
            return Optional.empty();
        }

        return Optional.of(a.isDummy() ? b : a);
    }
}
