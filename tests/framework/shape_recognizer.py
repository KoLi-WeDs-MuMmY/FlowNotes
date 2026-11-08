"""
ShapeRecognizer Oracle.
Faithfully mirrors Kotlin ShapeRecognizer logic from com.flownotes.app.engine.ShapeRecognizer.
Recognizes Lines, Rectangles, Circles, and Triangles from raw ink point paths.
"""

import math
from typing import List, Optional, Dict, Any
from tests.framework.models import Point


class DetectedShape:
    class Line:
        def __init__(self, start_x: float, start_y: float, end_x: float, end_y: float):
            self.type = "LINE"
            self.start_x = start_x
            self.start_y = start_y
            self.end_x = end_x
            self.end_y = end_y

        @property
        def length(self) -> float:
            return math.hypot(self.end_x - self.start_x, self.end_y - self.start_y)

    class Rectangle:
        def __init__(self, min_x: float, min_y: float, max_x: float, max_y: float):
            self.type = "RECTANGLE"
            self.min_x = min_x
            self.min_y = min_y
            self.max_x = max_x
            self.max_y = max_y

        @property
        def width(self) -> float:
            return self.max_x - self.min_x

        @property
        def height(self) -> float:
            return self.max_y - self.min_y

    class Circle:
        def __init__(self, center_x: float, center_y: float, radius: float):
            self.type = "CIRCLE"
            self.center_x = center_x
            self.center_y = center_y
            self.radius = radius

    class Triangle:
        def __init__(self, p1: Point, p2: Point, p3: Point):
            self.type = "TRIANGLE"
            self.p1 = p1
            self.p2 = p2
            self.p3 = p3


class ShapeRecognizer:

    @staticmethod
    def recognize(points: List[Point]) -> Optional[Any]:
        if len(points) < 4:
            return None

        start = points[0]
        end = points[-1]

        min_x = min(p.x for p in points)
        max_x = max(p.x for p in points)
        min_y = min(p.y for p in points)
        max_y = max(p.y for p in points)

        width = max_x - min_x
        height = max_y - min_y
        diagonal = math.hypot(width, height)
        end_to_end_dist = math.hypot(end.x - start.x, end.y - start.y)

        # 1. Straight line: end-to-end distance is close to diagonal
        if diagonal > 40.0 and end_to_end_dist > diagonal * 0.88:
            return DetectedShape.Line(start.x, start.y, end.x, end.y)

        # 2. Closed shape
        is_closed = end_to_end_dist < diagonal * 0.18
        if is_closed and diagonal > 40.0:
            aspect_ratio = width / max(1.0, height)
            cx = min_x + width / 2.0
            cy = min_y + height / 2.0
            radius = (width + height) / 4.0

            # Check if points roughly lie along circle radius
            rad_diffs = [abs(math.hypot(p.x - cx, p.y - cy) - radius) for p in points]
            avg_diff = sum(rad_diffs) / len(points)

            if 0.75 <= aspect_ratio <= 1.35 and avg_diff < radius * 0.3:
                return DetectedShape.Circle(cx, cy, radius)
            else:
                return DetectedShape.Rectangle(min_x, min_y, max_x, max_y)

        return None
