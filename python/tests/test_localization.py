"""Unit tests for polar localization and multi-anchor helpers."""

from __future__ import annotations

import math
import sys
from pathlib import Path

import numpy as np
import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from localization import BearingAccumulator, MultiAnchorLocalizer, polar_to_xy


def test_polar_to_xy_north():
    x, y = polar_to_xy(2.0, 0.0)
    assert abs(x) < 1e-9
    assert abs(y - 2.0) < 1e-9


def test_polar_to_xy_east():
    x, y = polar_to_xy(3.0, 90.0)
    assert abs(x - 3.0) < 1e-9
    assert abs(y) < 1e-9


def test_bearing_accumulator_peaks_at_updated_sector():
    acc = BearingAccumulator(num_bins=36)
    for _ in range(10):
        acc.update(bearing_deg=45.0, distance_m=2.5, strength=1.0)
    est = acc.best_estimate(min_score=0.1)
    assert est is not None
    bearing, distance, score = est
    assert abs(bearing - 45.0) < 10.0
    assert 1.5 < distance < 3.5
    assert score > 0.1


def test_bearing_accumulator_empty_returns_none():
    acc = BearingAccumulator()
    assert acc.best_estimate() is None


def test_multi_anchor_sync_and_estimate():
    loc = MultiAnchorLocalizer(radius_m=3.0)
    loc.sync_anchors([("aa:bb:01", "A1"), ("aa:bb:02", "A2")])
    assert len(loc.anchors) == 2
    for bssid in loc.anchors:
        loc.update_detection(bssid, distance_m=1.5, strength=0.8, variance=0.5)
    est = loc.best_estimate(min_score=0.05)
    assert est is not None
    bearing, distance, score = est
    assert 0.0 <= bearing < 360.0
    assert distance >= 0.0
    assert score > 0.0
