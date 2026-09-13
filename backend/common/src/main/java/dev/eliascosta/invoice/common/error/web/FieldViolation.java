package dev.eliascosta.invoice.common.error.web;

import org.jspecify.annotations.Nullable;

/**
 * One entry of the {@code errors[]} extension member of a {@code validation-error} problem ({@code
 * requisitos.md} §8.8). {@code field} is null for object-level (cross-field) violations.
 */
public record FieldViolation(@Nullable String field, String message) {}
