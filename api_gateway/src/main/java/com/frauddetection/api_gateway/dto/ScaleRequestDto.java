package com.frauddetection.api_gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * ScaleRequestDto is a data transfer object for scaling worker requests.
 * Maps incoming JSON fields (e.g. {"service": "producer", "replicas": 3})
 * to Java record properties.
 */
public record ScaleRequestDto(
    @JsonProperty("service") String service,
    @JsonProperty("replicas") int replicas
) {}
