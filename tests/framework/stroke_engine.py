"""
StrokeEngine Oracle.
Faithfully mirrors Kotlin StrokeEngine Contract and Implementation.
Handles Bezier smoothing, stroke intersection, and dynamic fountain pen width.
"""

import math
from typing import List, Tuple, Optional
from tests.framework.models import Point, Stroke


class SmoothPathResult:
    """Represents an interpolated 2D path curve constructed from midpoint quadratic Beziers."""
    def __init__(self, segments: List[Tuple[str, Tuple[float, ...]]]):
        # Segments: list of ('moveTo', (x, y)), ('quadTo', (x1, y1, x2, y2)), ('lineTo', (x, y))
        self.segments = segments

    @property
    def is_empty(self) -> bool:
        return len(self.segments) == 0

    def sample_points(self, samples_per_quad: int = 5) -> List[Tuple[float, float]]:
        """Samples discrete (x, y) coordinates along the path."""
        pts = []
        curr_x, curr_y = 0.0, 0.0
        for cmd, args in self.segments:
            if cmd == "moveTo":
                curr_x, curr_y = args[0], args[1]
                pts.append((curr_x, curr_y))
            elif cmd == "lineTo":
                curr_x, curr_y = args[0], args[1]
                pts.append((curr_x, curr_y))
            elif cmd == "quadTo":
                p1x, p1y, p2x, p2y = args
                p0x, p0y = curr_x, curr_y
                for step in range(1, samples_per_quad + 1):
                    t = step / float(samples_per_quad)
                    # B(t) = (1-t)^2 P0 + 2(1-t)t P1 + t^2 P2
                    bx = ((1.0 - t) ** 2) * p0x + 2.0 * (1.0 - t) * t * p1x + (t ** 2) * p2x
                    by = ((1.0 - t) ** 2) * p0y + 2.0 * (1.0 - t) * t * p1y + (t ** 2) * p2y
                    pts.append((bx, by))
                curr_x, curr_y = p2x, p2y
        return pts


class StrokeEngine:

    @staticmethod
    def create_smooth_path(points: List[Point]) -> SmoothPathResult:
        """
        Builds a smooth path using quadratic Bezier mid-points from raw points.
        Kotlin logic:
          if (points.isEmpty()) return empty
          if (points.size == 1) return lineTo(p0.x + 0.1, p0.y + 0.1)
          moveTo(p0)
          for i in 1 until points.size - 1:
            midX = (pPrev.x + pNext.x) / 2
            midY = (pPrev.y + pNext.y) / 2
            quadTo(pPrev.x, pPrev.y, midX, midY)
          lineTo(last.x, last.y)
        """
        segments = []
        if not points:
            return SmoothPathResult(segments)

        if len(points) == 1:
            p0 = points[0]
            segments.append(("moveTo", (p0.x, p0.y)))
            segments.append(("lineTo", (p0.x + 0.1, p0.y + 0.1)))
            return SmoothPathResult(segments)

        segments.append(("moveTo", (points[0].x, points[0].y)))

        for i in range(1, len(points) - 1):
            p_prev = points[i]
            p_next = points[i + 1]
            mid_x = (p_prev.x + p_next.x) / 2.0
            mid_y = (p_prev.y + p_next.y) / 2.0
            segments.append(("quadTo", (p_prev.x, p_prev.y, mid_x, mid_y)))

        last = points[-1]
        segments.append(("lineTo", (last.x, last.y)))
        return SmoothPathResult(segments)

    @staticmethod
    def intersects_stroke(stroke: Stroke, x: float, y: float, radius: float) -> bool:
        """
        Checks if a point intersects a given stroke within a tolerance radius.
        dist = hypot(pt.x - x, pt.y - y) <= radius
        """
        for pt in stroke.points:
            dist = math.hypot(pt.x - x, pt.y - y)
            if dist <= radius:
                return True
        return False

    @staticmethod
    def calculate_dynamic_width(base_width: float, current: Point, previous: Point) -> float:
        """
        Calculates dynamic line width for fountain pen based on stylus pressure and drawing speed.
        speed = distance / timeDiff clamped in [0.1, 4.0]
        pressure = current.pressure clamped in [0.2, 1.0]
        dynamicFactor = (pressure * 1.4) - (speed * 0.15)
        return (baseWidth * dynamicFactor) clamped in [baseWidth * 0.4, baseWidth * 2.2]
        """
        distance = math.hypot(current.x - previous.x, current.y - previous.y)
        time_diff = max(1, current.timestamp - previous.timestamp)
        speed = max(0.1, min(4.0, distance / float(time_diff)))
        pressure = max(0.2, min(1.0, current.pressure))

        dynamic_factor = (pressure * 1.4) - (speed * 0.15)
        return max(base_width * 0.4, min(base_width * 2.2, base_width * dynamic_factor))

    @staticmethod
    def pixel_erase_stroke(stroke: Stroke, x: float, y: float, radius: float) -> List[Stroke]:
        """
        Brush-based localized stroke segment clearing (Pixel / Area Eraser).
        Removes points within radius; splits the stroke into multiple strokes if erased in the middle.
        """
        remaining_segments: List[List[Point]] = []
        current_segment: List[Point] = []

        for pt in stroke.points:
            dist = math.hypot(pt.x - x, pt.y - y)
            if dist > radius:
                current_segment.append(pt)
            else:
                if current_segment:
                    remaining_segments.append(current_segment)
                    current_segment = []

        if current_segment:
            remaining_segments.append(current_segment)

        result_strokes = []
        for seg in remaining_segments:
            if seg:
                result_strokes.append(Stroke(
                    tool=stroke.tool,
                    color=stroke.color,
                    stroke_width=stroke.stroke_width,
                    points=seg,
                    shape_type=stroke.shape_type
                ))
        return result_strokes
