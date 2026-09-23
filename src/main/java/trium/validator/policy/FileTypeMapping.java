package trium.validator.policy;

import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

// @formatter:off
/// Defines the mapping between file extensions, MIME types, and formats
/// that represent the same file type.
///
/// Values are normalized by trimming whitespace and converting them to lowercase.
/// Null and blank entries are discarded, and the resulting sets are unmodifiable.
///
/// At least one normalized set must be non-empty.
// @formatter:on
public record FileTypeMapping(
        Set<String> extensions,
        Set<String> mimeTypes,
        Set<String> formats) {
    /// ~
    ///
    /// @throws NullPointerException     if any set is {@code null}
    /// @throws IllegalArgumentException if all normalized sets are empty
    public FileTypeMapping {
        extensions = normalize(extensions, "extensions");
        mimeTypes = normalize(mimeTypes, "mimeTypes");
        formats = normalize(formats, "formats");

        if (extensions.isEmpty() && mimeTypes.isEmpty() && formats.isEmpty()) {
            throw new IllegalArgumentException(
                    "at least one of extensions, mimeTypes, or formats must be configured");
        }
    }

    // @formatter:off
    /// @param values the values to normalize
    /// @param name the parameter name used in the null-check exception message
    /// @return an unmodifiable set containing the normalized values
    /// @throws NullPointerException if {@code values} is {@code null}
    // @formatter:on
    private static Set<String> normalize(Set<String> values, String name) {
        Objects.requireNonNull(values, name + " must not be null");

        Set<String> normalized = values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());

        return normalized;
    }

    /// Indicates whether this mapping subsumes the given mapping.
    ///
    /// A mapping subsumes another when all extensions, MIME types, and formats
    /// of the other mapping are also allowed by this mapping.
    ///
    /// @param that the mapping to test
    /// @return {@code true} if this mapping subsumes {@code that}
    /// @throws NullPointerException if {@code that} is {@code null}
    public boolean subsumes(FileTypeMapping that) {
        Objects.requireNonNull(that, "that must not be null");

        return extensions.containsAll(that.extensions)
                && mimeTypes.containsAll(that.mimeTypes)
                && formats.containsAll(that.formats);
    }

    // @formatter:off
    /// Attempts to fuse this mapping with the given mapping.
    ///
    /// Two mappings can be fused when two of their three attributes are equal.
    /// The remaining attribute is the union of both mappings' values for that attribute.
    ///
    /// @param that the mapping to fuse with this mapping
    /// @return the fused mapping, or {@link Optional#empty()} if the mappings cannot be fused
    /// @throws NullPointerException if {@code that} is {@code null}
    // @formatter:on
    public Optional<FileTypeMapping> fuse(FileTypeMapping that) {
        Objects.requireNonNull(that, "that must not be null");

        boolean sameExtensions = extensions.equals(that.extensions);
        boolean sameMimes = mimeTypes.equals(that.mimeTypes);
        boolean sameFormats = formats.equals(that.formats);

        if (sameExtensions && sameMimes && sameFormats) {
            return Optional.of(this);
        }

        if (sameMimes && sameFormats) {
            return Optional.of(new FileTypeMapping(
                    unionStrings(extensions, that.extensions),
                    mimeTypes,
                    formats));
        }

        if (sameExtensions && sameFormats) {
            return Optional.of(new FileTypeMapping(
                    extensions,
                    unionStrings(mimeTypes, that.mimeTypes),
                    formats));
        }

        if (sameExtensions && sameMimes) {
            return Optional.of(new FileTypeMapping(
                    extensions,
                    mimeTypes,
                    unionStrings(formats, that.formats)));
        }

        return Optional.empty();
    }

    private static Set<String> unionStrings(Set<String> a, Set<String> b) {
        Set<String> result = new HashSet<>(a);
        result.addAll(b);
        return Set.copyOf(result);
    }

    /// Returns the intersection of the given file type mappings.
    ///
    /// An attribute is considered conflicting only when both mappings constrain
    /// that attribute and their values have no intersection.
    /// Empty attributes therefore do not introduce a conflict.
    ///
    /// @param a the first
    /// @param b the second
    /// @return the intersected mapping,
    ///         or {@link Optional#empty()} if the mappings conflict
    /// @throws NullPointerException if {@code a} or {@code b} is {@code null}
    public static Optional<FileTypeMapping> intersect(FileTypeMapping a, FileTypeMapping b) {
        Objects.requireNonNull(a, "a must not be null");
        Objects.requireNonNull(b, "b must not be null");

        var extensions = intersectStrings(a.extensions(), b.extensions());
        if (!(a.extensions().isEmpty() || b.extensions().isEmpty()) && extensions.isEmpty()) {
            return Optional.empty();
        }

        var mimeTypes = intersectStrings(a.mimeTypes(), b.mimeTypes());
        if (!(a.mimeTypes().isEmpty() || b.mimeTypes().isEmpty()) && mimeTypes.isEmpty()) {
            return Optional.empty();
        }

        var formats = intersectStrings(a.formats(), b.formats());
        if (!(a.formats().isEmpty() || b.formats().isEmpty()) && formats.isEmpty()) {
            return Optional.empty();
        }

        if (extensions.isEmpty() && mimeTypes.isEmpty() && formats.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new FileTypeMapping(extensions, mimeTypes, formats));
    }

    private static Set<String> intersectStrings(Set<String> a, Set<String> b) {
        Set<String> result = new HashSet<>(a);
        result.retainAll(b);
        return Set.copyOf(result);
    }
}
