"""
VitalFi — Radar 3D para localizar persona bajo escombros.

Vista espacial: laptop/antenas, histórico de detecciones y objetivo estimado.
"""

from __future__ import annotations

import math

import numpy as np


class Radar3DView:
    """Panel matplotlib 3D para navegación hacia la persona detectada."""

    def __init__(self, ax, range_m: float = 5.5) -> None:
        self.ax = ax
        self.range_m = range_m
        self.elev = 28.0
        self.azim = -55.0
        self._auto_rotate = True

        self._range_rings: list = []
        self._ground_lines: list = []
        self._beam_line = None
        self._trail_line = None
        self._dir_line = None
        self._nav_text = None

        # Artistas dinámicos (se recrean cada frame — evita bugs de Path3DCollection)
        self._laptop_scatter = None
        self._antenna_scatter = None
        self._heatmap_scatter = None
        self._target_scatter = None
        self._target_glow = None
        self._target_sphere_lines: list = []

        self._setup_scene()

    def _remove_artist(self, artist) -> None:
        if artist is not None:
            try:
                artist.remove()
            except (ValueError, AttributeError):
                pass

    def _scatter(
        self,
        xs: list[float] | np.ndarray,
        ys: list[float] | np.ndarray,
        zs: list[float] | np.ndarray,
        old_artist,
        **kwargs,
    ):
        """Recrea un scatter 3D (matplotlib no soporta bien set_sizes en 3D)."""
        self._remove_artist(old_artist)
        xs = np.asarray(xs, dtype=float)
        ys = np.asarray(ys, dtype=float)
        zs = np.asarray(zs, dtype=float)
        if xs.size == 0:
            return None
        return self.ax.scatter(xs, ys, zs, **kwargs)

    def _setup_scene(self) -> None:
        ax = self.ax
        ax.set_facecolor("#0a0f0a")
        ax.set_title("Radar 3D — Navegación hacia la víctima", color="white", fontsize=11, pad=8)
        ax.set_xlabel("X → Este (m)", color="#888888", fontsize=8)
        ax.set_ylabel("Y → Norte (m)", color="#888888", fontsize=8)
        ax.set_zlabel("Z ↑ Altura (m)", color="#888888", fontsize=8)
        ax.tick_params(colors="#666666", labelsize=7)

        r = self.range_m
        ax.set_xlim(-r, r)
        ax.set_ylim(-r, r)
        ax.set_zlim(-2.2, 2.5)

        for radius, color in [(1.5, "#1a3a1a"), (3.0, "#2a4a2a"), (5.0, "#3a5a3a")]:
            theta = np.linspace(0, 2 * np.pi, 64)
            xs = radius * np.cos(theta)
            ys = radius * np.sin(theta)
            zs = np.zeros_like(xs)
            line, = ax.plot(xs, ys, zs, color=color, alpha=0.55, linewidth=0.9, linestyle="--")
            self._range_rings.append(line)

        grid_n = int(r)
        for i in range(-grid_n, grid_n + 1):
            self._ground_lines.append(
                ax.plot([i, i], [-r, r], [0, 0], color="#1a2a1a", alpha=0.25, linewidth=0.5)[0]
            )
            self._ground_lines.append(
                ax.plot([-r, r], [i, i], [0, 0], color="#1a2a1a", alpha=0.25, linewidth=0.5)[0]
            )

        xx, yy = np.meshgrid(np.linspace(-r, r, 2), np.linspace(-r, r, 2))
        zz = np.zeros_like(xx)
        ax.plot_surface(xx, yy, zz, color="#3a3020", alpha=0.12, shade=False)

        self._laptop_scatter = ax.scatter(
            [0], [0], [1.0], c="#00ffcc", s=80, marker="s", depthshade=True, label="Tú / AP"
        )

        self._beam_line, = ax.plot([], [], [], color="#00ffcc", alpha=0.45, linewidth=2.0)
        self._dir_line, = ax.plot([0, 0], [0, 1.2], [1.0, 1.0], color="#00ffcc", linewidth=2.5, alpha=0.85)
        self._trail_line, = ax.plot([], [], [], color="#ff6699", alpha=0.45, linewidth=1.5)

        self._nav_text = ax.text2D(
            0.02, 0.02, "", transform=ax.transAxes, color="#ffcc00",
            fontsize=9, family="monospace", va="bottom",
        )

        ax.view_init(elev=self.elev, azim=self.azim)
        ax.legend(loc="upper right", fontsize=6, framealpha=0.25)

    def rotate_azim(self, delta: float) -> None:
        self.azim = (self.azim + delta) % 360
        self._auto_rotate = False

    def rotate_elev(self, delta: float) -> None:
        self.elev = float(np.clip(self.elev + delta, 8, 82))
        self._auto_rotate = False

    def reset_view(self) -> None:
        self.elev = 28.0
        self.azim = -55.0
        self._auto_rotate = True

    @staticmethod
    def _depth_under_rubble(distance_m: float, confidence: float) -> float:
        depth = min(1.8, max(0.3, distance_m * 0.25))
        return -depth * (0.5 + 0.5 * min(1.0, confidence))

    @staticmethod
    def describe_person_position(
        x: float, y: float, z: float, phone_bearing_deg: float = 0.0
    ) -> str:
        """Texto legible: frente/atrás, izq/der, arriba/bajo escombros."""
        rad = math.radians(-phone_bearing_deg)
        rel_x = x * math.cos(rad) - y * math.sin(rad)
        rel_y = x * math.sin(rad) + y * math.cos(rad)
        hor: list[str] = []
        if rel_y >= 0.35:
            hor.append("Frente")
        elif rel_y <= -0.35:
            hor.append("Atrás")
        if rel_x >= 0.35:
            hor.append("Derecha")
        elif rel_x <= -0.35:
            hor.append("Izquierda")
        horizontal = " y ".join(hor) if hor else "Cerca de ti"
        if z > 0.35:
            vertical = f"Arriba del suelo (+{z:.1f} m)"
        elif z < -0.15:
            vertical = f"Bajo escombros ({abs(z):.1f} m)"
        else:
            vertical = "A nivel del suelo"
        dist = math.hypot(x, y)
        return f"{horizontal} · {vertical} · {dist:.1f} m | X={x:+.2f} Y={y:+.2f} Z={z:+.2f}"

    def _clear_sphere_lines(self) -> None:
        for line in self._target_sphere_lines:
            self._remove_artist(line)
        self._target_sphere_lines.clear()

    def _draw_sphere_wireframe(
        self, cx: float, cy: float, cz: float, radius: float, color: str
    ) -> None:
        """Esfera 3D (3 anillos) en el torso estimado de la persona."""
        self._clear_sphere_lines()
        segments = 32
        for plane in ("xy", "xz", "yz"):
            xs, ys, zs = [], [], []
            for i in range(segments + 1):
                t = i / segments * 2 * math.pi
                if plane == "xy":
                    xs.append(cx + radius * math.cos(t))
                    ys.append(cy + radius * math.sin(t))
                    zs.append(cz)
                elif plane == "xz":
                    xs.append(cx + radius * math.cos(t))
                    ys.append(cy)
                    zs.append(cz + radius * math.sin(t))
                else:
                    xs.append(cx)
                    ys.append(cy + radius * math.cos(t))
                    zs.append(cz + radius * math.sin(t))
            alpha = 0.55 if plane == "xy" else 0.35
            line, = self.ax.plot(xs, ys, zs, color=color, alpha=alpha, linewidth=1.2)
            self._target_sphere_lines.append(line)

    def update(
        self,
        pos_x: float | None,
        pos_y: float | None,
        bearing_deg: float,
        trail: list[tuple[float, float, float]],
        antenna_points: list[tuple[float, float, str, float]],
        heatmap_xyz: list[tuple[float, float, float, float]],
        is_breathing: bool,
        target_color: str,
        confidence: float = 0.0,
        now: float = 0.0,
    ) -> None:
        if self._auto_rotate:
            self.azim = (self.azim + 0.25) % 360

        self.ax.view_init(elev=self.elev, azim=self.azim)

        rad = math.radians(bearing_deg)
        dx = math.sin(rad) * 1.4
        dy = math.cos(rad) * 1.4
        self._dir_line.set_data_3d([0, dx], [0, dy], [1.0, 1.0])

        if antenna_points:
            ax_x = [p[0] for p in antenna_points]
            ax_y = [p[1] for p in antenna_points]
            ax_z = [1.4 + 0.15 * min(1.0, p[3]) for p in antenna_points]
            self._antenna_scatter = self._scatter(
                ax_x, ax_y, ax_z, self._antenna_scatter,
                c="#33aaff", s=60, marker="^", depthshade=True,
            )
        else:
            self._remove_artist(self._antenna_scatter)
            self._antenna_scatter = None

        if heatmap_xyz:
            # Máx. 60 puntos y tamaño uniforme — evita lag y bugs de sizes en scatter 3D
            pts = sorted(heatmap_xyz, key=lambda p: p[3], reverse=True)[:60]
            hx = [p[0] for p in pts]
            hy = [p[1] for p in pts]
            hz = [p[2] for p in pts]
            alphas = [0.15 + 0.45 * min(1.0, p[3]) for p in pts]
            self._heatmap_scatter = self._scatter(
                hx, hy, hz, self._heatmap_scatter,
                c="#ff6600", s=32, alpha=alphas, depthshade=True,
            )
        else:
            self._remove_artist(self._heatmap_scatter)
            self._heatmap_scatter = None

        if trail:
            self._trail_line.set_data_3d(
                [p[0] for p in trail],
                [p[1] for p in trail],
                [p[2] for p in trail],
            )
        else:
            self._trail_line.set_data_3d([], [], [])

        if pos_x is not None and pos_y is not None:
            dist = math.hypot(pos_x, pos_y)
            pos_z = self._depth_under_rubble(dist, confidence)
            torso_z = pos_z + 1.14
            color = target_color if is_breathing else "#ff9900"

            self._draw_sphere_wireframe(pos_x, pos_y, torso_z, 0.38, color)

            self._target_scatter = self._scatter(
                [pos_x], [pos_y], [torso_z], self._target_scatter,
                c=color, s=120, marker="o", depthshade=True,
            )
            self._target_glow = self._scatter(
                [pos_x], [pos_y], [torso_z], self._target_glow,
                c=color, s=280, alpha=0.18, depthshade=False,
            )
            self._beam_line.set_data_3d([0, pos_x], [0, pos_y], [1.0, torso_z])

            rel = self.describe_person_position(pos_x, pos_y, pos_z, bearing_deg)

            nav_angle = math.degrees(math.atan2(pos_x, pos_y)) % 360
            turn = nav_angle - bearing_deg
            if turn > 180:
                turn -= 360
            elif turn < -180:
                turn += 360

            if abs(turn) < 15:
                steer = "↑ AVANZA recto"
            elif turn > 0:
                steer = f"↗ Gira {abs(turn):.0f}° a la DERECHA"
            else:
                steer = f"↖ Gira {abs(turn):.0f}° a la IZQUIERDA"

            self._nav_text.set_text(
                f"{rel}\n"
                f"{steer}\n"
                f"Dist horizontal: {dist:.1f} m\n"
                f"Profundidad est.: {abs(pos_z):.1f} m bajo escombros\n"
                f"Rumbo objetivo: {nav_angle:.0f}° | Q/E rotar vista | W/S inclinar"
            )
        else:
            self._clear_sphere_lines()
            self._remove_artist(self._target_scatter)
            self._remove_artist(self._target_glow)
            self._target_scatter = None
            self._target_glow = None
            self._beam_line.set_data_3d([], [], [])
            self._nav_text.set_text(
                "Gira 360° o usa ← → para triangular\n"
                "Q/E = rotar radar 3D | W/S = inclinar | V = reset vista"
            )
