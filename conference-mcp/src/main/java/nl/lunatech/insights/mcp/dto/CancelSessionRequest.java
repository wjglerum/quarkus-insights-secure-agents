package nl.lunatech.insights.mcp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CancelSessionRequest(String reason, boolean undo) {}
