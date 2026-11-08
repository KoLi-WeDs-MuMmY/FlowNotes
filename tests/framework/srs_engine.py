"""
Spaced Repetition (SRS) Engine Oracle.
Faithfully mirrors SrsEngineContract and SuperMemo SM-2 Spaced Repetition implementation.
"""

import time
from typing import List, NamedTuple, Optional
from tests.framework.models import Flashcard, CardRating, CardState


class SrsReviewResult(NamedTuple):
    new_interval_days: int
    new_repetitions: int
    new_ease_factor: float
    new_due_date: int
    new_state: CardState


class SrsEngine:

    @staticmethod
    def calculate_next_review(card: Flashcard, rating: CardRating, current_time_ms: Optional[int] = None) -> SrsReviewResult:
        """
        Calculates interval, repetition count, and ease factor based on SuperMemo SM-2.
        Rating:
          AGAIN (1): failed recall -> repetitions = 0, interval = 1, state = RELEARNING
          HARD (2): difficult -> q = 3
          GOOD (3): correct -> q = 4
          EASY (4): perfect -> q = 5
        """
        now = int(current_time_ms if current_time_ms is not None else time.time() * 1000)
        ms_per_day = 86400 * 1000

        # Map CardRating (1..4) to standard SM-2 quality q (1, 3, 4, 5)
        if rating == CardRating.AGAIN:
            q = 1
        elif rating == CardRating.HARD:
            q = 3
        elif rating == CardRating.GOOD:
            q = 4
        elif rating == CardRating.EASY:
            q = 5
        else:
            q = 3

        # Update Ease Factor: EF' = EF + (0.1 - (5-q) * (0.08 + (5-q) * 0.02))
        delta = 0.1 - (5 - q) * (0.08 + (5 - q) * 0.02)
        new_ef = max(1.30, card.ease_factor + delta)

        if rating == CardRating.AGAIN:
            new_repetitions = 0
            new_interval = 1
            new_state = CardState.RELEARNING
        else:
            if card.repetitions == 0:
                new_interval = 1
            elif card.repetitions == 1:
                new_interval = 6
            else:
                new_interval = max(1, int(round(card.review_interval_days * new_ef)))

            # Bonus day for EASY on initial reviews
            if rating == CardRating.EASY and card.repetitions == 0:
                new_interval = 2

            new_repetitions = card.repetitions + 1
            new_state = CardState.REVIEW

        new_due_date = now + (new_interval * ms_per_day)

        return SrsReviewResult(
            new_interval_days=new_interval,
            new_repetitions=new_repetitions,
            new_ease_factor=round(new_ef, 3),
            new_due_date=new_due_date,
            new_state=new_state
        )

    @staticmethod
    def apply_review(card: Flashcard, rating: CardRating, current_time_ms: Optional[int] = None) -> Flashcard:
        res = SrsEngine.calculate_next_review(card, rating, current_time_ms)
        card.review_interval_days = res.new_interval_days
        card.repetitions = res.new_repetitions
        card.ease_factor = res.new_ease_factor
        card.due_date = res.new_due_date
        card.state = res.new_state
        return card

    @staticmethod
    def get_due_cards(cards: List[Flashcard], current_time_ms: Optional[int] = None) -> List[Flashcard]:
        now = int(current_time_ms if current_time_ms is not None else time.time() * 1000)
        return [c for c in cards if c.due_date <= now]
