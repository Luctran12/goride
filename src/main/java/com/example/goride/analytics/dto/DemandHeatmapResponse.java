package com.example.goride.analytics.dto;

import com.example.goride.analytics.model.AnalyticsSourceVariant;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Schema(
        name = "DemandHeatmap",
        description = "GeoJSON FeatureCollection of projected square-grid demand cells. "
                + "All returned geometry is transformed to EPSG:4326.",
        requiredProperties = {"type", "metadata", "features"},
        example = """
                {
                  "type": "FeatureCollection",
                  "metadata": {
                    "from": "2026-06-30T17:00:00Z",
                    "to": "2026-07-07T17:00:00Z",
                    "reportingTimezone": "Asia/Ho_Chi_Minh",
                    "cellSizeMeters": 1000,
                    "sourceVariant": "DIRECT",
                    "dataFreshnessAt": "2026-07-07T17:00:00Z"
                  },
                  "features": [{
                    "type": "Feature",
                    "geometry": {
                      "type": "Polygon",
                      "coordinates": [[[106.68, 10.76], [106.69, 10.76],
                        [106.69, 10.77], [106.68, 10.77], [106.68, 10.76]]]
                    },
                    "properties": {
                      "cellId": "32648:1000:685:1190",
                      "tripRequests": 120,
                      "completedTripsByRequestCohort": 97,
                      "completionRate": 0.8083
                    }
                  }]
                }
                """
)
public record DemandHeatmapResponse(
        @Schema(
                description = "GeoJSON root type.",
                allowableValues = "FeatureCollection",
                example = "FeatureCollection"
        )
        String type,
        @Schema(description = "Query, grid and freshness metadata.")
        Metadata metadata,
        @Schema(
                description = "Demand cells sorted by tripRequests descending then cellId. "
                        + "Empty data returns an empty list."
        )
        List<Feature> features
) {
    @Schema(
            name = "DemandHeatmapMetadata",
            description = "Heatmap query and source metadata.",
            requiredProperties = {
                    "from", "to", "reportingTimezone", "cellSizeMeters", "sourceVariant",
                    "dataFreshnessAt"
            }
    )
    public record Metadata(
            @Schema(description = "Inclusive normalized UTC range boundary.")
            Instant from,
            @Schema(description = "Exclusive normalized UTC range boundary.")
            Instant to,
            @Schema(description = "IANA timezone used for the time filter.")
            String reportingTimezone,
            @Schema(
                    description = "Square-grid edge length. Unit: meters. Supported values: "
                            + "250, 500, 1000 and 2000.",
                    example = "1000"
            )
            int cellSizeMeters,
            @Schema(description = "Physical query source used for this response.")
            AnalyticsSourceVariant sourceVariant,
            @Schema(description = "Database snapshot cutoff represented by the response.")
            Instant dataFreshnessAt
    ) {
    }

    @Schema(
            name = "DemandHeatmapFeature",
            description = "One GeoJSON demand feature.",
            requiredProperties = {"type", "geometry", "properties"}
    )
    public record Feature(
            @Schema(
                    description = "GeoJSON feature type.",
                    allowableValues = "Feature",
                    example = "Feature"
            )
            String type,
            @Schema(description = "EPSG:4326 square-cell polygon.")
            Polygon geometry,
            @Schema(description = "Demand metrics for the cell.")
            Properties properties
    ) {
    }

    @Schema(
            name = "DemandHeatmapPolygon",
            description = "GeoJSON Polygon geometry in EPSG:4326 longitude/latitude order.",
            requiredProperties = {"type", "coordinates"}
    )
    public record Polygon(
            @Schema(
                    description = "GeoJSON geometry type.",
                    allowableValues = "Polygon",
                    example = "Polygon"
            )
            String type,
            @Schema(description = "Closed polygon rings in [longitude, latitude] order.")
            List<List<List<BigDecimal>>> coordinates
    ) {
    }

    @Schema(
            name = "DemandHeatmapProperties",
            description = "Request-cohort demand metrics for one stable grid cell.",
            requiredProperties = {
                    "cellId", "tripRequests", "completedTripsByRequestCohort", "completionRate"
            }
    )
    public record Properties(
            @Schema(
                    description = "Stable projected-grid identifier: "
                            + "{projectedSrid}:{cellSizeMeters}:{gridX}:{gridY}.",
                    example = "32648:1000:685:1190"
            )
            String cellId,
            @Schema(description = "Requests in this cell. Unit: trips.", example = "120")
            long tripRequests,
            @Schema(
                    description = "Requested trips in this cell whose current outcome is "
                            + "COMPLETED. Unit: trips.",
                    example = "97"
            )
            long completedTripsByRequestCohort,
            @Schema(
                    description = "completedTripsByRequestCohort / tripRequests. Unit: ratio; "
                            + "scale: 4; non-null because every returned cell has at least one "
                            + "request.",
                    example = "0.8083",
                    minimum = "0",
                    maximum = "1"
            )
            BigDecimal completionRate
    ) {
    }
}
