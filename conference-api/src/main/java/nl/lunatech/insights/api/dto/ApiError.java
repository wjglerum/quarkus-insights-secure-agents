package nl.lunatech.insights.api.dto;

/** Uniform JSON error body returned by the API instead of hand-built strings. */
public record ApiError(String error, String description) {}
