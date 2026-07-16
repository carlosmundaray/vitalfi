"""
VitalFi — CSI Signal Processor for Vital Signs Detection

Preprocessing pipeline:
1. Hampel filter (outlier removal)
2. Butterworth bandpass filter (isolate respiratory / cardiac bands)
3. Linear phase sanitization
4. PCA for subcarrier dimensionality reduction
"""

import numpy as np
from scipy.signal import butter, filtfilt, find_peaks
from scipy.fft import fft, fftfreq


# ---------------------------------------------------------------------------
# Hampel filter (same technique as WhoFi paper §3.1.2)
# ---------------------------------------------------------------------------

def hampel_filter(signal: np.ndarray, window_size: int = 5, threshold: float = 3.0) -> np.ndarray:
    """Remove outliers from a 1-D signal using the Hampel identifier.

    Args:
        signal: 1-D array of amplitude values.
        window_size: Half-window size for the sliding median.
        threshold: Number of MADs to flag as outlier (ξ in the paper).

    Returns:
        Cleaned signal with outliers replaced by the local median.
    """
    filtered = signal.copy()
    n = len(signal)
    k = 1.4826  # consistency constant for Gaussian distribution

    for i in range(n):
        lo = max(0, i - window_size)
        hi = min(n, i + window_size + 1)
        window = signal[lo:hi]
        median_val = np.median(window)
        mad = k * np.median(np.abs(window - median_val))

        # MAD=0 when the window is nearly constant: any distinct sample is an outlier.
        if mad <= 0:
            if np.abs(signal[i] - median_val) > 0:
                filtered[i] = median_val
        elif np.abs(signal[i] - median_val) > threshold * mad:
            filtered[i] = median_val

    return filtered


# ---------------------------------------------------------------------------
# Butterworth bandpass filter
# ---------------------------------------------------------------------------

def bandpass_filter(
    signal: np.ndarray,
    lowcut: float,
    highcut: float,
    fs: float,
    order: int = 4,
) -> np.ndarray:
    """Apply a zero-phase Butterworth bandpass filter.

    Args:
        signal: 1-D input signal.
        lowcut: Lower cutoff frequency in Hz.
        highcut: Upper cutoff frequency in Hz.
        fs: Sampling frequency in Hz.
        order: Filter order.

    Returns:
        Bandpass-filtered signal.
    """
    nyq = 0.5 * fs
    low = lowcut / nyq
    high = highcut / nyq
    # Clamp to valid range
    low = max(low, 1e-5)
    high = min(high, 1.0 - 1e-5)
    b, a = butter(order, [low, high], btype="band")
    return filtfilt(b, a, signal)


def respiratory_filter(signal: np.ndarray, fs: float) -> np.ndarray:
    """Isolate the respiratory band (0.1–0.5 Hz → 6–30 breaths/min)."""
    return bandpass_filter(signal, 0.1, 0.5, fs)


def cardiac_filter(signal: np.ndarray, fs: float) -> np.ndarray:
    """Isolate the cardiac band (0.8–2.0 Hz → 48–120 bpm)."""
    return bandpass_filter(signal, 0.8, 2.0, fs)


# ---------------------------------------------------------------------------
# Phase sanitization (WhoFi paper §3.1.3, Equations 8–11)
# ---------------------------------------------------------------------------

def sanitize_phase(phase: np.ndarray) -> np.ndarray:
    """Remove linear phase offset caused by hardware synchronization errors.

    Args:
        phase: 2-D array of shape (num_subcarriers, num_packets).

    Returns:
        Calibrated phase array of same shape.
    """
    K = phase.shape[0]
    m = np.arange(K)

    # Estimate slope and offset from raw phase (Eq. 9, 10)
    a = (phase[-1, :] - phase[0, :]) / max(K - 1, 1)
    b = np.mean(phase, axis=0)

    # Subtract linear component (Eq. 11)
    calibrated = phase - np.outer(m, a) - b[np.newaxis, :]
    return calibrated


# ---------------------------------------------------------------------------
# PCA-based subcarrier reduction
# ---------------------------------------------------------------------------

def pca_reduce(csi_matrix: np.ndarray, n_components: int = 5) -> np.ndarray:
    """Reduce subcarrier dimension using PCA.

    Args:
        csi_matrix: Shape (num_subcarriers, num_packets).
        n_components: Number of principal components to keep.

    Returns:
        Reduced matrix of shape (n_components, num_packets).
    """
    # Center the data
    mean = np.mean(csi_matrix, axis=1, keepdims=True)
    centered = csi_matrix - mean

    # Covariance and eigen-decomposition
    cov = np.cov(centered)
    eigenvalues, eigenvectors = np.linalg.eigh(cov)

    # Sort by descending eigenvalue
    idx = np.argsort(eigenvalues)[::-1]
    top_vecs = eigenvectors[:, idx[:n_components]]

    # Project
    return top_vecs.T @ centered


# ---------------------------------------------------------------------------
# Full preprocessing pipeline
# ---------------------------------------------------------------------------

def preprocess_csi(
    csi_amplitude: np.ndarray,
    fs: float = 100.0,
    hampel_window: int = 5,
    hampel_threshold: float = 3.0,
    pca_components: int = 5,
) -> dict:
    """Full CSI preprocessing pipeline.

    Args:
        csi_amplitude: Raw CSI amplitude, shape (num_subcarriers, num_packets).
        fs: Sampling frequency in Hz.
        hampel_window: Hampel filter half-window.
        hampel_threshold: Hampel filter threshold.
        pca_components: Number of PCA components.

    Returns:
        Dictionary with keys:
        - 'cleaned': Hampel-filtered amplitude
        - 'respiratory': Respiratory-band filtered (after PCA)
        - 'cardiac': Cardiac-band filtered (after PCA)
        - 'pca': PCA-reduced components
    """
    num_subcarriers, num_packets = csi_amplitude.shape

    # Step 1: Hampel filter per subcarrier
    cleaned = np.zeros_like(csi_amplitude)
    for i in range(num_subcarriers):
        cleaned[i] = hampel_filter(csi_amplitude[i], hampel_window, hampel_threshold)

    # Step 2: PCA reduction
    pca = pca_reduce(cleaned, n_components=pca_components)

    # Step 3: Bandpass filtering on each PCA component
    respiratory = np.zeros_like(pca)
    cardiac = np.zeros_like(pca)
    for i in range(pca_components):
        respiratory[i] = respiratory_filter(pca[i], fs)
        cardiac[i] = cardiac_filter(pca[i], fs)

    return {
        "cleaned": cleaned,
        "pca": pca,
        "respiratory": respiratory,
        "cardiac": cardiac,
    }
