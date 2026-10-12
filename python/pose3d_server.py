"""
VitalFi — Pose3D WebSocket + HTTP Server

Serves pose estimation results in real-time via:
  - WebSocket (ws://localhost:8765): Continuous stream of pose results
  - HTTP REST API (http://localhost:8080): Polling and configuration

Designed to feed the Three.js 3D dashboard for skeleton visualization.

Usage:
    python pose3d_server.py [--model path/to/model.pt] [--port 8080] [--ws-port 8765]
"""

from __future__ import annotations

import json
import time
import asyncio
import argparse
import threading
from pathlib import Path

import numpy as np

# Conditional imports for server components
try:
    import websockets
    HAS_WEBSOCKETS = True
except ImportError:
    HAS_WEBSOCKETS = False

try:
    from aiohttp import web
    HAS_AIOHTTP = True
except ImportError:
    HAS_AIOHTTP = False

from pose3d_model import COCO_KEYPOINTS, COCO_SKELETON, POSTURE_CLASSES
from pose3d_pipeline import Pose3DPipeline, PoseResult, FusedResult


class NumpyEncoder(json.JSONEncoder):
    """JSON encoder that handles numpy arrays."""
    def default(self, obj):
        if isinstance(obj, np.ndarray):
            return obj.tolist()
        if isinstance(obj, np.floating):
            return float(obj)
        if isinstance(obj, np.integer):
            return int(obj)
        return super().default(obj)


def pose_result_to_json(result: PoseResult | None) -> dict:
    """Convert a PoseResult to a JSON-serializable dictionary."""
    if result is None:
        return {"status": "waiting", "message": "No data yet"}

    return {
        "status": "ok",
        "timestamp": result.timestamp,
        "keypoints_3d": result.keypoints_3d.tolist(),
        "keypoint_names": COCO_KEYPOINTS,
        "skeleton": COCO_SKELETON,
        "confidence": result.confidence.tolist(),
        "posture": result.posture,
        "posture_confidence": result.posture_confidence,
        "active_nodes": result.active_nodes,
        "inference_time_ms": round(result.inference_time_ms, 1),
        "total_frames": result.total_frames_used,
    }


def fused_result_to_json(result: FusedResult | None) -> dict:
    """Convert a FusedResult to a JSON-serializable dictionary."""
    if result is None:
        return {"status": "waiting"}

    data = pose_result_to_json(result.pose)
    data.update({
        "is_breathing": result.is_breathing,
        "respiratory_rate": round(result.respiratory_rate, 1),
        "heart_rate": round(result.heart_rate, 1),
        "vital_confidence": round(result.vital_confidence, 2),
        "description": result.description,
    })
    return data


class Pose3DServer:
    """Combined WebSocket + HTTP server for Pose3D results."""

    def __init__(
        self,
        pipeline: Pose3DPipeline,
        http_port: int = 8080,
        ws_port: int = 8765,
    ):
        self.pipeline = pipeline
        self.http_port = http_port
        self.ws_port = ws_port
        self._ws_clients: set = set()
        self._running = False

        # Register pipeline callback
        self.pipeline.on_result(self._on_new_result)

    def _on_new_result(self, result: PoseResult) -> None:
        """Callback from pipeline — broadcast to all WebSocket clients."""
        if not self._ws_clients:
            return
        data = json.dumps(pose_result_to_json(result), cls=NumpyEncoder)
        # Schedule async broadcast
        loop = getattr(self, '_ws_loop', None)
        if loop and loop.is_running():
            asyncio.run_coroutine_threadsafe(
                self._broadcast_ws(data), loop
            )

    async def _broadcast_ws(self, message: str) -> None:
        """Send message to all connected WebSocket clients."""
        if not self._ws_clients:
            return
        disconnected = set()
        for ws in self._ws_clients:
            try:
                await ws.send(message)
            except Exception:
                disconnected.add(ws)
        self._ws_clients -= disconnected

    # --- WebSocket Handler ---
    async def _ws_handler(self, websocket, path=None) -> None:
        """Handle a WebSocket connection."""
        self._ws_clients.add(websocket)
        client_addr = websocket.remote_address
        print(f"[WS] Client connected: {client_addr}")

        try:
            # Send initial state
            result = self.pipeline.get_latest()
            await websocket.send(
                json.dumps(pose_result_to_json(result), cls=NumpyEncoder)
            )

            # Keep connection alive, handle incoming messages
            async for message in websocket:
                try:
                    cmd = json.loads(message)
                    if cmd.get("type") == "get_latest":
                        result = self.pipeline.get_latest()
                        await websocket.send(
                            json.dumps(pose_result_to_json(result), cls=NumpyEncoder)
                        )
                except json.JSONDecodeError:
                    pass
        except Exception:
            pass
        finally:
            self._ws_clients.discard(websocket)
            print(f"[WS] Client disconnected: {client_addr}")

    # --- HTTP API Handlers ---
    async def _handle_pose3d_latest(self, request: web.Request) -> web.Response:
        """GET /api/pose3d/latest — Return latest pose estimation."""
        result = self.pipeline.get_latest()
        data = pose_result_to_json(result)
        return web.json_response(data, dumps=lambda x: json.dumps(x, cls=NumpyEncoder))

    async def _handle_pose3d_fused(self, request: web.Request) -> web.Response:
        """GET /api/pose3d/fused — Return latest fused result."""
        result = self.pipeline.get_latest_fused()
        data = fused_result_to_json(result)
        return web.json_response(data, dumps=lambda x: json.dumps(x, cls=NumpyEncoder))

    async def _handle_pose3d_config(self, request: web.Request) -> web.Response:
        """GET /api/pose3d/config — Return current configuration."""
        config = {
            "window_duration_ms": self.pipeline.window_duration_ms,
            "inference_interval_ms": self.pipeline.inference_interval_ms,
            "num_subcarriers": self.pipeline.num_subcarriers,
            "time_steps": self.pipeline.time_steps,
            "active_nodes": self.pipeline.receiver.get_active_nodes(),
            "receiver_stats": self.pipeline.receiver.stats,
        }
        return web.json_response(config)

    async def _handle_pose3d_nodes(self, request: web.Request) -> web.Response:
        """GET /api/pose3d/nodes — Return active ESP32 nodes."""
        nodes = self.pipeline.receiver.get_active_nodes()
        return web.json_response({
            "nodes": nodes,
            "count": len(nodes),
        })

    async def _handle_index(self, request: web.Request) -> web.Response:
        """GET / — Redirect to dashboard or serve info."""
        return web.json_response({
            "service": "VitalFi Pose3D Server",
            "version": "2.1.0",
            "endpoints": {
                "ws": f"ws://localhost:{self.ws_port}",
                "latest": f"http://localhost:{self.http_port}/api/pose3d/latest",
                "fused": f"http://localhost:{self.http_port}/api/pose3d/fused",
                "config": f"http://localhost:{self.http_port}/api/pose3d/config",
                "nodes": f"http://localhost:{self.http_port}/api/pose3d/nodes",
                "dashboard": f"http://localhost:{self.http_port}/dashboard",
            }
        })

    async def _handle_dashboard(self, request: web.Request) -> web.Response:
        """GET /dashboard — Serve the 3D visualization dashboard HTML."""
        dashboard_path = Path(__file__).parent / "pose3d_dashboard_static.html"
        if dashboard_path.exists():
            return web.FileResponse(dashboard_path)
        return web.Response(
            text="Dashboard file not found. Run pose3d_dashboard.py to generate it.",
            status=404,
        )

    # --- CORS Middleware ---
    @web.middleware
    async def _cors_middleware(self, request: web.Request, handler) -> web.Response:
        response = await handler(request)
        response.headers["Access-Control-Allow-Origin"] = "*"
        response.headers["Access-Control-Allow-Methods"] = "GET, POST, OPTIONS"
        response.headers["Access-Control-Allow-Headers"] = "Content-Type"
        return response

    # --- Server Start ---
    def start(self) -> None:
        """Start both WebSocket and HTTP servers."""
        self._running = True

        # Start WebSocket server in a separate thread
        ws_thread = threading.Thread(target=self._run_ws_server, daemon=True)
        ws_thread.start()

        # Start HTTP server (blocks)
        print(f"[HTTP] Starting Pose3D API server on port {self.http_port}")
        self._run_http_server()

    def _run_ws_server(self) -> None:
        """Run WebSocket server in its own event loop."""
        if not HAS_WEBSOCKETS:
            print("[WS] websockets package not installed. WebSocket server disabled.")
            return

        self._ws_loop = asyncio.new_event_loop()
        asyncio.set_event_loop(self._ws_loop)

        start_server = websockets.serve(self._ws_handler, "0.0.0.0", self.ws_port)
        self._ws_loop.run_until_complete(start_server)
        print(f"[WS] WebSocket server running on ws://0.0.0.0:{self.ws_port}")
        self._ws_loop.run_forever()

    def _run_http_server(self) -> None:
        """Run HTTP API server."""
        if not HAS_AIOHTTP:
            print("[HTTP] aiohttp package not installed. HTTP server disabled.")
            print("[HTTP] Install: pip install aiohttp")
            # Fallback: just keep the process alive
            try:
                while self._running:
                    time.sleep(1)
            except KeyboardInterrupt:
                pass
            return

        app = web.Application(middlewares=[self._cors_middleware])
        app.router.add_get("/", self._handle_index)
        app.router.add_get("/api/pose3d/latest", self._handle_pose3d_latest)
        app.router.add_get("/api/pose3d/fused", self._handle_pose3d_fused)
        app.router.add_get("/api/pose3d/config", self._handle_pose3d_config)
        app.router.add_get("/api/pose3d/nodes", self._handle_pose3d_nodes)
        app.router.add_get("/dashboard", self._handle_dashboard)

        web.run_app(app, host="0.0.0.0", port=self.http_port, print=None)


def main():
    parser = argparse.ArgumentParser(description="VitalFi Pose3D Server")
    parser.add_argument("--model", type=str, default=None,
                        help="Path to pretrained model (.pt)")
    parser.add_argument("--port", type=int, default=8080,
                        help="HTTP API port (default: 8080)")
    parser.add_argument("--ws-port", type=int, default=8765,
                        help="WebSocket port (default: 8765)")
    parser.add_argument("--receiver-port", type=int, default=5502,
                        help="UDP receiver port for ESP32 CSI (default: 5502)")
    parser.add_argument("--window-ms", type=int, default=300,
                        help="CSI window duration in ms (default: 300)")
    parser.add_argument("--interval-ms", type=int, default=100,
                        help="Inference interval in ms (default: 100)")
    args = parser.parse_args()

    print("=" * 60)
    print("  VitalFi — Person-in-WiFi 3D Pose Estimation Server")
    print("=" * 60)

    # Create pipeline
    pipeline = Pose3DPipeline(
        model_path=args.model,
        receiver_port=args.receiver_port,
        window_duration_ms=args.window_ms,
        inference_interval_ms=args.interval_ms,
    )
    pipeline.start()

    # Create and start server
    server = Pose3DServer(
        pipeline=pipeline,
        http_port=args.port,
        ws_port=args.ws_port,
    )

    print(f"\n  HTTP API:   http://localhost:{args.port}")
    print(f"  WebSocket:  ws://localhost:{args.ws_port}")
    print(f"  Dashboard:  http://localhost:{args.port}/dashboard")
    print(f"  UDP Listen: port {args.receiver_port}")
    print()

    try:
        server.start()
    except KeyboardInterrupt:
        print("\nShutting down...")
        pipeline.stop()


if __name__ == "__main__":
    main()
