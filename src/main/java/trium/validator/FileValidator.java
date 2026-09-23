package trium.validator;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;

import trium.validator.policy.FileSize;
import trium.validator.policy.FileType;
import trium.validator.policy.FileTypeMapping;

// @formatter:off
/// Validates files against configured file size and file type policies.
///
/// At least one validation policy must be defined.
///
/// Validation can be performed in two stages: file size and metadata can be validated first
/// using {@link #validateFileSizeAndMetadata},
/// followed by file format using {@link #validateFormat}.
///
/// Strict validation requires all relevant inputs and validation policies to be present and effective.
///
/// Lax validation allows inputs and policies to be omitted
/// when they are not required for the requested validation.
///
/// The validator may be combined with another validator using
/// {@link #exactlyOneOf(FileValidator, FileValidator)},
/// {@link #intersect(boolean, FileValidator, FileValidator)},
/// {@link #relax(boolean, FileValidator, FileValidator)},
/// or, more generally, {@link #combine}.
///
/// File size policy may be dummy,
/// indicating that file size validation is not enforced.
/// See {@link FileSize#isDummy()}.
///
/// The file type policy may be absent,
/// indicating that file type validation is not enforced.
// @formatter:on
public record FileValidator(
        FileSize fileSize,
        Optional<FileType> fileType) {
    /// ~
    ///
    /// @throws NullPointerException         if {@code fileSize} or {@code fileType}
    ///                                      is {@code null}
    /// @throws NoFilePolicyDefinedException if no file validation policies
    ///                                      are defined
    public FileValidator {
        Objects.requireNonNull(fileSize, "fileSize must not be null");
        Objects.requireNonNull(fileType, "fileType must not be null");

        if (fileSize.isDummy() && fileType.isEmpty()) {
            throw new NoFilePolicyDefinedException("at least one validation policy must be defined");
        }
    }

    public FileValidator(FileSize fileSize, FileType fileType) {
        this(fileSize, Optional.of(fileType));
    }

    public FileValidator(FileSize fileSize) {
        this(fileSize, Optional.empty());
    }

    public FileValidator(FileType fileType) {
        this(FileSize.DUMMY, fileType);
    }

    // @formatter:off
    /// Combines two {@link FileValidator}s
    /// using the provided combiners for file size and file type.
    ///
    /// In strict mode, both combinations must succeed and produce effective policies.
    /// Dummy policies are treated as absent.
    ///
    /// In non-strict mode, the resulting configuration may contain either combination,
    /// with {@link FileSize#DUMMY} used when the file size combination fails.
    ///
    /// @param strict whether both combinations must succeed and produce effective policies
    /// @param a the first
    /// @param b the second
    /// @param sizeCombiner the strategy to combine {@link FileSize}s
    /// @param typeCombiner the strategy to combine {@link FileType}s
    /// @return the combined configuration,
    ///         or {@link Optional#empty()} if no effective policies remain after combination
    /// @throws NullPointerException if any argument is {@code null}
    /// @see #exactlyOneOf(FileValidator, FileValidator)
    /// @see #intersect(boolean, FileValidator, FileValidator)
    // @formatter:on
    public static Optional<FileValidator> combine(
            boolean strict,
            BiFunction<FileSize, FileSize, Optional<FileSize>> sizeCombiner,
            BinaryOperator<Optional<FileType>> typeCombiner,
            FileValidator a,
            FileValidator b) {
        Objects.requireNonNull(a, "a must not be null");
        Objects.requireNonNull(b, "b must not be null");
        Objects.requireNonNull(sizeCombiner, "sizeCombiner must not be null");
        Objects.requireNonNull(typeCombiner, "typeCombiner must not be null");

        var fileSize = sizeCombiner.apply(a.fileSize(), b.fileSize());
        var fileType = typeCombiner.apply(a.fileType(), b.fileType());

        var effectiveSize = fileSize.filter(s -> !s.isDummy());

        if (strict) {
            return effectiveSize.flatMap(s -> fileType.map(t -> new FileValidator(s, t)));
        }

        if (effectiveSize.isPresent() || fileType.isPresent()) {
            return Optional.of(new FileValidator(effectiveSize.orElse(FileSize.DUMMY), fileType));
        }

        return Optional.empty();
    }

    /// Combines two {@link FileValidator}s by retaining the non-dummy file size
    /// and non-empty file type when exactly one of each is present.
    ///
    /// @param a the first
    /// @param b the second
    /// @return the resulting file configuration,
    ///         or {@link Optional#empty()} if the configurations do not contain
    ///         exactly one non-dummy file size and exactly one non-empty file type
    /// @throws NullPointerException if {@code a} or {@code b} is {@code null}
    /// @see #combine
    public static Optional<FileValidator> exactlyOneOf(FileValidator a, FileValidator b) {
        return combine(true, FileSize::exactlyOne, FileType::exactlyOne, a, b);
    }

    // @formatter:off
    /// Returns the intersection of the given {@link FileValidator}s.
    ///
    /// In strict mode, both intersections must succeed.
    /// In non-strict mode, the resulting configuration may contain either intersection,
    /// with {@link FileSize#DUMMY} used when the file size intersection fails.
    ///
    /// @param strict whether both intersections must succeed
    /// @param a the first
    /// @param b the second
    /// @return the intersected file configuration,
    ///         or {@link Optional#empty()} if no effective policies remain after intersection
    /// @throws NullPointerException if {@code a} or {@code b} is {@code null}
    /// @see #combine
    /// @see FileSize#intersect
    /// @see FileType#intersect
    // @formatter:on
    public static Optional<FileValidator> intersect(
            boolean strict,
            FileValidator a,
            FileValidator b) {
        return combine(strict, FileSize::intersect, FileType::intersect, a, b);
    }

    /// Returns the strict intersection of the given {@link FileValidator}s.
    ///
    /// @see #intersect(boolean, FileValidator, FileValidator)
    public static Optional<FileValidator> strictIntersect(
            FileValidator a,
            FileValidator b) {
        return intersect(true, a, b);
    }

    /// Returns the lax intersection of the given {@link FileValidator}s.
    ///
    /// @see #intersect(boolean, FileValidator, FileValidator)
    public static Optional<FileValidator> laxIntersect(
            FileValidator a,
            FileValidator b) {
        return intersect(false, a, b);
    }

    // @formatter:off
    /// Returns the relaxation of the given {@link FileValidator}s.
    ///
    /// When {@code requireAll} is {@code true}, both relaxations must succeed.
    /// Otherwise, the resulting configuration may contain either relaxation,
    /// with {@link FileSize#DUMMY} used when the file size relaxation fails.
    ///
    /// @param requireAll whether both relaxations must succeed
    /// @param a the first
    /// @param b the second
    /// @return the relaxed file configuration,
    ///         or {@link Optional#empty()} if no effective policies remain after relaxation
    /// @throws NullPointerException if {@code a} or {@code b} is {@code null}
    /// @see #combine
    /// @see FileSize#span
    /// @see FileType#union
    // @formatter:on
    public static Optional<FileValidator> relax(
            boolean requireAll,
            FileValidator a,
            FileValidator b) {
        return combine(requireAll, FileSize::optionalOfSpan, FileType::union, a, b);
    }

    // @formatter:off
    /// Validates the file size and metadata against the configured policies.
    ///
    /// In strict mode, size, extension, and MIME type must all be provided,
    /// and the file size and file type policies must not be dummy or absent.
    ///
    /// In lax mode, each provided value is validated when the corresponding policy is available;
    /// an {@link IllegalStateException} is thrown when a provided value requires an unavailable policy.
    ///
    /// @param strict whether all required inputs and validation policies must be present
    /// @param size the file size to validate
    /// @param extension the file extension to validate
    /// @param mimeType the MIME type to validate
    /// @return the file type mappings compatible with the provided metadata,
    ///         or all configured mappings if no metadata is provided
    /// @throws IllegalStateException if the required validation policies are unavailable
    ///                               for the provided inputs
    /// @throws IllegalArgumentException if strict validation is requested without
    ///                                  size, extension, or MIME type
    /// @throws NullPointerException if {@code size}, {@code extension}, or {@code mimeType} is null
    /// @see FileSize#validate
    /// @see FileType#validate
    // @formatter:on
    public List<FileTypeMapping> validateFileSizeAndMetadata(
            boolean strict,
            OptionalLong size,
            Optional<String> extension,
            Optional<String> mimeType) {
        Objects.requireNonNull(size, "size must not be null");
        Objects.requireNonNull(extension, "extension must not be null");
        Objects.requireNonNull(mimeType, "mimeType must not be null");

        if (strict && (fileSize.isDummy() || fileType.isEmpty())) {
            throw new IllegalStateException(
                    "strict validation requires file size and file type policies");
        }

        if (size.isPresent() && fileSize.isDummy()) {
            throw new IllegalStateException(
                    "file size policy must not be dummy when size is provided");
        }

        if ((extension.isPresent() || mimeType.isPresent()) && fileType.isEmpty()) {
            throw new IllegalStateException(
                    "file type policy is required when extension or MIME type is provided");
        }

        if (strict && !(size.isPresent() && extension.isPresent() && mimeType.isPresent())) {
            throw new IllegalArgumentException(
                    "size, extension, and mimeType must all be present in strict mode");
        }

        size.ifPresent(fileSize::validate);

        if (extension.isPresent() || mimeType.isPresent()) {
            return fileType.get().validate(extension, mimeType, Optional.empty());
        }

        return fileType
                .map(o -> o.mappings().stream().toList())
                .orElseGet(List::of);
    }

    /// Validates the file size and metadata in strict mode.
    ///
    /// @see #validateFileSizeAndMetadata(boolean, OptionalLong, Optional, Optional)
    public List<FileTypeMapping> strictValidateFileSizeAndMetadata(
            OptionalLong size,
            Optional<String> extension,
            Optional<String> mimeType) {
        return validateFileSizeAndMetadata(true, size, extension, mimeType);
    }

    /// Validates the file size and metadata in lax mode.
    ///
    /// @see #validateFileSizeAndMetadata(boolean, OptionalLong, Optional, Optional)
    public List<FileTypeMapping> laxValidateFileSizeAndMetadata(
            OptionalLong size,
            Optional<String> extension,
            Optional<String> mimeType) {
        return validateFileSizeAndMetadata(false, size, extension, mimeType);
    }

    // @formatter:off
    /// Validates the image format against a list of candidate file type mappings.
    ///
    /// This method may be used as the second step of a validation flow,
    /// following {@link #validateFileSizeAndMetadata}.
    ///
    /// In strict mode, format must be provided, and the file type policy must not be absent.
    ///
    /// In lax mode, format is validated when present;
    /// an {@link IllegalStateException} is thrown when format is provided
    ///                                  but the file type policy is unavailable
    ///
    /// @param strict whether format and file type policy must be present
    /// @param candidates the file type mappings to validate against
    /// @param format the image format
    /// @throws IllegalStateException if required validation policies are unavailable
    /// @throws IllegalArgumentException if strict validation is requested without format
    /// @throws NullPointerException if {@code candidates} or {@code format} is {@code null}
    /// @see FileType#validate(List, Optional, Optional, Optional)
    // @formatter:on
    public void validateFormat(
            boolean strict,
            List<FileTypeMapping> candidates,
            Optional<String> format) {
        Objects.requireNonNull(candidates, "candidates must not be null");
        Objects.requireNonNull(format, "format must not be null");

        if (strict && fileType.isEmpty()) {
            throw new IllegalStateException(
                    "strict validation requires file type policy");
        }

        if (format.isPresent() && fileType.isEmpty()) {
            throw new IllegalStateException(
                    "file type policy is required when format is provided");
        }

        if (strict && format.isEmpty()) {
            throw new IllegalArgumentException(
                    "format must be present in strict mode");
        }

        if (format.isPresent()) {
            fileType.get().validate(candidates, Optional.empty(), Optional.empty(), format);
        }
    }

    /// Validates the image format in strict mode.
    ///
    /// @see #validateFormat(boolean, List, Optional)
    public void strictValidateFormat(List<FileTypeMapping> candidates, Optional<String> format) {
        validateFormat(true, candidates, format);
    }

    /// Validates the image format in lax mode.
    ///
    /// @see #validateFormat(boolean, List, Optional)
    public void laxValidateFormat(List<FileTypeMapping> candidates, Optional<String> format) {
        validateFormat(false, candidates, format);
    }
}
