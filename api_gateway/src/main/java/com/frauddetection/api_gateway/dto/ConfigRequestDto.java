package com.frauddetection.api_gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * ConfigRequestDto is a data transfer object for simulation configuration updates.
 * Maps incoming JSON parameters to the simulation config fields.
 */
public record ConfigRequestDto(
    @JsonProperty("numUsers") Integer numUsers,
    @JsonProperty("burstProbability") Double burstProbability,
    @JsonProperty("speedMultiplier") Double speedMultiplier
) {}
