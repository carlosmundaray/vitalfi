const fs = require('fs');
const path = require('path');

const i18nPath = path.join(__dirname, '..', 'landing', 'i18n.js');
const { VITALFI_LANGUAGES, TRANSLATIONS } = require(i18nPath);

const manualES = {
  'manual.web.link': 'Ver Manual Interactivo en Línea',
  'manual.nav.back': 'Volver a la web',
  'manual.tag': 'Guía Táctica Oficial',
  'manual.hero.title': 'Manual de Operaciones y Rescate en Campo',
  'manual.hero.desc': 'Protocolos técnicos de instalación, calibración táctica, interpretación del radar 3D/2D y detección biológica en estructuras colapsadas.',
  'manual.toc.title': 'Contenido',
  'manual.toc.cap1': '1. Principio Físico',
  'manual.toc.cap2': '2. Requisitos y Permisos',
  'manual.toc.cap3': '3. Calibración Inicial',
  'manual.toc.cap4': '4. Radar Polar y 3D',
  'manual.toc.cap5': '5. Alerta de Excavación',
  'manual.toc.cap6': '6. Seguridad y Protocolos',
  'manual.cap1.title': '1. Principio Físico y Procesamiento DSP',
  'manual.cap1.p1': 'VitalFi utiliza fluctuaciones de micro-potencia y cambios de fase en las señales de radiofrecuencia (RF) causados por movimientos micrométricos en el tórax de seres vivos.',
  'manual.cap1.resp.title': 'Filtro de Respiración',
  'manual.cap1.resp.desc': '0.10 Hz a 0.60 Hz (6 a 36 respiraciones por minuto).',
  'manual.cap1.card.title': 'Filtro Cardíaco',
  'manual.cap1.card.desc': '0.80 Hz a 2.50 Hz (48 a 150 latidos por minuto).',
  'manual.cap1.mov.title': 'Filtro de Movimiento',
  'manual.cap1.mov.desc': 'Exclusión del movimiento del propio rescatista mediante sensores inerciales.',
  'manual.cap2.title': '2. Requisitos de Hardware y Permisos',
  'manual.cap2.p1': 'La aplicación funciona en dispositivos con Android 5.0 (API 21) hasta Android 16. No requiere conexión a Internet.',
  'manual.cap2.perm.title': 'Permisos requeridos en la app:',
  'manual.cap2.perm.1': 'Ubicación precisa: Requerido por Android para el subsistema de escaneo de radio.',
  'manual.cap2.perm.2': 'Sensores de movimiento y brújula: Para la orientación del radar en 360° y proyección de pasos.',
  'manual.cap2.perm.3': 'Almacenamiento / Exportación: Para guardar la bitácora de rescate en formato CSV forense.',
  'manual.cap3.title': '3. Calibración en Terreno',
  'manual.cap3.p1': 'Antes de ingresar a la zona de escombros, mantenga el dispositivo firme durante 5 segundos para calibrar el piso de ruido ambiental (Noise Floor Calibration).',
  'manual.cap3.callout': 'Consejo de campo: Realice la calibración apuntando hacia la estructura colapsada a una distancia de seguridad de 2 a 3 metros.',
  'manual.cap4.title': '4. Interpretación del Radar Táctico (2D y 3D)',
  'manual.cap4.p1': 'La interfaz de radar táctico muestra la posición estimada de la víctima con respecto al eje de avance del rescatista.',
  'manual.cap4.li1': 'Punto Cian / Verde: Detección de patrón respiratorio estable (> 75% confianza).',
  'manual.cap4.li2': 'Banner de Telemetría Superior: Indica los metros restantes y la corrección angular (ej. ↑ Recto 1.8 m o ↻ 25° der).',
  'manual.cap4.li3': 'Modo 3D: Proyecta la profundidad estimada bajo el plano del suelo o la altura de los escombros.',
  'manual.cap5.title': '5. Alerta de Punto Cero de Excavación',
  'manual.cap5.p1': 'Cuando el rescatista se aproxima a menos de 1.0 metro de la víctima detectada, la aplicación activa automáticamente la alarma visual y háptica de PUNTO CERO.',
  'manual.cap5.callout': 'Alerta de Excavación: Detenga la marcha. Verifique la estabilidad estructural antes de realizar intervenciones o remoción de escombros con herramientas pesadas.',
  'manual.cap6.title': '6. Protocolos de Seguridad USAR',
  'manual.cap6.p1': 'VitalFi es una herramienta complementaria de respuesta rápida. No sustituye los protocolos oficiales del Grupo Asesor Internacional de Operaciones de Búsqueda y Rescate (INSARAG) ni los equipos geofónicos homologados.'
};

const manualEN = {
  'manual.web.link': 'View Interactive Online Manual',
  'manual.nav.back': 'Back to website',
  'manual.tag': 'Official Tactical Guide',
  'manual.hero.title': 'Field Operations & Rescue Manual',
  'manual.hero.desc': 'Technical protocols for installation, tactical calibration, 3D/2D radar interpretation, and biological sensing in collapsed structures.',
  'manual.toc.title': 'Table of Contents',
  'manual.toc.cap1': '1. Physical Principle',
  'manual.toc.cap2': '2. Hardware & Permissions',
  'manual.toc.cap3': '3. Field Calibration',
  'manual.toc.cap4': '4. Tactical Radar 2D/3D',
  'manual.toc.cap5': '5. Excavation Point Zero',
  'manual.toc.cap6': '6. USAR Safety Protocols',
  'manual.cap1.title': '1. Physical Principle & DSP Processing',
  'manual.cap1.p1': 'VitalFi detects micro-power fluctuations and phase shifts in RF signals caused by micrometric chest movements of trapped survivors.',
  'manual.cap1.resp.title': 'Breathing Bandpass Filter',
  'manual.cap1.resp.desc': '0.10 Hz to 0.60 Hz (6 to 36 breaths per minute).',
  'manual.cap1.card.title': 'Heart Rate Bandpass Filter',
  'manual.cap1.card.desc': '0.80 Hz to 2.50 Hz (48 to 150 beats per minute).',
  'manual.cap1.mov.title': 'Rescuer Motion Filter',
  'manual.cap1.mov.desc': 'Exclusion of the rescuer own walking motion using on-board IMU sensors.',
  'manual.cap2.title': '2. Hardware Requirements & Permissions',
  'manual.cap2.p1': 'Runs on Android 5.0 (API 21) up to Android 16. Completely offline without Internet requirements.',
  'manual.cap2.perm.title': 'Required Application Permissions:',
  'manual.cap2.perm.1': 'Precise Location: Required by Android OS to interface with wireless scanning radios.',
  'manual.cap2.perm.2': 'Motion Sensors & Compass: Required for 360° radar heading and step vector projection.',
  'manual.cap2.perm.3': 'Storage / Export: To export tactical CSV logs for USAR post-mission debriefing.',
  'manual.cap3.title': '3. Field Calibration',
  'manual.cap3.p1': 'Hold device steady for 5 seconds before entering the debris perimeter to perform noise floor baseline calibration.',
  'manual.cap3.callout': 'Field Tip: Point device toward the rubble mound from a 2 to 3-meter safety perimeter during baseline calibration.',
  'manual.cap4.title': '4. Tactical Radar Interpretation (2D & 3D)',
  'manual.cap4.p1': 'The tactical HUD projects the survivor relative position along the rescuer heading axis.',
  'manual.cap4.li1': 'Cyan/Green Icon: Stable breathing pattern identified (>75% confidence).',
  'manual.cap4.li2': 'Top Telemetry Banner: Displays remaining distance and angular steering (e.g. ↑ Straight 1.8 m or ↻ 25° right).',
  'manual.cap4.li3': '3D Canvas: Projects depth below surface and debris height estimation.',
  'manual.cap5.title': '5. Excavation Point Zero Alert',
  'manual.cap5.p1': 'When the rescuer closes within 1.0 meter of the target, VitalFi triggers a visual and haptic POINT ZERO EXCAVATION alert.',
  'manual.cap5.callout': 'Excavation Alert: Halt forward movement. Conduct structural stability assessment before clearing rubble with heavy tools.',
  'manual.cap6.title': '6. USAR Safety Protocols',
  'manual.cap6.p1': 'VitalFi is an auxiliary rapid-response tool. It does not replace official INSARAG guidelines or certified acoustic geophones.'
};

for (const lang of Object.keys(TRANSLATIONS)) {
  if (lang === 'es') {
    Object.assign(TRANSLATIONS.es, manualES);
  } else {
    // Inject English or specific manual keys
    Object.assign(TRANSLATIONS[lang], manualEN);
  }
}

const fileContent = `/**
 * VitalFi Landing Page & Manual Multi-Language Translation System
 * Full comprehensive translations for major global languages.
 */

const VITALFI_LANGUAGES = ${JSON.stringify(VITALFI_LANGUAGES, null, 2)};

const TRANSLATIONS = ${JSON.stringify(TRANSLATIONS, null, 2)};

function getTranslation(lang, key) {
  if (TRANSLATIONS[lang] && TRANSLATIONS[lang][key]) {
    return TRANSLATIONS[lang][key];
  }
  if (TRANSLATIONS["en"] && TRANSLATIONS["en"][key]) {
    return TRANSLATIONS["en"][key];
  }
  return (TRANSLATIONS["es"] && TRANSLATIONS["es"][key]) || "";
}

function applyLanguage(langCode) {
  const langObj = VITALFI_LANGUAGES.find(l => l.code === langCode) || VITALFI_LANGUAGES[0];
  document.documentElement.lang = langObj.code;
  
  if (langObj.rtl) {
    document.documentElement.setAttribute("dir", "rtl");
  } else {
    document.documentElement.removeAttribute("dir");
  }

  // Update all elements with data-i18n
  document.querySelectorAll("[data-i18n]").forEach(el => {
    const key = el.getAttribute("data-i18n");
    const trans = getTranslation(langObj.code, key);
    if (trans) {
      el.textContent = trans;
    }
  });

  // Update search input placeholder
  const searchInput = document.getElementById("lang-search");
  if (searchInput) {
    searchInput.placeholder = getTranslation(langObj.code, "modal.lang.search") || "Buscar idioma...";
  }

  // Update language button on top nav
  const btn = document.getElementById("lang-btn");
  if (btn) {
    btn.innerHTML = \`<span class="lang-flag">\${langObj.flag}</span> <span class="lang-name">\${langObj.native}</span> <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><path d="m6 9 6 6 6-6"/></svg>\`;
  }

  localStorage.setItem("vitalfi_lang", langObj.code);
}

function initLanguageModal() {
  const modal = document.getElementById("lang-modal");
  const btn = document.getElementById("lang-btn");
  const closeBtn = document.getElementById("lang-modal-close");
  const searchInput = document.getElementById("lang-search");
  const listContainer = document.getElementById("lang-list");

  if (!modal || !btn) return;

  function renderList(query = "") {
    const q = query.toLowerCase().trim();
    const filtered = VITALFI_LANGUAGES.filter(l => 
      l.name.toLowerCase().includes(q) || 
      l.native.toLowerCase().includes(q) || 
      l.code.toLowerCase().includes(q)
    );

    const current = localStorage.getItem("vitalfi_lang") || "es";
    listContainer.innerHTML = filtered.map(l => \`
      <button class="lang-item \${l.code === current ? 'active' : ''}" data-code="\${l.code}">
        <span class="lang-item-flag">\${l.flag}</span>
        <div class="lang-item-text">
          <strong>\${l.native}</strong>
          <span>\${l.name} (\${l.code.toUpperCase()})</span>
        </div>
        \${l.code === current ? '<span class="lang-check">✓</span>' : ''}
      </button>
    \`).join("");

    listContainer.querySelectorAll(".lang-item").forEach(item => {
      item.addEventListener("click", () => {
        const code = item.getAttribute("data-code");
        applyLanguage(code);
        closeModal();
      });
    });
  }

  function openModal() {
    modal.classList.add("open");
    searchInput.value = "";
    renderList();
    setTimeout(() => searchInput.focus(), 100);
  }

  function closeModal() {
    modal.classList.remove("open");
  }

  btn.addEventListener("click", openModal);
  if (closeBtn) closeBtn.addEventListener("click", closeModal);

  modal.addEventListener("click", (e) => {
    if (e.target === modal) closeModal();
  });

  searchInput.addEventListener("input", (e) => {
    renderList(e.target.value);
  });

  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape" && modal.classList.contains("open")) {
      closeModal();
    }
  });

  // Detect user preference or browser language
  const saved = localStorage.getItem("vitalfi_lang");
  if (saved) {
    applyLanguage(saved);
  } else {
    const nav = (navigator.language || navigator.userLanguage || "es").substring(0, 2).toLowerCase();
    const match = VITALFI_LANGUAGES.find(l => l.code === nav);
    applyLanguage(match ? match.code : "es");
  }
}

if (typeof document !== "undefined") {
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", initLanguageModal);
  } else {
    initLanguageModal();
  }
}

if (typeof module !== "undefined") {
  module.exports = { VITALFI_LANGUAGES, TRANSLATIONS, getTranslation };
}
`;

fs.writeFileSync(i18nPath, fileContent, 'utf8');
console.log('Successfully wrote landing/i18n.js with all manual keys for all languages');
