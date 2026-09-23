package trium.validator;

import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.BinaryOperator;

/// Functional combinators and guard utilities for validator algebra operations.
public class FileCombinators {
    private FileCombinators() {
    }

    // @formatter:off
    /// Wraps a binary combinator with a precondition guard.
    ///
    /// @throws NullPointerException if {@code precondition} or {@code combinator} is {@code null}
    // @formatter:on
    public static <T> BiFunction<T, T, Optional<T>> guard(
            BiPredicate<T, T> precondition,
            BiFunction<T, T, Optional<T>> combinator) {
        Objects.requireNonNull(precondition, "precondition must not be null");
        Objects.requireNonNull(combinator, "combinator must not be null");

        return (a, b) -> precondition.test(a, b)
                ? combinator.apply(a, b)
                : Optional.empty();
    }

    // @formatter:off
    /// Wraps an optional-aware binary operator
    /// with a precondition guard operating on optionals.
    ///
    /// @throws NullPointerException if {@code precondition} or {@code combinator} is {@code null}
    // @formatter:on
    public static <T> BinaryOperator<Optional<T>> guard(
            BiPredicate<Optional<T>, Optional<T>> precondition,
            BinaryOperator<Optional<T>> combinator) {
        Objects.requireNonNull(precondition, "precondition must not be null");
        Objects.requireNonNull(combinator, "combinator must not be null");

        return (a, b) -> precondition.test(a, b)
                ? combinator.apply(a, b)
                : Optional.empty();
    }
}
