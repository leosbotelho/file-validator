# File Validator

Java library for validating files against configurable security constraints.

See also [Image Validator](https://github.com/leosbotelho/image-validator).

## Motivation

Existing tools fall short for production-grade file validation due to three main limitations:
* **Isolated Checks & Lack of Consistency**: Libraries may validate extensions, MIME types, or formats separately, lacking configurable mechanisms for cross-attribute validation to ensure these metadata layers actually agree with one another.
* **Rigid Policies & Lack of Configurability**: Applications frequently need to reconcile general configurations and endpoint-specific constraints. Existing tools often lack configurable mechanisms for defining these policies and composing them, making it difficult to derive narrower or more permissive policies from existing configurations.
* **Resource Efficiency (Multi-Step Validation)**: Format detection can be computationally expensive, so cheaper checks can be performed first to narrow down the possible file types before detecting and validating the format.

## Features

* Configurable and flexible validation policies
* Composable validation configurations, supporting both stricter and more permissive policies
* Multi-step file type validation
* Full control over validation scope, allowing selective checks for any combination of attributes
* File size validation with minimum and maximum limits
* File type validation using file extension, MIME type, and format, including consistency checks between them
* Configuration loading, with optional logger integration to report missing properties

## Configuration

Validation policies can be loaded from a properties file.

Not all configuration properties are required.

Example:
```properties
file-size.min=1
# 10 MiB
file-size.max=10485760

file-type.jpeg.extensions=jpg,jpeg
file-type.jpeg.mime-types=image/jpeg
file-type.jpeg.formats=JPEG

file-type.png.extensions=png
file-type.png.mime-types=image/png
file-type.png.formats=PNG

file-type.webp.extensions=webp
file-type.webp.mime-types=image/webp
file-type.webp.formats=WEBP
```

The `unlimited` value can be used for the maximum file size.

## Usage

Basic usage with a single configuration and multi-step validation:
```java
// Load configuration from a file
var fileValidator = FileConfig.load(input);

// First step
var candidates = fileValidator.validateFileSizeAndMetadata(true, fileSize, extension, mimeType);

// Second step
fileValidator.validateFormat(true, candidates, format);
```

You have full control over what is validated by calling the validation methods with the desired parameters.

Also available as aliases:
* `strictValidateFileSizeAndMetadata`
* `laxValidateFileSizeAndMetadata`
* `strictValidateFormat`
* `laxValidateFormat`

<br>

Combining configurations loaded from separate files:
```java
// Load configuration from separate files
var sizeValidator = FileConfig.load(sizeInput);
var typeValidator = FileConfig.load(typeInput);

// Combine the configurations
Optional<FileValidator> fileValidator =
        FileValidator.exactlyOneOf(sizeValidator, typeValidator);
```

Combining general and specific configurations:
```java
// General configuration
var generalValidator = FileConfig.load(generalInput);

// More specific configuration
var specificValidator = FileConfig.load(specificInput);

// Produce a less permissive configuration covering both policies
// strictIntersect and laxIntersect are also available as aliases
Optional<FileValidator> narrowerValidator =
        FileValidator.intersect(true, generalValidator, specificValidator);

// Produce a more permissive configuration covering both policies
Optional<FileValidator> broaderValidator =
        FileValidator.relax(true, generalValidator, specificValidator);
```

Combining configurations with custom strategies:
```java
// Strictly combine
// file size policies using exactlyOne,
// and file type policies using union
Optional<FileValidator> finalValidator = FileValidator.combine(
        true,
        FileSize::exactlyOne,
        FileType::union,
        a,
        b);
```

Also see `FileCombinators.guard`.

Configuration can also be loaded directly from Properties.

An optional logger can be provided to report missing properties.

Alternatively, validation objects may be constructed and combined directly.

See the source code for details. It's extremely well documented.

## Requirements

* Java 27+

## License

See the [MIT License](LICENSE).
