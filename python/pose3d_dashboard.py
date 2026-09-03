"""
VitalFi — Pose3D Three.js Dashboard Generator

Generates the static HTML dashboard with Three.js for real-time
3D skeleton visualization from Wi-Fi CSI pose estimation.

The dashboard connects to the Pose3D WebSocket server and renders:
  - Animated 3D skeleton (17 COCO keypoints)
  - Posture classification with emoji indicator
  - Vital signs overlay (respiratory rate, heart rate)
  - Node status panel
  - CSI amplitude heatmap
  - Inference performance metrics

Usage:
    python pose3d_dashboard.py
    # Then open http://localhost:8080/dashboard

This script generates pose3d_dashboard_static.html which is served
by the Pose3D HTTP server.
"""

from pathlib import Path


DASHBOARD_HTML = r'''<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>VitalFi — Person-in-WiFi 3D Pose Dashboard</title>
    <script src="https://cdnjs.cloudflare.com/ajax/libs/three.js/r128/three.min.js"></script>
    <style>
        @import url('https://fonts.googleapis.com/css2?family=Inter:wght@400;600;700;800&display=swap');
        :root {
            --bg: #07080f;
            --bg-card: rgba(15, 20, 35, 0.9);
            --accent: #00f2fe;
            --accent-green: #00ff88;
            --accent-purple: #a855f7;
            --accent-rose: #f43f5e;
            --text: #eef2f7;
            --text-muted: #6b7b8f;
            --border: rgba(0, 242, 254, 0.12);
            --glow: rgba(0, 242, 254, 0.3);
        }
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body { font-family: 'Inter', sans-serif; background: var(--bg); color: var(--text); overflow: hidden; height: 100vh; }
        .layout { display: grid; grid-template-columns: 1fr 320px; grid-template-rows: auto 1fr auto; height: 100vh; gap: 0; }
        .header { grid-column: 1 / -1; display: flex; align-items: center; justify-content: space-between; padding: 0.8rem 1.2rem; border-bottom: 1px solid var(--border); background: var(--bg-card); }
        .logo { font-size: 1.3rem; font-weight: 800; background: linear-gradient(135deg, var(--accent), var(--accent-purple)); -webkit-background-clip: text; -webkit-text-fill-color: transparent; }
        .header-badges { display: flex; gap: 0.5rem; }
        .badge { padding: 0.25rem 0.7rem; border-radius: 999px; font-size: 0.7rem; font-weight: 600; }
        .badge-live { background: rgba(0,255,136,0.12); color: var(--accent-green); border: 1px solid rgba(0,255,136,0.3); }
        .badge-pose { background: rgba(168,85,247,0.12); color: var(--accent-purple); border: 1px solid rgba(168,85,247,0.3); }
        .badge-off { background: rgba(100,100,100,0.12); color: #666; border: 1px solid #444; }

        .viewport { position: relative; overflow: hidden; }
        #three-canvas { width: 100%; height: 100%; display: block; }
        .posture-overlay { position: absolute; bottom: 1.5rem; left: 50%; transform: translateX(-50%); background: var(--bg-card); border: 1px solid var(--border); border-radius: 16px; padding: 0.8rem 2rem; text-align: center; backdrop-filter: blur(12px); }
        .posture-emoji { font-size: 2.5rem; }
        .posture-label { font-size: 1rem; font-weight: 700; margin-top: 0.3rem; text-transform: uppercase; letter-spacing: 1px; }
        .posture-conf { font-size: 0.75rem; color: var(--text-muted); }

        .sidebar { background: var(--bg-card); border-left: 1px solid var(--border); padding: 1rem; overflow-y: auto; display: flex; flex-direction: column; gap: 1rem; }
        .panel { background: rgba(0,0,0,0.3); border: 1px solid var(--border); border-radius: 10px; padding: 0.8rem; }
        .panel-title { font-size: 0.7rem; font-weight: 700; color: var(--text-muted); text-transform: uppercase; letter-spacing: 1px; margin-bottom: 0.6rem; }
        .stat-row { display: flex; justify-content: space-between; align-items: center; padding: 0.3rem 0; }
        .stat-label { font-size: 0.75rem; color: var(--text-muted); }
        .stat-value { font-size: 0.85rem; font-weight: 700; }
        .stat-value.green { color: var(--accent-green); }
        .stat-value.cyan { color: var(--accent); }
        .stat-value.purple { color: var(--accent-purple); }
        .stat-value.rose { color: var(--accent-rose); }

        .vital-big { text-align: center; padding: 0.5rem; }
        .vital-number { font-size: 2rem; font-weight: 800; }
        .vital-unit { font-size: 0.7rem; color: var(--text-muted); }

        .node-dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; margin-right: 0.4rem; }
        .node-dot.active { background: var(--accent-green); box-shadow: 0 0 6px var(--accent-green); }
        .node-dot.inactive { background: #444; }

        .confidence-bars { display: flex; flex-direction: column; gap: 2px; }
        .conf-bar-row { display: flex; align-items: center; gap: 4px; }
        .conf-bar-label { font-size: 0.55rem; color: var(--text-muted); width: 45px; text-align: right; overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }
        .conf-bar-track { flex: 1; height: 4px; background: rgba(255,255,255,0.06); border-radius: 2px; }
        .conf-bar-fill { height: 100%; border-radius: 2px; transition: width 0.2s; }

        .footer { grid-column: 1 / -1; padding: 0.4rem 1rem; text-align: center; font-size: 0.65rem; color: var(--text-muted); border-top: 1px solid var(--border); background: var(--bg-card); }
    </style>
</head>
<body>
<div class="layout">
    <div class="header">
        <div class="logo">🧍 VitalFi — Person-in-WiFi 3D</div>
        <div class="header-badges">
            <span class="badge badge-off" id="ws-badge">⏳ Connecting...</span>
            <span class="badge badge-pose" id="pose-badge">Pose3D v2.1</span>
        </div>
    </div>

    <div class="viewport">
        <canvas id="three-canvas"></canvas>
        <div class="posture-overlay" id="posture-overlay">
            <div class="posture-emoji" id="posture-emoji">⏳</div>
            <div class="posture-label" id="posture-label">Esperando datos...</div>
            <div class="posture-conf" id="posture-conf"></div>
        </div>
    </div>

    <div class="sidebar">
        <!-- Vital Signs -->
        <div class="panel">
            <div class="panel-title">💨 Signos Vitales</div>
            <div style="display: grid; grid-template-columns: 1fr 1fr; gap: 0.5rem;">
                <div class="vital-big">
                    <div class="vital-number cyan" id="val-rpm">--</div>
                    <div class="vital-unit">RPM (Respiración)</div>
                </div>
                <div class="vital-big">
                    <div class="vital-number rose" id="val-bpm">--</div>
                    <div class="vital-unit">BPM (Pulso)</div>
                </div>
            </div>
        </div>

        <!-- Node Status -->
        <div class="panel">
            <div class="panel-title">📡 Nodos ESP32-S3</div>
            <div id="nodes-container">
                <div class="stat-row">
                    <span class="stat-label"><span class="node-dot inactive"></span>Sin nodos</span>
                    <span class="stat-value">---</span>
                </div>
            </div>
        </div>

        <!-- Inference Stats -->
        <div class="panel">
            <div class="panel-title">⚡ Rendimiento</div>
            <div class="stat-row">
                <span class="stat-label">Inferencia</span>
                <span class="stat-value cyan" id="val-infer">-- ms</span>
            </div>
            <div class="stat-row">
                <span class="stat-label">Frames usados</span>
                <span class="stat-value" id="val-frames">--</span>
            </div>
            <div class="stat-row">
                <span class="stat-label">FPS modelo</span>
                <span class="stat-value green" id="val-fps">--</span>
            </div>
        </div>

        <!-- Keypoint Confidence -->
        <div class="panel">
            <div class="panel-title">🎯 Confianza por Articulación</div>
            <div class="confidence-bars" id="conf-bars"></div>
        </div>
    </div>

    <div class="footer">
        VitalFi v2.1 — Person-in-WiFi 3D Pose Estimation &copy; 2026 | WebSocket: <span id="ws-url">ws://localhost:8765</span>
    </div>
</div>

<script>
// ========== THREE.JS 3D SKELETON RENDERER ==========
const canvas = document.getElementById('three-canvas');
const viewport = canvas.parentElement;

const scene = new THREE.Scene();
scene.background = new THREE.Color(0x07080f);

// Camera
const camera = new THREE.PerspectiveCamera(50, viewport.clientWidth / viewport.clientHeight, 0.1, 100);
camera.position.set(0, 2, 5);
camera.lookAt(0, 1, 0);

const renderer = new THREE.WebGLRenderer({ canvas, antialias: true });
renderer.setSize(viewport.clientWidth, viewport.clientHeight);
renderer.setPixelRatio(window.devicePixelRatio);

// Lighting
const ambient = new THREE.AmbientLight(0x334466, 0.6);
scene.add(ambient);
const directional = new THREE.DirectionalLight(0x00f2fe, 0.8);
directional.position.set(5, 10, 5);
scene.add(directional);

// Ground plane grid
const gridHelper = new THREE.GridHelper(10, 20, 0x1a2a3a, 0x0d1520);
scene.add(gridHelper);

// Axis helper (subtle)
const axisHelper = new THREE.AxesHelper(0.5);
axisHelper.position.set(-4.5, 0.01, -4.5);
scene.add(axisHelper);

// Keypoint spheres
const KEYPOINT_COLORS = {
    head: 0x00f2fe,    // cyan
    arm: 0xa855f7,     // purple
    torso: 0x00ff88,   // green
    leg: 0xf59e0b,     // amber
};
const keypointGroups = {
    head: [0, 1, 2, 3, 4],
    arm: [5, 6, 7, 8, 9, 10],
    torso: [11, 12],
    leg: [13, 14, 15, 16],
};

function getKeypointColor(idx) {
    for (const [group, indices] of Object.entries(keypointGroups)) {
        if (indices.includes(idx)) return KEYPOINT_COLORS[group];
    }
    return 0xffffff;
}

const jointSpheres = [];
const JOINT_RADIUS = 0.04;
for (let i = 0; i < 17; i++) {
    const geo = new THREE.SphereGeometry(JOINT_RADIUS, 12, 12);
    const mat = new THREE.MeshPhongMaterial({
        color: getKeypointColor(i),
        emissive: getKeypointColor(i),
        emissiveIntensity: 0.4,
    });
    const sphere = new THREE.Mesh(geo, mat);
    sphere.visible = false;
    scene.add(sphere);
    jointSpheres.push(sphere);
}

// Skeleton bones (lines)
const SKELETON = [
    [0,1],[0,2],[1,3],[2,4],
    [5,6],[5,7],[7,9],[6,8],[8,10],
    [5,11],[6,12],[11,12],
    [11,13],[13,15],[12,14],[14,16],
];

const boneMaterials = [];
const boneLines = [];
for (const [a, b] of SKELETON) {
    const geo = new THREE.BufferGeometry();
    const positions = new Float32Array(6);
    geo.setAttribute('position', new THREE.BufferAttribute(positions, 3));
    const color = getKeypointColor(a);
    const mat = new THREE.LineBasicMaterial({ color, linewidth: 2, transparent: true, opacity: 0.7 });
    const line = new THREE.Line(geo, mat);
    line.visible = false;
    scene.add(line);
    boneLines.push({ line, geo, a, b });
    boneMaterials.push(mat);
}

// Confidence bar setup
const KEYPOINT_NAMES = [
    "nose","l_eye","r_eye","l_ear","r_ear",
    "l_shoulder","r_shoulder","l_elbow","r_elbow",
    "l_wrist","r_wrist","l_hip","r_hip",
    "l_knee","r_knee","l_ankle","r_ankle",
];
const confContainer = document.getElementById('conf-bars');
const confBarFills = [];
for (let i = 0; i < 17; i++) {
    const row = document.createElement('div');
    row.className = 'conf-bar-row';
    const label = document.createElement('span');
    label.className = 'conf-bar-label';
    label.textContent = KEYPOINT_NAMES[i];
    const track = document.createElement('div');
    track.className = 'conf-bar-track';
    const fill = document.createElement('div');
    fill.className = 'conf-bar-fill';
    fill.style.width = '0%';
    fill.style.background = `#${getKeypointColor(i).toString(16).padStart(6,'0')}`;
    track.appendChild(fill);
    row.appendChild(label);
    row.appendChild(track);
    confContainer.appendChild(row);
    confBarFills.push(fill);
}

// ========== UPDATE SKELETON FROM POSE DATA ==========
function updateSkeleton(data) {
    if (!data.keypoints_3d || data.status !== 'ok') {
        jointSpheres.forEach(s => s.visible = false);
        boneLines.forEach(b => b.line.visible = false);
        return;
    }

    const kp = data.keypoints_3d;
    const conf = data.confidence || new Array(17).fill(0.5);

    // Update joint positions
    for (let i = 0; i < 17; i++) {
        const [x, y, z] = kp[i];
        jointSpheres[i].position.set(x, z, -y); // Remap: WiFi Y→3D Z, WiFi Z→3D Y
        jointSpheres[i].visible = conf[i] > 0.1;
        jointSpheres[i].scale.setScalar(0.5 + conf[i] * 1.0);
        jointSpheres[i].material.opacity = 0.3 + conf[i] * 0.7;
    }

    // Update bones
    for (const bone of boneLines) {
        const posA = jointSpheres[bone.a].position;
        const posB = jointSpheres[bone.b].position;
        const positions = bone.geo.attributes.position.array;
        positions[0] = posA.x; positions[1] = posA.y; positions[2] = posA.z;
        positions[3] = posB.x; positions[4] = posB.y; positions[5] = posB.z;
        bone.geo.attributes.position.needsUpdate = true;
        bone.line.visible = conf[bone.a] > 0.1 && conf[bone.b] > 0.1;
    }

    // Update confidence bars
    for (let i = 0; i < 17; i++) {
        confBarFills[i].style.width = `${Math.round(conf[i] * 100)}%`;
    }

    // Update posture display
    const postureEmojis = { standing: '🧍', sitting: '🪑', lying: '🛏️', fallen: '⚠️' };
    document.getElementById('posture-emoji').textContent = postureEmojis[data.posture] || '❓';
    document.getElementById('posture-label').textContent = data.posture || '---';
    document.getElementById('posture-conf').textContent = data.posture_confidence
        ? `${(data.posture_confidence * 100).toFixed(0)}% confianza`
        : '';

    // Update stats
    document.getElementById('val-infer').textContent = `${data.inference_time_ms || 0} ms`;
    document.getElementById('val-frames').textContent = data.total_frames || '--';

    // Update nodes
    if (data.active_nodes) {
        const nc = document.getElementById('nodes-container');
        nc.innerHTML = '';
        for (const nid of data.active_nodes) {
            const row = document.createElement('div');
            row.className = 'stat-row';
            row.innerHTML = `<span class="stat-label"><span class="node-dot active"></span>Nodo #${nid}</span><span class="stat-value green">Activo</span>`;
            nc.appendChild(row);
        }
    }

    // Vitals
    if (data.respiratory_rate !== undefined) {
        document.getElementById('val-rpm').textContent = data.respiratory_rate > 0 ? data.respiratory_rate.toFixed(0) : '--';
    }
    if (data.heart_rate !== undefined) {
        document.getElementById('val-bpm').textContent = data.heart_rate > 0 ? data.heart_rate.toFixed(0) : '--';
    }
}

// ========== WEBSOCKET CONNECTION ==========
let ws = null;
let lastMessageTime = 0;
let messageCount = 0;

function connectWS() {
    const wsUrl = `ws://${window.location.hostname || 'localhost'}:8765`;
    document.getElementById('ws-url').textContent = wsUrl;

    ws = new WebSocket(wsUrl);

    ws.onopen = () => {
        document.getElementById('ws-badge').className = 'badge badge-live';
        document.getElementById('ws-badge').textContent = '🟢 Conectado';
    };

    ws.onmessage = (event) => {
        try {
            const data = JSON.parse(event.data);
            updateSkeleton(data);
            messageCount++;
            const now = Date.now();
            if (now - lastMessageTime >= 1000) {
                document.getElementById('val-fps').textContent = messageCount + ' fps';
                messageCount = 0;
                lastMessageTime = now;
            }
        } catch (e) {}
    };

    ws.onclose = () => {
        document.getElementById('ws-badge').className = 'badge badge-off';
        document.getElementById('ws-badge').textContent = '🔴 Desconectado';
        setTimeout(connectWS, 3000);
    };

    ws.onerror = () => {
        ws.close();
    };
}

// ========== CAMERA ORBIT ==========
let cameraAngle = 0;
let autoRotate = true;
canvas.addEventListener('mousedown', () => { autoRotate = false; });
canvas.addEventListener('dblclick', () => { autoRotate = true; });

// ========== RENDER LOOP ==========
function animate() {
    requestAnimationFrame(animate);

    if (autoRotate) {
        cameraAngle += 0.003;
        camera.position.x = 5 * Math.sin(cameraAngle);
        camera.position.z = 5 * Math.cos(cameraAngle);
        camera.lookAt(0, 1, 0);
    }

    renderer.render(scene, camera);
}

// ========== INIT ==========
window.addEventListener('resize', () => {
    const w = viewport.clientWidth;
    const h = viewport.clientHeight;
    camera.aspect = w / h;
    camera.updateProjectionMatrix();
    renderer.setSize(w, h);
});

connectWS();
animate();
lastMessageTime = Date.now();
</script>
</body>
</html>
'''


def generate_dashboard():
    """Generate the static dashboard HTML file."""
    output_path = Path(__file__).parent / "pose3d_dashboard_static.html"
    output_path.write_text(DASHBOARD_HTML, encoding="utf-8")
    print(f"[Dashboard] Generated: {output_path}")
    print(f"[Dashboard] Open http://localhost:8080/dashboard after starting the server")
    return str(output_path)


if __name__ == "__main__":
    generate_dashboard()
