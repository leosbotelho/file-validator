package trium.validator.policy;

import static trium.validator.policy.FileViolationException.Reason.INVALID_EXTENSION;
import static trium.validator.policy.FileViolationException.Reason.INVALID_FORMAT;
import static trium.validator.policy.FileViolationException.Reason.INVALID_MIME_TYPE;
import static trium.validator.policy.FileViolationException.Reason.UNALLOWED_EXTENSION;
import static trium.validator.policy.FileViolationException.Reason.UNALLOWED_FORMAT;
import static trium.validator.policy.FileViolationException.Reason.UNALLOWED_MIME_TYPE;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import trium.validator.FileCombinators;

// @formatter:off
/// Defines a file type validation policy composed of one or more {@link FileTypeMapping}s.
///
/// Each mapping associates allowed file extensions, MIME types, and formats.
/// Validation progressively restricts candidate mappings
/// as each available file attribute is checked.
///
/// File type policies can be combined
/// using {@link #union}, {@link #intersect}, and {@link #exactlyOne}.
///
/// The combinations can also be guarded using {@link FileCombinators#guard}.
///
// @formatter:on
public record FileType(
        Set<FileTypeMapping> mappings,
        boolean normalized) {
    // @formatter:off
    /// Creates a file type validation policy from the given {@link FileTypeMapping}s.
    ///
    /// Null mappings are ignored. At least one non-null mapping must be provided.
    ///
    /// If {@code normalized} is {@code true}, the resulting mappings are normalized
    /// (subsumed and fused), using {@link #normalize}.
    /// Otherwise, the provided mappings may contain 'redundancy'.
    ///
    /// @param mappings the set of allowed {@link FileTypeMapping}s
    /// @param normalized whether the resulting mappings should be normalized
    /// @throws NullPointerException if {@code mappings} is {@code null}
    /// @throws IllegalArgumentException if no non-null mappings are provided
    /// @see #FileType(Set<FileTypeMapping>)
    // @formatter:on
    public FileType {
        Objects.requireNonNull(mappings, "mappings must not be null");

        if (normalized) {
            mappings = normalize(mappings);
        } else {
            mappings = mappings.stream().filter(Objects::nonNull).collect(Collectors.toUnmodifiableSet());
        }

        if (mappings.isEmpty()) {
            throw new IllegalArgumentException("mappings must not be empty");
        }
    }

    // @formatter:off
    /// Creates a normalized (subsumed and fused) file type validation policy from the given
    /// {@link FileTypeMapping}s.
    ///
    /// @see #FileType(Set<FileTypeMapping>, boolean)
    // @formatter:on
    public FileType(Set<FileTypeMapping> mappings) {
        this(mappings, true);
    }

    // @formatter:off
    /// Progressively validates the supplied file attributes against the candidate mappings,
    /// enabling multi-step validation.
    ///
    /// Each supplied attribute narrows the set of candidate mappings.
    /// An empty {@link Optional<String>} indicates that the corresponding attribute is skipped
    /// at the current validation step.
    ///
    /// @param candidates the candidate mappings to validate
    /// @param extension the file extension
    /// @param mimeType the MIME type
    /// @param format the detected format
    /// @return the candidate mappings that remain after validation
    /// @throws NullPointerException if any argument is {@code null}
    /// @throws IllegalArgumentException if {@code candidates} is empty
    /// @throws FileViolationException if a supplied attribute is invalid
    ///                                or does not match any candidate mapping
    /// @see #validate(Optional, Optional, Optional)
    // @formatter:on
    public List<FileTypeMapping> validate(
            List<FileTypeMapping> candidates,
            Optional<String> extension,
            Optional<String> mimeType,
            Optional<String> format) {

        Objects.requireNonNull(candidates, "candidates must not be null");
        Objects.requireNonNull(extension, "extension must not be null");
        Objects.requireNonNull(mimeType, "mimeType must not be null");
        Objects.requireNonNull(format, "format must not be null");

        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("candidates must not be empty");
        }

        boolean constrained = false;

        if (extension.isPresent()) {
            String normalizedExtension = extension.get().trim().toLowerCase(Locale.ROOT);

            if (normalizedExtension.isEmpty()) {
                throw new FileViolationException(INVALID_EXTENSION);
            }

            candidates = candidates.stream()
                    .filter(xs -> xs.extensions().contains(normalizedExtension)).toList();

            if (candidates.isEmpty()) {
                throw new FileViolationException(UNALLOWED_EXTENSION);
            }

            constrained = true;
        }

        if (mimeType.isPresent()) {
            String value = mimeType.get();

            if (value.isBlank()) {
                throw new FileViolationException(INVALID_MIME_TYPE);
            }

            int semicolonIndex = value.indexOf(';');

            String cleanMime = (semicolonIndex >= 0
                    ? value.substring(0, semicolonIndex)
                    : value)
                    .trim()
                    .toLowerCase(Locale.ROOT);

            int slashIndex = cleanMime.indexOf('/');

            boolean hasNoSlash = slashIndex == -1;
            boolean slashAtStart = slashIndex == 0;
            boolean slashAtEnd = slashIndex == cleanMime.length() - 1;
            boolean hasMultipleSlashes = cleanMime.indexOf('/', slashIndex + 1) != -1;

            if (hasNoSlash || slashAtStart || slashAtEnd || hasMultipleSlashes) {
                throw new FileViolationException(INVALID_MIME_TYPE);
            }

            candidates = candidates.stream()
                    .filter(xs -> xs.mimeTypes().contains(cleanMime)).toList();

            if (candidates.isEmpty()) {
                throw new FileViolationException(UNALLOWED_MIME_TYPE, constrained);
            }

            constrained = true;
        }

        if (format.isPresent()) {
            String normalizedFormat = format.get().trim().toLowerCase(Locale.ROOT);

            if (normalizedFormat.isEmpty()) {
                throw new FileViolationException(INVALID_FORMAT);
            }

            candidates = candidates.stream()
                    .filter(xs -> xs.formats().contains(normalizedFormat)).toList();

            if (candidates.isEmpty()) {
                throw new FileViolationException(UNALLOWED_FORMAT, constrained);
            }
        }

        return candidates;
    }

    // @formatter:off
    /// Validates the supplied file attributes using all configured mappings
    /// as candidates.
    ///
    /// @see #validate(List, Optional, Optional, Optional)
    // @formatter:on
    public List<FileTypeMapping> validate(
            Optional<String> extension,
            Optional<String> mimeType,
            Optional<String> format) {
        return validate(mappings.stream().toList(), extension, mimeType, format);
    }

    // @formatter:off
    /// Extracts the file extension from the given file name.
    ///
    /// @param filename the file name
    /// @return an {@link Optional} containing the file extension without the leading dot,
    ///         or {@link Optional#empty()} if the file name is blank or has no extension;
    ///         or an {@link Optional} containing an empty string if the file name ends with a dot
    /// @throws NullPointerException if {@code filename} is {@code null}
    // @formatter:on
    public static Optional<String> extractExtension(String filename) {
        Objects.requireNonNull(filename, "filename must not be null");

        if (filename.isBlank()) {
            return Optional.empty();
        }

        int dotIndex = filename.lastIndexOf('.');

        if (dotIndex <= 0) {
            return Optional.empty();
        }

        if (dotIndex == filename.length() - 1) {
            return Optional.of("");
        }

        return Optional.of(filename.substring(dotIndex + 1));
    }

    /// Normalizes the given file type mappings by removing redundant mappings
    /// and applying distributive fusion until no further changes can be made.
    ///
    /// Null mappings are ignored. The returned set is unmodifiable.
    ///
    /// @param mappings the mappings to normalize
    /// @return an unmodifiable set containing the normalized mappings
    /// @throws NullPointerException if {@code mappings} is {@code null}
    public static Set<FileTypeMapping> normalize(Set<FileTypeMapping> mappings) {
        List<FileTypeMapping> list = mappings.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));

        boolean changed;
        do {
            // evaluate both passes explicitly to avoid short-circuit.
            boolean subsumed = subsumePass(list);
            boolean fused = fusionPass(list);

            changed = subsumed || fused;
        } while (changed);

        return Set.copyOf(list);
    }

    // @formatter:off
    /// Performs a single pass of subsumption over the given list of mappings.
    ///
    /// Mappings subsumed by another mapping (according to {@link FileTypeMapping#subsumes})
    /// are removed from the list. The list is modified in place.
    ///
    /// @param list the mappings to process
    /// @return {@code true} if at least one mapping was removed
    // @formatter:on
    private static boolean subsumePass(List<FileTypeMapping> list) {
        var removedAny = false;

        for (int i = 0; i < list.size(); i++) {
            var candidate = list.get(i);

            for (int j = 0; j < list.size(); j++) {
                if (i != j && list.get(j).subsumes(candidate)) {
                    list.remove(i);
                    i--;
                    removedAny = true;
                    break;
                }
            }
        }

        return removedAny;
    }

    // @formatter:off
    /// Performs a single pass of distributive fusion over the given list of mappings.
    ///
    /// At most one pair is fused (via {@link FileTypeMapping#fuse}) per pass.
    /// The list is modified in place and the pass stops after the first successful fusion.
    ///
    /// @param list list the mappings to process
    /// @return {@code true} if a pair was fused
    // @formatter:on
    private static boolean fusionPass(List<FileTypeMapping> list) {
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                var fused = list.get(i).fuse(list.get(j));

                if (fused.isPresent()) {
                    list.set(i, fused.get());
                    list.remove(j);
                    return true;
                }
            }
        }

        return false;
    }

    /// Returns the union of the given file type validation policies.
    ///
    /// The resulting policy accepts any file type accepted by either policy.
    /// The {@code normalized} parameter determines whether the resulting mappings
    /// are normalized.
    ///
    /// @param normalized whether the resulting mappings should be normalized
    /// @param a          the first
    /// @param b          the second
    /// @return the union of {@code a} and {@code b}
    /// @throws NullPointerException if {@code a} or {@code b} is {@code null}
    /// @see #union(FileType, FileType)
    /// @see #union(boolean, Optional, Optional)
    public static FileType union(boolean normalized, FileType a, FileType b) {
        Objects.requireNonNull(a, "a must not be null");
        Objects.requireNonNull(b, "b must not be null");

        Set<FileTypeMapping> mappings = new HashSet<>(a.mappings());
        mappings.addAll(b.mappings());

        return new FileType(mappings, normalized);
    }

    /// Returns the normalized union of the given file type validation policies.
    ///
    /// @see #union(Optional, Optional)
    /// @see #union(boolean, FileType, FileType)
    public static FileType union(FileType a, FileType b) {
        return union(true, a, b);
    }

    /// ~
    ///
    /// @see #union(Optional, Optional)
    /// @see #union(boolean, FileType, FileType)
    public static Optional<FileType> union(
            boolean normalized,
            Optional<FileType> a,
            Optional<FileType> b) {
        Objects.requireNonNull(a, "a must not be null");
        Objects.requireNonNull(b, "b must not be null");

        if (a.isEmpty()) {
            return b;
        }
        if (b.isEmpty()) {
            return a;
        }

        return Optional.of(union(normalized, a.get(), b.get()));
    }

    /// Returns the normalized union of the given file type validation policies.
    ///
    /// @see #union(boolean, Optional, Optional)
    public static Optional<FileType> union(
            Optional<FileType> a,
            Optional<FileType> b) {
        return union(true, a, b);
    }

    // @formatter:off
    /// Returns the normalized intersection of the given file type validation policies.
    ///
    /// The resulting policy contains only mappings accepted by both policies, derived by computing
    /// the pairwise intersection via {@link FileTypeMapping#intersect}.
    ///
    /// @param a the first
    /// @param b the second
    /// @return an {@link Optional} containing the normalized intersection of {@code a} and {@code b},
    ///         or {@link Optional#empty()} if the policies do not overlap
    /// @throws NullPointerException if {@code a} or {@code b} is {@code null}
    /// @see #intersect(Optional, Optional)
    // @formatter:on
    public static Optional<FileType> intersect(FileType a, FileType b) {
        Objects.requireNonNull(a, "a must not be null");
        Objects.requireNonNull(b, "b must not be null");

        var xs = a.normalized() ? a.mappings() : normalize(a.mappings());
        var ys = b.normalized() ? b.mappings() : normalize(b.mappings());

        var mappings = new HashSet<FileTypeMapping>();

        for (FileTypeMapping x : xs) {
            for (FileTypeMapping y : ys) {
                FileTypeMapping.intersect(x, y).ifPresent(mappings::add);
            }
        }

        if (mappings.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new FileType(mappings, true));
    }

    /// ~
    ///
    /// @see #intersect(FileType, FileType)
    public static Optional<FileType> intersect(
            Optional<FileType> a,
            Optional<FileType> b) {
        Objects.requireNonNull(a, "a must not be null");
        Objects.requireNonNull(b, "b must not be null");

        if (a.isEmpty() || b.isEmpty()) {
            return Optional.empty();
        }

        return intersect(a.get(), b.get());
    }

    /// Returns the present file type policy if and only if exactly one of the given
    /// optional policies is present (exclusive or).
    ///
    /// @throws NullPointerException if {@code a} or {@code b} is {@code null}
    public static Optional<FileType> exactlyOne(
            Optional<FileType> a,
            Optional<FileType> b) {
        Objects.requireNonNull(a, "a must not be null");
        Objects.requireNonNull(b, "b must not be null");

        if (a.isPresent() == b.isPresent()) {
            return Optional.empty();
        }

        return a.isPresent() ? a : b;
    }
}
