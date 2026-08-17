const fs = require('fs');

let html = fs.readFileSync('landing/index.html', 'utf8');

// 1. Update top nav links
html = html.replace('<a href="#about" class="nav-link" data-i18n="about.title">Qué es</a>', '<a href="#about" class="nav-link" data-i18n="nav.about">Qué es</a>');
html = html.replace('<a href="#versions" class="nav-link" data-i18n="hero.cta.versions">Versiones</a>', '<a href="#versions" class="nav-link" data-i18n="nav.versions">Versiones</a>');
html = html.replace('<a href="#contact" class="nav-link" data-i18n="contact.title">Contacto</a>', '<a href="#contact" class="nav-link" data-i18n="nav.contact">Contacto</a>');

// 2. Update releases table rows with exact data-i18n
const replacements = [
  {
    find: '<td>Alineación de memoria 16 KB para Google Play, exclusión de AD_ID y R8.</td>',
    replace: '<td data-i18n="history.v32.desc">Alineación de memoria 16 KB para Google Play, exclusión de AD_ID y R8.</td>'
  },
  {
    find: '<td>Alineación de páginas de 16 KB, exclusión de AD_ID y archivo de desofuscación R8.</td>',
    replace: '<td data-i18n="history.v31.desc">Alineación de páginas de 16 KB, exclusión de AD_ID y archivo de desofuscación R8.</td>'
  },
  {
    find: '<td>Eliminación de rayas de reflejo Wi-Fi, optimización de hit detection 3D y Android 5.0+.</td>',
    replace: '<td data-i18n="history.v30.desc">Eliminación de rayas de reflejo Wi-Fi, optimización de hit detection 3D y Android 5.0+.</td>'
  },
  {
    find: '<td>Multi-idioma (30 idiomas con buscador), fix de insets de barra de estado y padding.</td>',
    replace: '<td data-i18n="history.v29.desc">Multi-idioma (30 idiomas con buscador), insets de barra de estado y padding.</td>'
  },
  {
    find: '<td>Filtro de atenuación de escombros y estabilidad del sensor barométrico.</td>',
    replace: '<td data-i18n="history.v28.desc">Filtro de atenuación de escombros y estabilidad del sensor barométrico.</td>'
  },
  {
    find: '<td>Calibración de sensor barométrico y detección de altura física en 3D.</td>',
    replace: '<td data-i18n="history.v26.desc">Calibración de sensor barométrico y detección de altura física en 3D.</td>'
  },
  {
    find: '<td>Optimización en el detector de frecuencia cardíaca y respiratoria.</td>',
    replace: '<td data-i18n="history.v25.desc">Optimización en el detector de frecuencia cardíaca y respiratoria.</td>'
  },
  {
    find: '<td>Mejoras de rendimiento en CPUs de baja potencia y ahorro de batería.</td>',
    replace: '<td data-i18n="history.v24.desc">Mejoras de rendimiento en CPUs de baja potencia y ahorro de batería.</td>'
  },
  {
    find: '<td>Inferencia on-device con TensorFlow Lite y análisis de firmas espectrales.</td>',
    replace: '<td data-i18n="history.v23.desc">Inferencia on-device con TensorFlow Lite y firmas espectrales.</td>'
  },
  {
    find: '<td>Compilación App Bundle de producción para Google Play Console.</td>',
    replace: '<td data-i18n="history.v22.desc">Compilación App Bundle de producción para Google Play Console.</td>'
  },
  {
    find: '<td>Integración de brújula con vector de rotación y filtrado polar.</td>',
    replace: '<td data-i18n="history.v21.desc">Integración de brújula con vector de rotación y filtrado polar.</td>'
  },
  {
    find: '<td>Primera versión con motor de radar 3D y telemetría de campo.</td>',
    replace: '<td data-i18n="history.v20.desc">Primera versión con motor de radar 3D y telemetría de campo.</td>'
  },
  {
    find: '<td>Versión inicial de investigación para pruebas de campo en La Guaira.</td>',
    replace: '<td data-i18n="history.v10.desc">Versión inicial de investigación para pruebas de campo en La Guaira.</td>'
  },
  {
    find: '<span data-od-id="footer.copy">© 2026 Carlos Mundaray — Solvitco · GPL v3</span>',
    replace: '<span data-od-id="footer.copy" data-i18n="footer.copy">© 2026 Carlos Mundaray — Solvitco · GPL v3</span>'
  }
];

for (const r of replacements) {
  if (html.includes(r.find)) {
    html = html.replace(r.find, r.replace);
  }
}

fs.writeFileSync('landing/index.html', html, 'utf8');
console.log('Successfully patched landing/index.html with data-i18n tags');
