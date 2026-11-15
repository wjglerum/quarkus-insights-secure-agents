package nl.lunatech.insights.api.dto;

/** {@code undo=true} restores a cancelled session; omitted or false cancels it. */
public record CancelSessionRequest(String reason, boolean undo) {}
