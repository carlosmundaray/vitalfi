const fs = require('fs');

// 1. Update i18n.js with robust mobile touch handling
let i18n = fs.readFileSync('landing/i18n.js', 'utf8');

const updatedModalLogic = `
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

  try {
    localStorage.setItem("vitalfi_lang", langObj.code);
  } catch (_) {}
}

let modalOpenFn = null;
let modalCloseFn = null;

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

    let current = "es";
    try {
      current = localStorage.getItem("vitalfi_lang") || "es";
    } catch (_) {}

    listContainer.innerHTML = filtered.map(l => \`
      <button type="button" class="lang-item \${l.code === current ? 'active' : ''}" data-code="\${l.code}">
        <span class="lang-item-flag">\${l.flag}</span>
        <div class="lang-item-text">
          <strong>\${l.native}</strong>
          <span>\${l.name} (\${l.code.toUpperCase()})</span>
        </div>
        \${l.code === current ? '<span class="lang-check">✓</span>' : ''}
      </button>
    \`).join("");

    listContainer.querySelectorAll(".lang-item").forEach(item => {
      const selectHandler = (e) => {
        if (e) e.preventDefault();
        const code = item.getAttribute("data-code");
        if (code) {
          applyLanguage(code);
          closeModal();
        }
      };
      item.addEventListener("click", selectHandler);
      item.addEventListener("touchend", selectHandler);
    });
  }

  function openModal(e) {
    if (e) e.preventDefault();
    modal.classList.add("open");
    modal.removeAttribute("aria-hidden");
    document.body.style.overflow = "hidden"; // Prevent background scroll on mobile
    searchInput.value = "";
    renderList();
    setTimeout(() => {
      if (window.innerWidth > 640) searchInput.focus();
    }, 100);
  }

  function closeModal(e) {
    if (e && e.target && e.target !== modal && e.target !== closeBtn && !closeBtn.contains(e.target)) {
      // Don't close if clicking inside modal content
      if (modal.firstElementChild && modal.firstElementChild.contains(e.target)) return;
    }
    if (e) e.preventDefault();
    modal.classList.remove("open");
    modal.setAttribute("aria-hidden", "true");
    document.body.style.overflow = "";
    if (btn) btn.focus();
  }

  modalOpenFn = openModal;
  modalCloseFn = closeModal;

  btn.addEventListener("click", openModal);
  btn.addEventListener("touchend", openModal);
  
  if (closeBtn) {
    closeBtn.addEventListener("click", closeModal);
    closeBtn.addEventListener("touchend", closeModal);
  }

  modal.addEventListener("click", (e) => {
    if (e.target === modal) closeModal(e);
  });

  searchInput.addEventListener("input", (e) => {
    renderList(e.target.value);
  });

  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape" && modal.classList.contains("open")) {
      closeModal(e);
    }
  });

  // Detect user preference or browser language safely
  let saved = null;
  try {
    saved = localStorage.getItem("vitalfi_lang");
  } catch (_) {}

  if (saved) {
    applyLanguage(saved);
  } else {
    const nav = (navigator.language || navigator.userLanguage || "es").substring(0, 2).toLowerCase();
    const match = VITALFI_LANGUAGES.find(l => l.code === nav);
    applyLanguage(match ? match.code : "es");
  }
}

if (typeof window !== "undefined") {
  window.applyLanguage = applyLanguage;
  window.openLanguageModal = function(e) { if (modalOpenFn) modalOpenFn(e); };
  window.closeLanguageModal = function(e) { if (modalCloseFn) modalCloseFn(e); };
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

// Replace from function applyLanguage in i18n.js
const idx = i18n.indexOf('function applyLanguage(');
if (idx !== -1) {
  i18n = i18n.substring(0, idx) + updatedModalLogic.trim() + '\n';
  fs.writeFileSync('landing/i18n.js', i18n, 'utf8');
  console.log('Successfully updated landing/i18n.js with mobile touch events');
} else {
  console.log('applyLanguage function not found in i18n.js');
}

// 2. Update landing/index.html and landing/manual.html
let indexHtml = fs.readFileSync('landing/index.html', 'utf8');

// Ensure button has type="button" and onclick fallback
indexHtml = indexHtml.replace(
  '<button id="lang-btn" class="lang-selector-btn" aria-label="Seleccionar idioma">',
  '<button id="lang-btn" class="lang-selector-btn" type="button" onclick="window.openLanguageModal(event)" aria-label="Seleccionar idioma">'
);

// Ensure modal overlay has z-index 99999 and pointer-events
indexHtml = indexHtml.replace(
  'z-index: 1000;',
  'z-index: 99999;'
);

fs.writeFileSync('landing/index.html', indexHtml, 'utf8');

// Also update manual.html
if (fs.existsSync('landing/manual.html')) {
  let manualHtml = fs.readFileSync('landing/manual.html', 'utf8');
  manualHtml = manualHtml.replace(
    '<button id="lang-btn" class="lang-selector-btn" aria-label="Cambiar idioma">',
    '<button id="lang-btn" class="lang-selector-btn" type="button" onclick="window.openLanguageModal(event)" aria-label="Cambiar idioma">'
  );
  manualHtml = manualHtml.replace(
    'z-index: 1000;',
    'z-index: 99999;'
  );
  fs.writeFileSync('landing/manual.html', manualHtml, 'utf8');
}

console.log('Successfully updated index.html and manual.html with mobile-optimized modal support');
