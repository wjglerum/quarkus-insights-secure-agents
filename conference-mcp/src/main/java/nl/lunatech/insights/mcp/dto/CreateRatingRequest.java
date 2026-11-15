package nl.lunatech.insights.mcp.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateRatingRequest(int stars, String comment) {}
