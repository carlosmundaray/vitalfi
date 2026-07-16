"""Unit tests for Wi-Fi vital-signal detection pipeline."""

from __future__ import annotations

import sys
from pathlib import Path

import numpy as np
import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from csi_processor import hampel_filter
from detection import RubbleConfidenceTracker, analyze_vital_signal
from vital_features import estimate_dominant_frequency, temporal_variance


def test_hampel_removes_outlier():
    x = np.array([1.0, 1.0, 1.0, 40.0, 1.0, 1.0, 1.0], dtype=np.float64)
    y = hampel_filter(x, window_size=2, threshold=3.0)
    assert y[3] < 40.0
    assert abs(y[3] - 1.0) < 5.0


def test_estimate_dominant_frequency_sine():
    fs = 10.0
    f0 = 0.25
    t = np.arange(0, 25.6, 1.0 / fs)
    signal = np.sin(2 * np.pi * f0 * t)
    freq, mag = estimate_dominant_frequency(signal, fs, (0.1, 0.5))
    assert mag > 0
    assert abs(freq - f0) < 0.05


def test_temporal_variance_constant_is_zero():
    assert temporal_variance(np.ones(50)) == pytest.approx(0.0, abs=1e-12)


def test_analyze_collecting_when_short():
    result = analyze_vital_signal(np.zeros(10), fs=2.0)
    assert result.is_breathing is False
    assert "Recolectando" in result.status


def test_analyze_flat_signal_no_breathing():
    signal = np.full(80, -55.0)
    result = analyze_vital_signal(signal, fs=2.0, sensitivity=2.0, rubble_mode=True)
    assert result.is_breathing is False


def test_analyze_periodic_modulation_detects_activity_or_breathing():
    fs = 2.0
    f0 = 0.25
    t = np.arange(0, 60) / fs
    # Stronger amplitude so coarse RSSI-like pipeline can see it
    signal = -60.0 + 8.0 * np.sin(2 * np.pi * f0 * t)
    result = analyze_vital_signal(signal, fs=fs, sensitivity=3.0, rubble_mode=True)
    assert result.is_activity or result.is_breathing or result.periodicity > 0.15


def test_rubble_tracker_accumulates_after_calibration():
    tracker = RubbleConfidenceTracker(calib_seconds=1.0)
    now = 0.0
    tracker.begin(now)
    from detection import DetectionResult

    breathing = DetectionResult(
        is_breathing=True,
        is_activity=True,
        resp_freq=0.25,
        resp_rate=15.0,
        var=0.5,
        snr=2.0,
        noise_floor=0.1,
        unique_values=5,
        signal_flat=False,
        status="ok",
        hint="",
        confidence=0.8,
        periodicity=0.5,
        spectral_energy=0.2,
    )
    # Still calibrating — score should stay low
    tracker.update(breathing, now + 0.5)
    assert tracker.calibrating is True
    # Past calibration
    for i in range(12):
        tracker.update(breathing, now + 1.5 + i * 0.5)
    assert tracker.calibrating is False
    assert tracker.score > 0.2
    assert tracker.likely_present(0.2)
