package nl.lunatech.insights.mcp.dto;

public record CancelSessionRequest(String reason, boolean undo) {}
