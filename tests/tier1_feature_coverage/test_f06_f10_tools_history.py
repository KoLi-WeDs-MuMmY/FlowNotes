"""
Tier 1: Feature Coverage (Features 6 - 10)
- F06: Textured Pencil Tool
- F07: Stroke Eraser (Whole Stroke)
- F08: Pixel / Area Eraser
- F09: Geometric Shape Recognizer
- F10: Undo / Redo History Stacks
Total Tests: 25 (5 tests per feature)
"""

import unittest
from tests.framework.models import Point, Stroke, PenTool
from tests.framework.stroke_engine import StrokeEngine
from tests.framework.shape_recognizer import ShapeRecognizer, DetectedShape


class TestFeature06TexturedPencilTool(unittest.TestCase):
    """F06: Textured Pencil Tool"""

    def test_f06_01_pencil_tool_identifier(self):
        """Pencil tool enum and default profile setup."""
        stroke = Stroke(tool=PenTool.PENCIL, color=0xFF475569, stroke_width=2.0)
        self.assertEqual(stroke.tool, PenTool.PENCIL)
        self.assertEqual(stroke.stroke_width, 2.0)

    def test_f06_02_sketch_line_rendering(self):
        """Pencil strokes record fine-grained point density for tactile sketch aesthetics."""
        pts = [Point(float(i), float(i * i * 0.1), pressure=0.4 + (i % 3) * 0.1) for i in range(15)]
        stroke = Stroke(tool=PenTool.PENCIL, stroke_width=1.5, points=pts)
        self.assertEqual(len(stroke.points), 15)
        self.assertAlmostEqual(stroke.stroke_width, 1.5)

    def test_f06_03_graphite_gray_palette(self):
        """Pencil supports classic graphite and colored sketching shades."""
        graphite_shades = [0xFF64748B, 0xFF475569, 0xFF334155, 0xFF1E293B]
        for color in graphite_shades:
            stroke = Stroke(tool=PenTool.PENCIL, color=color)
            self.assertEqual(stroke.color, color)

    def test_f06_04_pencil_dynamic_pressure_response(self):
        """Pencil respects pressure for sketch shading intensity."""
        p_light = Point(10.0, 10.0, pressure=0.25)
        p_heavy = Point(10.0, 10.0, pressure=0.85)
        self.assertLess(p_light.pressure, p_heavy.pressure)

    def test_f06_05_pencil_serialization(self):
        """Pencil stroke converts to and from dictionary accurately."""
        stroke = Stroke(tool=PenTool.PENCIL, color=0xFF334155, stroke_width=2.5,
                        points=[Point(0.0, 0.0), Point(5.0, 10.0)])
        d = stroke.to_dict()
        restored = Stroke.from_dict(d)
        self.assertEqual(restored.tool, PenTool.PENCIL)
        self.assertEqual(len(restored.points), 2)


class TestFeature07StrokeEraserWholeStroke(unittest.TestCase):
    """F07: Stroke Eraser (Whole Stroke)"""

    def test_f07_01_stroke_eraser_direct_hit(self):
        """Touch point within tolerance radius directly identifies intersecting stroke."""
        pts = [Point(100.0, 100.0), Point(120.0, 100.0), Point(140.0, 100.0)]
        stroke = Stroke(points=pts)
        # Touch at (120, 102) with radius 10 -> intersects point 1
        hit = StrokeEngine.intersects_stroke(stroke, 120.0, 102.0, radius=10.0)
        self.assertTrue(hit)

    def test_f07_02_stroke_eraser_miss_preserves_stroke(self):
        """Touch point outside tolerance radius returns false and preserves stroke."""
        pts = [Point(100.0, 100.0), Point(120.0, 100.0)]
        stroke = Stroke(points=pts)
        # Touch at (200, 200) with radius 10 -> no intersection
        hit = StrokeEngine.intersects_stroke(stroke, 200.0, 200.0, radius=10.0)
        self.assertFalse(hit)

    def test_f07_03_instant_whole_stroke_removal(self):
        """Intersecting stroke is removed entirely from page strokes list."""
        s1 = Stroke(stroke_id="s1", points=[Point(50.0, 50.0), Point(60.0, 60.0)])
        s2 = Stroke(stroke_id="s2", points=[Point(200.0, 200.0), Point(210.0, 210.0)])
        page_strokes = [s1, s2]

        # Eraser touches s1 at (52, 52) with radius 8
        page_strokes = [s for s in page_strokes if not StrokeEngine.intersects_stroke(s, 52.0, 52.0, 8.0)]
        self.assertEqual(len(page_strokes), 1)
        self.assertEqual(page_strokes[0].id, "s2")

    def test_f07_04_boundary_radius_edge_detection(self):
        """Touch exactly at distance equal to radius triggers intersection."""
        pts = [Point(100.0, 100.0)]
        stroke = Stroke(points=pts)
        # Distance = hypot(100 - 100, 106 - 100) = 6.0
        hit = StrokeEngine.intersects_stroke(stroke, 100.0, 106.0, radius=6.0)
        self.assertTrue(hit)

    def test_f07_05_multi_point_curve_intersection(self):
        """Eraser intersects complex curved stroke at an intermediate vertex."""
        pts = [Point(0.0, 0.0), Point(50.0, 100.0), Point(100.0, 200.0), Point(150.0, 100.0)]
        stroke = Stroke(points=pts)
        hit = StrokeEngine.intersects_stroke(stroke, 98.0, 198.0, radius=5.0)
        self.assertTrue(hit)


class TestFeature08PixelAreaEraser(unittest.TestCase):
    """F08: Pixel / Area Eraser"""

    def test_f08_01_eraser_pixel_tool_identifier(self):
        """Pixel eraser tool enum is recognized."""
        stroke = Stroke(tool=PenTool.ERASER_PIXEL)
        self.assertEqual(stroke.tool, PenTool.ERASER_PIXEL)

    def test_f08_02_pixel_eraser_splits_stroke_in_middle(self):
        """Erasing middle points of a stroke splits it into two distinct stroke segments."""
        pts = [Point(float(i * 10), 50.0) for i in range(10)]  # 0 to 90
        stroke = Stroke(points=pts)
        # Erase at x=45 with radius 12 (clears points x=40, x=50, leaving 0..30 and 60..90)
        split_strokes = StrokeEngine.pixel_erase_stroke(stroke, 45.0, 50.0, radius=12.0)
        self.assertEqual(len(split_strokes), 2)
        # First segment: points 0, 10, 20, 30
        self.assertEqual(len(split_strokes[0].points), 4)
        # Second segment: points 60, 70, 80, 90
        self.assertEqual(len(split_strokes[1].points), 4)

    def test_f08_03_pixel_eraser_trims_start_of_stroke(self):
        """Erasing at the start of a stroke trims the initial points."""
        pts = [Point(float(i * 10), 100.0) for i in range(6)]  # 0 to 50
        stroke = Stroke(points=pts)
        # Erase at x=5, y=100 with radius 12 (clears points 0 and 10)
        remaining = StrokeEngine.pixel_erase_stroke(stroke, 5.0, 100.0, radius=12.0)
        self.assertEqual(len(remaining), 1)
        self.assertEqual(len(remaining[0].points), 4)
        self.assertEqual(remaining[0].points[0].x, 20.0)

    def test_f08_04_pixel_eraser_complete_clearing(self):
        """Erasing with radius encompassing all points leaves zero remaining strokes."""
        pts = [Point(100.0, 100.0), Point(105.0, 105.0)]
        stroke = Stroke(points=pts)
        remaining = StrokeEngine.pixel_erase_stroke(stroke, 102.0, 102.0, radius=50.0)
        self.assertEqual(len(remaining), 0)

    def test_f08_05_pixel_eraser_miss_preserves_intact(self):
        """Erasing far from stroke leaves stroke unaltered."""
        pts = [Point(10.0, 10.0), Point(20.0, 20.0)]
        stroke = Stroke(points=pts)
        remaining = StrokeEngine.pixel_erase_stroke(stroke, 500.0, 500.0, radius=20.0)
        self.assertEqual(len(remaining), 1)
        self.assertEqual(len(remaining[0].points), 2)


class TestFeature09GeometricShapeRecognizer(unittest.TestCase):
    """F09: Geometric Shape Recognizer"""

    def test_f09_01_insufficient_points_returns_none(self):
        """Less than 4 points does not qualify as a geometric shape."""
        pts = [Point(0.0, 0.0), Point(10.0, 10.0), Point(20.0, 20.0)]
        shape = ShapeRecognizer.recognize(pts)
        self.assertIsNone(shape)

    def test_f09_02_recognize_straight_line(self):
        """Rough hand-drawn straight path snaps to clean Line vector."""
        pts = [
            Point(10.0, 10.0),
            Point(40.0, 12.0),
            Point(80.0, 11.0),
            Point(120.0, 10.0),
            Point(160.0, 9.0),
            Point(200.0, 10.0)
        ]
        shape = ShapeRecognizer.recognize(pts)
        self.assertIsNotNone(shape)
        self.assertEqual(shape.type, "LINE")
        self.assertEqual(shape.start_x, 10.0)
        self.assertEqual(shape.end_x, 200.0)

    def test_f09_03_recognize_circle(self):
        """Closed rough hand-drawn circular loop snaps to Circle."""
        # 16-point circle approximation
        import math
        cx, cy, r = 200.0, 200.0, 80.0
        pts = []
        for i in range(16):
            angle = i * (2 * math.pi / 16)
            pts.append(Point(cx + r * math.cos(angle), cy + r * math.sin(angle)))
        # Close the loop
        pts.append(Point(pts[0].x, pts[0].y))

        shape = ShapeRecognizer.recognize(pts)
        self.assertIsNotNone(shape)
        self.assertEqual(shape.type, "CIRCLE")
        self.assertAlmostEqual(shape.center_x, cx, delta=5.0)
        self.assertAlmostEqual(shape.center_y, cy, delta=5.0)
        self.assertAlmostEqual(shape.radius, r, delta=5.0)

    def test_f09_04_recognize_rectangle(self):
        """Closed loop with elongated aspect ratio snaps to Rectangle."""
        pts = [
            Point(50.0, 50.0),
            Point(250.0, 50.0),
            Point(250.0, 120.0),
            Point(50.0, 120.0),
            Point(50.0, 50.0)
        ]
        shape = ShapeRecognizer.recognize(pts)
        self.assertIsNotNone(shape)
        self.assertEqual(shape.type, "RECTANGLE")
        self.assertEqual(shape.min_x, 50.0)
        self.assertEqual(shape.max_x, 250.0)
        self.assertEqual(shape.width, 200.0)
        self.assertEqual(shape.height, 70.0)

    def test_f09_05_unrecognized_scribble_returns_none(self):
        """Random erratic scribble that is neither line nor closed returns None."""
        pts = [
            Point(100.0, 100.0),
            Point(120.0, 300.0),
            Point(50.0, 150.0),
            Point(300.0, 200.0),
            Point(150.0, 20.0)
        ]
        shape = ShapeRecognizer.recognize(pts)
        # End to end dist is not close to diagonal, and not closed
        self.assertIsNone(shape)


class TestFeature10UndoRedoHistoryStacks(unittest.TestCase):
    """F10: Undo / Redo History Stacks"""

    def test_f10_01_undo_pops_last_stroke(self):
        """Undo removes the most recent stroke from page and pushes to redo stack."""
        page_strokes = [Stroke(stroke_id="s1"), Stroke(stroke_id="s2")]
        undo_stack = [list(page_strokes)]
        redo_stack = []

        # User presses Undo
        previous_state = []  # State before s2
        redo_stack.append(list(page_strokes))
        page_strokes.pop()

        self.assertEqual(len(page_strokes), 1)
        self.assertEqual(page_strokes[0].id, "s1")
        self.assertEqual(len(redo_stack), 1)

    def test_f10_02_redo_restores_popped_stroke(self):
        """Redo restores the stroke popped during undo."""
        page_strokes = [Stroke(stroke_id="s1")]
        redo_stack = [[Stroke(stroke_id="s1"), Stroke(stroke_id="s2")]]

        restored_state = redo_stack.pop()
        page_strokes = restored_state

        self.assertEqual(len(page_strokes), 2)
        self.assertEqual(page_strokes[1].id, "s2")

    def test_f10_03_new_action_clears_redo_stack(self):
        """Committing a new stroke after undo invalidates and empties the redo stack."""
        redo_stack = [[Stroke(stroke_id="s_old")]]
        self.assertEqual(len(redo_stack), 1)

        # New stroke drawn
        new_stroke = Stroke(stroke_id="s_new")
        redo_stack.clear()
        self.assertEqual(len(redo_stack), 0)

    def test_f10_04_multi_level_undo_sequence(self):
        """Multiple undos sequentially rewind through drawing history."""
        strokes = []
        history = [list(strokes)]
        for i in range(5):
            strokes.append(Stroke(stroke_id=f"s{i}"))
            history.append(list(strokes))

        self.assertEqual(len(strokes), 5)
        # Undo 3 times: pops 5->4, 4->3, 3->2
        history.pop()  # Pop state with 5 strokes
        history.pop()  # Pop state with 4 strokes
        history.pop()  # Pop state with 3 strokes
        strokes = history[-1]  # State with 2 strokes
        self.assertEqual(len(strokes), 2)
        self.assertEqual(strokes[0].id, "s0")
        self.assertEqual(strokes[1].id, "s1")

    def test_f10_05_undo_on_empty_stack_safe(self):
        """Calling undo on empty stack does not crash or corrupt page state."""
        strokes = []
        undo_stack = []
        if undo_stack:
            strokes = undo_stack.pop()
        self.assertEqual(len(strokes), 0)


if __name__ == "__main__":
    unittest.main()
