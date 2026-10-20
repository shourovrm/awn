#!/usr/bin/env python3
"""Generate review sequences with py-fsrs so the Kotlin port can be checked against it.

Usage (py-fsrs lives in a virtualenv, never system-wide):
    python3 -m venv /tmp/awn-fsrs && /tmp/awn-fsrs/bin/pip install fsrs
    /tmp/awn-fsrs/bin/python tools/generate_fsrs_vectors.py

Output: feature/learn/src/test/resources/fsrs_vectors.json. Fuzzing is off, so every number is
deterministic. The random generator is seeded, so a rerun with the same py-fsrs gives the same file.
The py-fsrs git commit is the commit of the release tag v<version> on GitHub, read with
`git ls-remote`; pass --git-commit to set it when offline.
"""

import argparse
import json
import random
import subprocess
from datetime import datetime, timedelta, timezone
from importlib.metadata import version
from pathlib import Path

from fsrs import Card, Rating, Scheduler

SEQUENCE_COUNT = 200
# The app runs FSRS without minute-long learning steps (see LearnProgress), so a second set of
# sequences covers that configuration.
NO_STEPS_SEQUENCE_COUNT = 50
RANDOM_SEED = 1409703933
FIRST_REVIEW = datetime(2026, 1, 1, 6, 30, tzinfo=timezone.utc)
OUTPUT = (
    Path(__file__).resolve().parent.parent
    / "feature/learn/src/test/resources/fsrs_vectors.json"
)

# Good is the most common rating in real use; Again and Hard are rarer.
RATING_WEIGHTS = {Rating.Again: 2, Rating.Hard: 2, Rating.Good: 5, Rating.Easy: 2}


def lookup_git_commit(package_version: str) -> str:
    tag = f"refs/tags/v{package_version}"
    output = subprocess.run(
        ["git", "ls-remote", "https://github.com/open-spaced-repetition/py-fsrs", tag],
        capture_output=True,
        text=True,
        check=True,
    ).stdout.split()
    if not output:
        raise SystemExit(f"tag {tag} not found; pass --git-commit")
    return output[0]


def next_review_time(rng: random.Random, card: Card, now: datetime) -> datetime:
    """Pick when the next review happens: on time, late, early, or minutes later."""
    choice = rng.choice(["on_time", "on_time", "late", "very_late", "early", "same_day"])
    if choice == "on_time":
        return card.due
    if choice == "late":
        return card.due + timedelta(days=rng.randint(1, 10), minutes=rng.randint(0, 600))
    if choice == "very_late":
        return card.due + timedelta(days=rng.randint(30, 400))
    if choice == "early":
        early = card.due - timedelta(days=rng.randint(1, 20))
        return max(early, now + timedelta(days=1))
    return now + timedelta(minutes=rng.choice([1, 5, 11, 45, 180, 600]))


def build_sequence(rng: random.Random, scheduler: Scheduler, sequence_id: int) -> dict:
    card = Card(card_id=sequence_id, due=FIRST_REVIEW)
    now = FIRST_REVIEW
    ratings = list(RATING_WEIGHTS)
    weights = list(RATING_WEIGHTS.values())
    reviews = []
    for review_index in range(rng.randint(3, 14)):
        if review_index > 0:
            now = next_review_time(rng, card, now)
        rating = rng.choices(ratings, weights)[0]
        card, _ = scheduler.review_card(card, rating, now)
        reviews.append(
            {
                "rating": int(rating),
                "review_at": int(now.timestamp()),
                "state": int(card.state),
                "step": card.step,
                "stability": card.stability,
                "difficulty": card.difficulty,
                "due_at": int(card.due.timestamp()),
                "interval_days": (card.due - now).days,
            }
        )
    return {"id": sequence_id, "reviews": reviews}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--git-commit", help="py-fsrs commit; looked up from GitHub when omitted")
    arguments = parser.parse_args()

    package_version = version("fsrs")
    scheduler = Scheduler(enable_fuzzing=False)
    no_steps_scheduler = Scheduler(
        learning_steps=(), relearning_steps=(), enable_fuzzing=False
    )
    rng = random.Random(RANDOM_SEED)
    sequences = [build_sequence(rng, scheduler, number) for number in range(1, SEQUENCE_COUNT + 1)]
    no_steps_sequences = [
        build_sequence(rng, no_steps_scheduler, number)
        for number in range(1, NO_STEPS_SEQUENCE_COUNT + 1)
    ]

    document = {
        "py_fsrs_version": package_version,
        "py_fsrs_git_commit": arguments.git_commit or lookup_git_commit(package_version),
        "random_seed": RANDOM_SEED,
        "parameters": list(scheduler.parameters),
        "desired_retention": scheduler.desired_retention,
        "learning_steps_seconds": [int(s.total_seconds()) for s in scheduler.learning_steps],
        "relearning_steps_seconds": [int(s.total_seconds()) for s in scheduler.relearning_steps],
        "maximum_interval_days": scheduler.maximum_interval,
        "enable_fuzzing": scheduler.enable_fuzzing,
        "sequences": sequences,
        "no_steps_sequences": no_steps_sequences,
    }
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(json.dumps(document, indent=1) + "\n", encoding="utf-8")
    review_count = sum(len(sequence["reviews"]) for sequence in sequences + no_steps_sequences)
    sequence_count = len(sequences) + len(no_steps_sequences)
    print(f"wrote {OUTPUT} ({sequence_count} sequences, {review_count} reviews)")


if __name__ == "__main__":
    main()
