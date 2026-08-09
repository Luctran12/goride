from __future__ import annotations

import math
from dataclasses import dataclass

from ..config import SpatialSettings
from ..errors import ExtractionError


@dataclass(frozen=True, order=True)
class GridCell:
    grid_x: int
    grid_y: int
    cell_id: str


class GridDefinition:
    def __init__(self, settings: SpatialSettings, cell_size_meters: int) -> None:
        if cell_size_meters not in settings.evaluation_cell_sizes_meters:
            raise ExtractionError(
                "FEATURE_CELL_SIZE_UNSUPPORTED",
                "Cell size is not approved by the selected profile",
                {
                    "cellSizeMeters": cell_size_meters,
                    "allowed": list(settings.evaluation_cell_sizes_meters),
                },
            )
        try:
            from pyproj import Transformer

            self._transformer = Transformer.from_crs(
                settings.source_srid,
                settings.projected_srid,
                always_xy=True,
            )
        except ImportError as error:
            raise ExtractionError(
                "SPATIAL_RUNTIME_MISSING",
                "pyproj is required to build spatial features",
            ) from error
        except Exception as error:
            raise ExtractionError(
                "SPATIAL_TRANSFORM_INVALID",
                "The configured source/projected CRS transform is unavailable",
            ) from error
        self.settings = settings
        self.cell_size_meters = cell_size_meters

    def contains_wgs84(self, longitude: float, latitude: float) -> bool:
        bounds = self.settings.study_bounds_wgs84
        return (
            bounds.minimum_longitude <= longitude < bounds.maximum_longitude
            and bounds.minimum_latitude <= latitude < bounds.maximum_latitude
        )

    def assign(self, longitude: float, latitude: float) -> GridCell | None:
        if not self.contains_wgs84(longitude, latitude):
            return None
        try:
            projected_x, projected_y = self._transformer.transform(longitude, latitude)
        except Exception as error:
            raise ExtractionError(
                "SPATIAL_TRANSFORM_FAILED",
                "A pickup coordinate could not be transformed",
            ) from error
        if not math.isfinite(projected_x) or not math.isfinite(projected_y):
            raise ExtractionError(
                "SPATIAL_TRANSFORM_FAILED",
                "A pickup coordinate transformed to a non-finite value",
            )
        return self.assign_projected(projected_x, projected_y)

    def assign_projected(self, projected_x: float, projected_y: float) -> GridCell:
        if not math.isfinite(projected_x) or not math.isfinite(projected_y):
            raise ExtractionError(
                "SPATIAL_COORDINATE_INVALID",
                "Projected grid coordinates must be finite",
            )
        grid_x = math.floor(
            (projected_x - self.settings.grid_origin_x_meters)
            / self.cell_size_meters
        )
        grid_y = math.floor(
            (projected_y - self.settings.grid_origin_y_meters)
            / self.cell_size_meters
        )
        cell_id = ":".join(
            (
                self.settings.grid_version,
                str(self.settings.projected_srid),
                str(self.cell_size_meters),
                str(grid_x),
                str(grid_y),
            )
        )
        return GridCell(grid_x, grid_y, cell_id)

    @staticmethod
    def neighbors(cell: GridCell) -> tuple[tuple[int, int], ...]:
        return tuple(
            (cell.grid_x + dx, cell.grid_y + dy)
            for dx in (-1, 0, 1)
            for dy in (-1, 0, 1)
            if dx or dy
        )
