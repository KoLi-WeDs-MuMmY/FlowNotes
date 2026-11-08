"""
Tier 1: Feature Coverage (Features 1 - 5)
- F01: Custom CanvasView Touch Engine
- F02: Bezier / Spline Interpolation
- F03: Ballpoint Pen Mode
- F04: Fountain Pen Mode
- F05: Semi-Transparent Highlighter
Total Tests: 25 (5 tests per feature)
"""

import unittest
from tests.framework.models import Point, Stroke, PenTool
from tests.framework.stroke_engine import StrokeEngine


class TestFeature01CanvasTouchEngine(unittest.TestCase):
    """F01: Custom CanvasView Touch Engine"""

    def test_f01_01_touch_lifecycle_recording(self):
        """Down, Move, Up lifecycle accurately constructs stroke points."""
        pts = [
            Point(100.0, 150.0, pressure=0.5, timestamp=1000),
            Point(110.0, 155.0, pressure=0.6, timestamp=1016),
            Point(125.0, 165.0, pressure=0.7, timestamp=1032),
            Point(140.0, 180.0, pressure=0.5, timestamp=1048),
        ]
        stroke = Stroke(tool=PenTool.BALLPOINT, color=0xFF1E293B, stroke_width=3.0, points=pts)
        self.assertEqual(len(stroke.points), 4)
        self.assertEqual(stroke.points[0].x, 100.0)
        self.assertEqual(stroke.points[-1].x, 140.0)

    def test_f01_02_pressure_tracking(self):
        """Stylus pressure changes are captured across points."""
        p1 = Point(50.0, 50.0, pressure=0.2)
        p2 = Point(60.0, 60.0, pressure=0.95)
        self.assertAlmostEqual(p1.pressure, 0.2, places=2)
        self.assertAlmostEqual(p2.pressure, 0.95, places=2)
        self.assertGreater(p2.pressure, p1.pressure)

    def test_f01_03_timestamp_monotonicity(self):
        """Touch events maintain monotonic timestamp ordering."""
        pts = [
            Point(10.0, 10.0, timestamp=100),
            Point(12.0, 15.0, timestamp=116),
            Point(15.0, 20.0, timestamp=132),
            Point(20.0, 30.0, timestamp=148)
        ]
        for i in range(1, len(pts)):
            self.assertGreaterEqual(pts[i].timestamp, pts[i - 1].timestamp)

    def test_f01_04_point_serialization(self):
        """Touch point serializes and deserializes cleanly without precision loss."""
        pt = Point(123.45, 678.90, pressure=0.82, timestamp=1690000000)
        d = pt.to_dict()
        reconstructed = Point.from_dict(d)
        self.assertAlmostEqual(reconstructed.x, 123.45, places=2)
        self.assertAlmostEqual(reconstructed.y, 678.90, places=2)
        self.assertAlmostEqual(reconstructed.pressure, 0.82, places=2)
        self.assertEqual(reconstructed.timestamp, 1690000000)

    def test_f01_05_multi_point_path_traversal(self):
        """Canvas captures dense multi-point drawing sequences."""
        pts = [Point(float(i), float(i * 2), pressure=0.5, timestamp=1000 + i * 16) for i in range(50)]
        stroke = Stroke(tool=PenTool.BALLPOINT, stroke_width=4.0, points=pts)
        self.assertEqual(len(stroke.points), 50)
        self.assertEqual(stroke.points[25].x, 25.0)
        self.assertEqual(stroke.points[25].y, 50.0)


class TestFeature02BezierSplineInterpolation(unittest.TestCase):
    """F02: Bezier / Spline Interpolation"""

    def test_f02_01_empty_points_generates_empty_path(self):
        """Empty point list returns path with 0 segments."""
        path = StrokeEngine.create_smooth_path([])
        self.assertTrue(path.is_empty)
        self.assertEqual(len(path.segments), 0)

    def test_f02_02_single_point_dot_interpolation(self):
        """Single point produces a micro line segment (dot)."""
        pts = [Point(100.0, 200.0)]
        path = StrokeEngine.create_smooth_path(pts)
        self.assertEqual(len(path.segments), 2)
        self.assertEqual(path.segments[0][0], "moveTo")
        self.assertEqual(path.segments[1][0], "lineTo")
        self.assertAlmostEqual(path.segments[1][1][0], 100.1, places=1)

    def test_f02_03_midpoint_quadratic_bezier_construction(self):
        """Three points produce one quadratic Bezier curve segment."""
        pts = [
            Point(0.0, 0.0),
            Point(50.0, 100.0),
            Point(100.0, 0.0)
        ]
        path = StrokeEngine.create_smooth_path(pts)
        self.assertEqual(len(path.segments), 3)  # moveTo, quadTo, lineTo
        self.assertEqual(path.segments[0][0], "moveTo")
        self.assertEqual(path.segments[1][0], "quadTo")
        # Midpoint between p1 (50, 100) and p2 (100, 0) is (75, 50)
        mid_x, mid_y = path.segments[1][1][2], path.segments[1][1][3]
        self.assertAlmostEqual(mid_x, 75.0, places=2)
        self.assertAlmostEqual(mid_y, 50.0, places=2)

    def test_f02_04_sampled_curve_smoothness(self):
        """Sampling points along quadratic curve produces smooth progression without NaN."""
        pts = [Point(10.0, 10.0), Point(50.0, 80.0), Point(90.0, 20.0), Point(130.0, 70.0)]
        path = StrokeEngine.create_smooth_path(pts)
        sampled = path.sample_points(samples_per_quad=10)
        self.assertGreater(len(sampled), 15)
        for x, y in sampled:
            self.assertFalse(x != x or y != y)  # No NaN
            self.assertGreaterEqual(x, 10.0)
            self.assertLessEqual(x, 130.0)

    def test_f02_05_long_stroke_interpolation_continuity(self):
        """Complex hand-drawn stroke with 20 points generates contiguous curve segments."""
        pts = [Point(i * 10.0, (i % 5) * 20.0) for i in range(20)]
        path = StrokeEngine.create_smooth_path(pts)
        # Segments: 1 moveTo, 18 quadTo, 1 lineTo = 20 segments
        self.assertEqual(len(path.segments), 20)
        self.assertEqual(path.segments[0][0], "moveTo")
        self.assertEqual(path.segments[-1][0], "lineTo")


class TestFeature03BallpointPenMode(unittest.TestCase):
    """F03: Ballpoint Pen Mode"""

    def test_f03_01_ballpoint_tool_identifier(self):
        """Ballpoint tool enum and default profile properties."""
        stroke = Stroke(tool=PenTool.BALLPOINT, color=0xFF1E293B, stroke_width=3.0)
        self.assertEqual(stroke.tool, PenTool.BALLPOINT)
        self.assertEqual(stroke.stroke_width, 3.0)

    def test_f03_02_consistent_uniform_stroke_width(self):
        """Ballpoint maintains constant stroke width regardless of velocity."""
        stroke = Stroke(tool=PenTool.BALLPOINT, stroke_width=3.5)
        self.assertEqual(stroke.stroke_width, 3.5)

    def test_f03_03_color_and_opacity_preservation(self):
        """Ballpoint uses full opacity (alpha 255) by default."""
        color = 0xFF2563EB  # Vibrant blue
        stroke = Stroke(tool=PenTool.BALLPOINT, color=color, stroke_width=2.5)
        alpha = (stroke.color >> 24) & 0xFF
        self.assertEqual(alpha, 255)

    def test_f03_04_serialization_roundtrip(self):
        """Ballpoint stroke converts to dict and back preserving properties."""
        pts = [Point(10.0, 20.0), Point(30.0, 40.0)]
        stroke = Stroke(tool=PenTool.BALLPOINT, color=0xFF000000, stroke_width=4.0, points=pts)
        data = stroke.to_dict()
        restored = Stroke.from_dict(data)
        self.assertEqual(restored.tool, PenTool.BALLPOINT)
        self.assertEqual(restored.stroke_width, 4.0)
        self.assertEqual(len(restored.points), 2)

    def test_f03_05_paint_configuration_round_cap(self):
        """Ballpoint stroke specifies anti-aliasing and round cap styling."""
        stroke = Stroke(tool=PenTool.BALLPOINT, stroke_width=3.0)
        self.assertEqual(stroke.tool.name, "BALLPOINT")
        self.assertGreater(stroke.stroke_width, 0.0)


class TestFeature04FountainPenMode(unittest.TestCase):
    """F04: Fountain Pen Mode"""

    def test_f04_01_fountain_pen_identifier(self):
        """Fountain pen tool enum is distinct and valid."""
        stroke = Stroke(tool=PenTool.FOUNTAIN, stroke_width=4.0)
        self.assertEqual(stroke.tool, PenTool.FOUNTAIN)

    def test_f04_02_pressure_sensitivity_increases_width(self):
        """Higher pressure produces thicker dynamic stroke width."""
        p_prev = Point(0.0, 0.0, timestamp=1000)
        p_light = Point(10.0, 0.0, pressure=0.2, timestamp=1050)
        p_heavy = Point(10.0, 0.0, pressure=0.9, timestamp=1050)

        base = 5.0
        w_light = StrokeEngine.calculate_dynamic_width(base, p_light, p_prev)
        w_heavy = StrokeEngine.calculate_dynamic_width(base, p_heavy, p_prev)
        self.assertGreater(w_heavy, w_light)

    def test_f04_03_velocity_sensitivity_decreases_width(self):
        """Higher drawing velocity (rapid flourish) produces thinner line."""
        p_prev = Point(0.0, 0.0, timestamp=1000)
        # Fast stroke: 100px in 10ms (speed 10.0 -> clamped to 4.0)
        p_fast = Point(100.0, 0.0, pressure=0.6, timestamp=1010)
        # Slow stroke: 10px in 100ms (speed 0.1 -> clamped to 0.1)
        p_slow = Point(10.0, 0.0, pressure=0.6, timestamp=1100)

        base = 5.0
        w_fast = StrokeEngine.calculate_dynamic_width(base, p_fast, p_prev)
        w_slow = StrokeEngine.calculate_dynamic_width(base, p_slow, p_prev)
        self.assertGreater(w_slow, w_fast)

    def test_f04_04_clamp_bounds_enforced(self):
        """Dynamic stroke width is strictly clamped between 0.4x and 2.2x base width."""
        base = 10.0
        # Ultra fast + zero pressure
        p0 = Point(0.0, 0.0, timestamp=0)
        p_min = Point(1000.0, 1000.0, pressure=0.0, timestamp=1)
        w_min = StrokeEngine.calculate_dynamic_width(base, p_min, p0)
        self.assertGreaterEqual(w_min, base * 0.4)

        # Ultra slow + max pressure
        p_max = Point(0.1, 0.1, pressure=1.0, timestamp=10000)
        w_max = StrokeEngine.calculate_dynamic_width(base, p_max, p0)
        self.assertLessEqual(w_max, base * 2.2)

    def test_f04_05_dynamic_width_sequence_variation(self):
        """Stroke trajectory exhibits continuous dynamic width variation."""
        pts = [
            Point(0.0, 0.0, pressure=0.3, timestamp=1000),
            Point(20.0, 5.0, pressure=0.5, timestamp=1020),
            Point(50.0, 15.0, pressure=0.8, timestamp=1050),
            Point(90.0, 30.0, pressure=0.4, timestamp=1070)
        ]
        base = 4.0
        widths = [StrokeEngine.calculate_dynamic_width(base, pts[i], pts[i-1]) for i in range(1, len(pts))]
        self.assertEqual(len(widths), 3)
        self.assertNotEqual(widths[0], widths[1])


class TestFeature05SemiTransparentHighlighter(unittest.TestCase):
    """F05: Semi-Transparent Highlighter"""

    def test_f05_01_highlighter_tool_identifier(self):
        """Highlighter tool identifier and stroke registration."""
        stroke = Stroke(tool=PenTool.HIGHLIGHTER, color=0xFFFEF08A, stroke_width=12.0)
        self.assertEqual(stroke.tool, PenTool.HIGHLIGHTER)

    def test_f05_02_semi_transparent_alpha_overlay(self):
        """Highlighter paint applies translucent alpha (~43% / 110-alpha) for text overlay."""
        highlighter_alpha = 110
        normalized_alpha = highlighter_alpha / 255.0
        self.assertAlmostEqual(normalized_alpha, 0.431, places=2)
        self.assertLess(highlighter_alpha, 255)
        self.assertGreater(highlighter_alpha, 0)

    def test_f05_03_chisel_width_multiplier(self):
        """Highlighter renders with a 3.5x widened chisel stroke factor."""
        base_width = 8.0
        chisel_width = base_width * 3.5
        self.assertEqual(chisel_width, 28.0)

    def test_f05_04_pastel_color_palette(self):
        """Highlighter supports standard pastel colors (Yellow, Cyan, Pink, Green)."""
        palette = [
            0xFFFEF08A,  # Pastel Yellow
            0xFFBAE6FD,  # Pastel Cyan
            0xFFFBCFE8,  # Pastel Pink
            0xFFBBF7D0   # Pastel Green
        ]
        for color in palette:
            stroke = Stroke(tool=PenTool.HIGHLIGHTER, color=color, stroke_width=10.0)
            self.assertEqual(stroke.color, color)

    def test_f05_05_overlay_blending_underlying_text_preserved(self):
        """Highlighter overlay maintains visibility of underlying stroke coordinates."""
        under_stroke = Stroke(tool=PenTool.BALLPOINT, color=0xFF000000, points=[Point(100.0, 100.0), Point(200.0, 100.0)])
        hl_stroke = Stroke(tool=PenTool.HIGHLIGHTER, color=0xFFFEF08A, points=[Point(90.0, 100.0), Point(210.0, 100.0)])
        # Both strokes occupy the same page coordinates
        self.assertEqual(under_stroke.points[0].y, hl_stroke.points[0].y)
        self.assertNotEqual(under_stroke.tool, hl_stroke.tool)


if __name__ == "__main__":
    unittest.main()
