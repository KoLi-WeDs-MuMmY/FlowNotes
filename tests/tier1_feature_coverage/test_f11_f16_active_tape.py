"""
Tier 1: Feature Coverage (Features 11 - 16)
- F11: Active Recall Tape Creation
- F12: Tap-to-Reveal / Conceal
- F13: Visual Concealed State
- F14: Visual Revealed State
- F15: Quick Action: Reveal All
- F16: Quick Action: Conceal All
Total Tests: 30 (5 tests per feature)
"""

import unittest
from tests.framework.models import ActiveRecallTape, Page
from tests.framework.tape_engine import ActiveRecallTapeEngine


class TestFeature11ActiveRecallTapeCreation(unittest.TestCase):
    """F11: Active Recall Tape Creation"""

    def test_f11_01_drag_coordinate_normalization(self):
        """Tape creation normalizes inverted drag coordinates (min/max)."""
        # User dragged from bottom-right (400, 250) to top-left (100, 200)
        tape = ActiveRecallTapeEngine.create_tape(400.0, 250.0, 100.0, 200.0)
        self.assertEqual(tape.left, 100.0)
        self.assertEqual(tape.top, 200.0)
        self.assertEqual(tape.right, 400.0)
        self.assertEqual(tape.bottom, 250.0)
        self.assertEqual(tape.width, 300.0)
        self.assertEqual(tape.height, 50.0)

    def test_f11_02_default_soft_pastel_yellow_color(self):
        """Tape defaults to standard Freenotes pastel yellow (0xFFFEF08A)."""
        tape = ActiveRecallTapeEngine.create_tape(50.0, 50.0, 150.0, 80.0)
        self.assertEqual(tape.color, 0xFFFEF08A)

    def test_f11_03_initial_state_concealed(self):
        """Newly created tape is initially in the concealed state (is_revealed = False)."""
        tape = ActiveRecallTapeEngine.create_tape(10.0, 10.0, 100.0, 40.0)
        self.assertFalse(tape.is_revealed)

    def test_f11_04_unique_tape_id_generation(self):
        """Each created tape possesses a globally unique UUID."""
        t1 = ActiveRecallTapeEngine.create_tape(10.0, 10.0, 50.0, 30.0)
        t2 = ActiveRecallTapeEngine.create_tape(10.0, 10.0, 50.0, 30.0)
        self.assertNotEqual(t1.id, t2.id)

    def test_f11_05_page_tape_collection_addition(self):
        """Created tape is appended to the active page tapes list."""
        page = Page(page_number=1)
        tape = ActiveRecallTapeEngine.create_tape(100.0, 100.0, 300.0, 140.0)
        page.tapes.append(tape)
        self.assertEqual(len(page.tapes), 1)
        self.assertEqual(page.tapes[0].width, 200.0)


class TestFeature12TapToRevealConceal(unittest.TestCase):
    """F12: Tap-to-Reveal / Conceal"""

    def test_f12_01_tap_within_bounds_toggles_to_revealed(self):
        """Tapping inside concealed tape switches state to revealed."""
        tape = ActiveRecallTape(left=100.0, top=100.0, right=200.0, bottom=140.0, is_revealed=False)
        tapes = [tape]
        hit = ActiveRecallTapeEngine.toggle_tape_at(tapes, 150.0, 120.0)
        self.assertIsNotNone(hit)
        self.assertTrue(tape.is_revealed)

    def test_f12_02_second_tap_toggles_back_to_concealed(self):
        """Tapping already revealed tape switches it back to concealed."""
        tape = ActiveRecallTape(left=100.0, top=100.0, right=200.0, bottom=140.0, is_revealed=True)
        tapes = [tape]
        ActiveRecallTapeEngine.toggle_tape_at(tapes, 150.0, 120.0)
        self.assertFalse(tape.is_revealed)

    def test_f12_03_tap_outside_bounds_does_not_toggle(self):
        """Tapping outside tape bounds leaves tape state unchanged."""
        tape = ActiveRecallTape(left=100.0, top=100.0, right=200.0, bottom=140.0, is_revealed=False)
        tapes = [tape]
        hit = ActiveRecallTapeEngine.toggle_tape_at(tapes, 250.0, 120.0)
        self.assertIsNone(hit)
        self.assertFalse(tape.is_revealed)

    def test_f12_04_top_most_tape_priority_on_overlap(self):
        """When two tapes overlap, tapping targets the top-most (most recently added) tape."""
        t1 = ActiveRecallTape(left=50.0, top=50.0, right=150.0, bottom=100.0, is_revealed=False, tape_id="bottom")
        t2 = ActiveRecallTape(left=80.0, top=60.0, right=180.0, bottom=110.0, is_revealed=False, tape_id="top")
        tapes = [t1, t2]
        # Tap at (100, 80) which is inside both t1 and t2
        hit = ActiveRecallTapeEngine.toggle_tape_at(tapes, 100.0, 80.0)
        self.assertIsNotNone(hit)
        self.assertEqual(hit.id, "top")
        self.assertTrue(t2.is_revealed)
        self.assertFalse(t1.is_revealed)

    def test_f12_05_tape_corner_coordinate_hit_test(self):
        """Hit test includes exact corner boundaries (left, top, right, bottom)."""
        tape = ActiveRecallTape(left=10.0, top=20.0, right=100.0, bottom=60.0)
        self.assertTrue(tape.contains(10.0, 20.0))
        self.assertTrue(tape.contains(100.0, 60.0))
        self.assertTrue(tape.contains(10.0, 60.0))
        self.assertTrue(tape.contains(100.0, 20.0))


class TestFeature13VisualConcealedState(unittest.TestCase):
    """F13: Visual Concealed State"""

    def test_f13_01_opaque_alpha_level(self):
        """Concealed state has full opacity (alpha = 255) to block text completely."""
        tape = ActiveRecallTape(left=0.0, top=0.0, right=100.0, bottom=50.0, is_revealed=False)
        state = ActiveRecallTapeEngine.get_render_state(tape)
        self.assertEqual(state["alpha"], 255)
        self.assertTrue(state["is_opaque"])

    def test_f13_02_no_dashed_border_when_concealed(self):
        """Concealed state renders solid rectangle without dashed boundary."""
        tape = ActiveRecallTape(left=0.0, top=0.0, right=100.0, bottom=50.0, is_revealed=False)
        state = ActiveRecallTapeEngine.get_render_state(tape)
        self.assertFalse(state["has_dashed_border"])
        self.assertIsNone(state["border_color"])

    def test_f13_03_text_visibility_blocked(self):
        """Underlying note text is flagged as hidden/occluded."""
        tape = ActiveRecallTape(left=0.0, top=0.0, right=100.0, bottom=50.0, is_revealed=False)
        state = ActiveRecallTapeEngine.get_render_state(tape)
        self.assertFalse(state["text_visible"])

    def test_f13_04_pastel_color_integrity(self):
        """Concealed tape maintains assigned pastel hue."""
        tape = ActiveRecallTape(left=0.0, top=0.0, right=100.0, bottom=50.0, color=0xFFFBCFE8)
        self.assertEqual(tape.color, 0xFFFBCFE8)

    def test_f13_05_serialization_preserves_concealed_state(self):
        """Concealed tape serializes isRevealed: false."""
        tape = ActiveRecallTape(left=10.0, top=10.0, right=50.0, bottom=30.0, is_revealed=False)
        d = tape.to_dict()
        self.assertFalse(d["isRevealed"])
        restored = ActiveRecallTape.from_dict(d)
        self.assertFalse(restored.is_revealed)


class TestFeature14VisualRevealedState(unittest.TestCase):
    """F14: Visual Revealed State"""

    def test_f14_01_translucent_alpha_level(self):
        """Revealed state renders subtle translucent fill (alpha = 40) for text legibility."""
        tape = ActiveRecallTape(left=0.0, top=0.0, right=100.0, bottom=50.0, is_revealed=True)
        state = ActiveRecallTapeEngine.get_render_state(tape)
        self.assertEqual(state["alpha"], 40)
        self.assertFalse(state["is_opaque"])

    def test_f14_02_dashed_border_activated(self):
        """Revealed state renders distinct dashed outline."""
        tape = ActiveRecallTape(left=0.0, top=0.0, right=100.0, bottom=50.0, is_revealed=True)
        state = ActiveRecallTapeEngine.get_render_state(tape)
        self.assertTrue(state["has_dashed_border"])
        self.assertEqual(state["dash_pattern"], (12.0, 8.0))

    def test_f14_03_border_color_matches_tape_color(self):
        """Dashed border uses matching tape color."""
        tape = ActiveRecallTape(left=0.0, top=0.0, right=100.0, bottom=50.0, color=0xFFBAE6FD, is_revealed=True)
        state = ActiveRecallTapeEngine.get_render_state(tape)
        self.assertEqual(state["border_color"], 0xFFBAE6FD)

    def test_f14_04_underlying_text_visible(self):
        """Revealed state allows full readability of underlying notes and equations."""
        tape = ActiveRecallTape(left=0.0, top=0.0, right=100.0, bottom=50.0, is_revealed=True)
        state = ActiveRecallTapeEngine.get_render_state(tape)
        self.assertTrue(state["text_visible"])

    def test_f14_05_serialization_preserves_revealed_state(self):
        """Revealed tape serializes isRevealed: true."""
        tape = ActiveRecallTape(left=10.0, top=10.0, right=50.0, bottom=30.0, is_revealed=True)
        d = tape.to_dict()
        self.assertTrue(d["isRevealed"])
        restored = ActiveRecallTape.from_dict(d)
        self.assertTrue(restored.is_revealed)


class TestFeature15QuickActionRevealAll(unittest.TestCase):
    """F15: Quick Action: Reveal All"""

    def test_f15_01_bulk_reveal_all_tapes(self):
        """Reveal All sets is_revealed = True for every tape on page."""
        tapes = [
            ActiveRecallTape(0, 0, 10, 10, is_revealed=False),
            ActiveRecallTape(20, 20, 30, 30, is_revealed=False),
            ActiveRecallTape(40, 40, 50, 50, is_revealed=False),
        ]
        count = ActiveRecallTapeEngine.set_all_tapes_revealed(tapes, revealed=True)
        self.assertEqual(count, 3)
        for t in tapes:
            self.assertTrue(t.is_revealed)

    def test_f15_02_idempotent_repeated_calls(self):
        """Calling Reveal All multiple times maintains all tapes in revealed state."""
        tapes = [ActiveRecallTape(0, 0, 10, 10, is_revealed=True)]
        count = ActiveRecallTapeEngine.set_all_tapes_revealed(tapes, revealed=True)
        self.assertEqual(count, 0)
        self.assertTrue(tapes[0].is_revealed)

    def test_f15_03_mixed_initial_states(self):
        """Reveal All uncovers tapes that were previously partially revealed."""
        tapes = [
            ActiveRecallTape(0, 0, 10, 10, is_revealed=False),
            ActiveRecallTape(20, 20, 30, 30, is_revealed=True)
        ]
        ActiveRecallTapeEngine.set_all_tapes_revealed(tapes, revealed=True)
        self.assertTrue(tapes[0].is_revealed)
        self.assertTrue(tapes[1].is_revealed)

    def test_f15_04_empty_tape_list_safety(self):
        """Reveal All on page with 0 tapes executes cleanly without error."""
        tapes = []
        count = ActiveRecallTapeEngine.set_all_tapes_revealed(tapes, revealed=True)
        self.assertEqual(count, 0)

    def test_f15_05_visual_state_updates_to_revealed(self):
        """All tapes transition to revealed render state with dashed borders."""
        tapes = [ActiveRecallTape(0, 0, 10, 10, is_revealed=False)]
        ActiveRecallTapeEngine.set_all_tapes_revealed(tapes, revealed=True)
        state = ActiveRecallTapeEngine.get_render_state(tapes[0])
        self.assertEqual(state["alpha"], 40)
        self.assertTrue(state["has_dashed_border"])


class TestFeature16QuickActionConcealAll(unittest.TestCase):
    """F16: Quick Action: Conceal All"""

    def test_f16_01_bulk_conceal_all_tapes(self):
        """Conceal All sets is_revealed = False for every tape on page."""
        tapes = [
            ActiveRecallTape(0, 0, 10, 10, is_revealed=True),
            ActiveRecallTape(20, 20, 30, 30, is_revealed=True),
            ActiveRecallTape(40, 40, 50, 50, is_revealed=False),
        ]
        count = ActiveRecallTapeEngine.set_all_tapes_revealed(tapes, revealed=False)
        self.assertEqual(count, 2)
        for t in tapes:
            self.assertFalse(t.is_revealed)

    def test_f16_02_idempotent_repeated_calls(self):
        """Calling Conceal All repeatedly keeps all tapes concealed."""
        tapes = [ActiveRecallTape(0, 0, 10, 10, is_revealed=False)]
        count = ActiveRecallTapeEngine.set_all_tapes_revealed(tapes, revealed=False)
        self.assertEqual(count, 0)
        self.assertFalse(tapes[0].is_revealed)

    def test_f16_03_exam_revision_readiness(self):
        """Conceal All preps the page for rapid self-testing."""
        tapes = [ActiveRecallTape(float(i*20), 50.0, float(i*20+15), 70.0, is_revealed=True) for i in range(5)]
        ActiveRecallTapeEngine.set_all_tapes_revealed(tapes, revealed=False)
        self.assertTrue(all(not t.is_revealed for t in tapes))

    def test_f16_04_empty_tape_list_safety(self):
        """Conceal All on page with 0 tapes executes without exception."""
        tapes = []
        count = ActiveRecallTapeEngine.set_all_tapes_revealed(tapes, revealed=False)
        self.assertEqual(count, 0)

    def test_f16_05_visual_state_updates_to_opaque(self):
        """All tapes transition to solid opaque rendering blocking answers."""
        tapes = [ActiveRecallTape(0, 0, 10, 10, is_revealed=True)]
        ActiveRecallTapeEngine.set_all_tapes_revealed(tapes, revealed=False)
        state = ActiveRecallTapeEngine.get_render_state(tapes[0])
        self.assertEqual(state["alpha"], 255)
        self.assertFalse(state["has_dashed_border"])


if __name__ == "__main__":
    unittest.main()
