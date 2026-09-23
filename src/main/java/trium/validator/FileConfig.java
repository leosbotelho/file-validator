package trium.validator;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Consumer;

import trium.validator.policy.FileSize;
import trium.validator.policy.FileType;
import trium.validator.policy.FileTypeMapping;

/// Defines the file validation configuration for file size and file types.
///
/// At least one validation policy must be defined.
///
/// The configuration is used to create a {@link FileValidator}.
public class FileConfig {
    /// Prefix for file size configuration properties.
    public static final String FILE_SIZE_PREFIX = "file-size.";

    /// Prefix for file type configuration properties.
    public static final String FILE_TYPE_PREFIX = "file-type.";

    /// String representation of an unlimited maximum file size.
    public static final String MAX_UNLIMITED_STR = "unlimited";

    private FileConfig() {
    }

    // @formatter:off
    /// Loads file validation configuration from the given properties.
    ///
    /// Missing properties are logged when a logger is provided.
    ///
    /// Validation of configured values is limited to the constraints enforced by
    /// {@link FileSize}, {@link FileTypeMapping}, and {@link FileType}.
    ///
    /// @param log optional logger for reporting missing configuration properties
    /// @param properties the properties containing the file validation configuration
    /// @return the loaded {@link FileValidator}
    /// @throws NullPointerException if {@code log} or {@code properties} is {@code null}
    /// @throws IllegalArgumentException if a configured value is invalid
    /// @throws NoFilePolicyDefinedException if no configuration is provided
    /// @see #load(InputStream)
    /// @see #load(Properties)
    // @formatter:on
    public static FileValidator load(Optional<Consumer<String>> log, Properties properties) {
        Objects.requireNonNull(log, "log must not be null");
        Objects.requireNonNull(properties, "properties must not be null");

        var minRaw = properties.getProperty(FILE_SIZE_PREFIX + "min");
        var maxRaw = properties.getProperty(FILE_SIZE_PREFIX + "max");

        long min = 0;
        long max = FileSize.MAX_UNLIMITED;

        if (minRaw != null) {
            try {
                min = Long.parseLong(minRaw);
            } catch (NumberFormatException e) {
                if (minRaw.toLowerCase(Locale.ROOT).equals(MAX_UNLIMITED_STR)) {
                    min = FileSize.MAX_UNLIMITED;
                } else {
                    throw new IllegalArgumentException("invalid %smin".formatted(FILE_SIZE_PREFIX));
                }
            }
        } else {
            log.ifPresent(o -> o.accept("%smin is not configured".formatted(FILE_SIZE_PREFIX)));
        }
        if (maxRaw != null) {
            try {
                max = Long.parseLong(maxRaw);
            } catch (NumberFormatException e) {
                if (maxRaw.toLowerCase(Locale.ROOT).equals(MAX_UNLIMITED_STR)) {
                    max = FileSize.MAX_UNLIMITED;
                } else {
                    throw new IllegalArgumentException("invalid %smax".formatted(FILE_SIZE_PREFIX));
                }
            }
        } else {
            log.ifPresent(o -> o.accept("%smax is not configured".formatted(FILE_SIZE_PREFIX)));
        }

        var fileSize = new FileSize(min, max);

        if (fileSize.isDummy()) {
            log.ifPresent(o -> o.accept(FILE_SIZE_PREFIX + " is dummy"));
        }

        var names = properties.stringPropertyNames().stream()
                .filter(name -> name.startsWith(FILE_TYPE_PREFIX))
                .map(name -> name.substring(FILE_TYPE_PREFIX.length()))
                .filter(name -> name.contains("."))
                .map(name -> name.substring(0, name.indexOf('.')))
                .distinct()
                .toList();

        var mappings = new HashSet<FileTypeMapping>();

        for (var name : names) {
            var extensions = properties.getProperty(FILE_TYPE_PREFIX + name + ".extensions");
            var mimeTypes = properties.getProperty(FILE_TYPE_PREFIX + name + ".mime-types");
            var formats = properties.getProperty(FILE_TYPE_PREFIX + name + ".formats");

            if (extensions == null) {
                log.ifPresent(o -> o.accept(
                        FILE_TYPE_PREFIX + name + ".extensions is not configured"));
            }
            if (mimeTypes == null) {
                log.ifPresent(o -> o.accept(
                        FILE_TYPE_PREFIX + name + ".mime-types is not configured"));
            }
            if (formats == null) {
                log.ifPresent(o -> o.accept(
                        FILE_TYPE_PREFIX + name + ".formats is not configured"));
            }

            mappings.add(new FileTypeMapping(
                    extensions == null ? Set.of() : Set.of(extensions.split(",")),
                    mimeTypes == null ? Set.of() : Set.of(mimeTypes.split(",")),
                    formats == null ? Set.of() : Set.of(formats.split(","))));
        }

        if (mappings.isEmpty()) {
            log.ifPresent(o -> o.accept(FILE_TYPE_PREFIX + " is not configured"));
        }

        Optional<FileType> fileType = mappings.isEmpty()
                ? Optional.empty()
                : Optional.of(new FileType(mappings));

        return new FileValidator(fileSize, fileType);
    }

    /// Loads file validation configuration from the given properties
    /// without logging missing properties.
    ///
    /// @see #load(Optional, Properties)
    /// @see #load(InputStream)
    public static FileValidator load(Properties properties) {
        return load(Optional.empty(), properties);
    }

    // @formatter:off
    /// Loads file validation configuration from the given input stream.
    ///
    /// The input stream is loaded into a {@link Properties} object and delegated to
    /// {@link #load(Optional, Properties)}.
    ///
    /// @param log   optional logger for reporting missing configuration properties
    /// @param input the input stream containing the file validation configuration
    /// @return the loaded {@link FileValidator}
    /// @throws NullPointerException     if {@code log} or {@code input} is null
    /// @throws IOException              if an error occurred when reading from the
    ///                                  input stream
    /// @throws IllegalArgumentException if the input stream contains
    ///                                  a malformed Unicode escape sequence,
    ///                                  or if a configured value is invalid
    /// @throws NoFilePolicyDefinedException if no configuration is provided
    /// @see #load(InputStream)
    // @formatter:on
    public static FileValidator load(Optional<Consumer<String>> log, InputStream input)
            throws IOException {
        Objects.requireNonNull(log, "log must not be null");
        Objects.requireNonNull(input, "input must not be null");

        var properties = new Properties();
        properties.load(input);

        return load(log, properties);
    }

    /// Loads file validation configuration from the given input stream
    /// without logging missing properties.
    ///
    /// Exceptions thrown during loading are documented
    /// by the corresponding overload.
    ///
    /// @see #load(Optional, InputStream)
    /// @see #load(Optional, Properties)
    public static FileValidator load(InputStream input) throws IOException {
        return load(Optional.empty(), input);
    }
}
