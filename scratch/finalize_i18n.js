const fs = require('fs');

let html = fs.readFileSync('landing/index.html', 'utf8');

// Replace date cells in historical table
html = html.replaceAll('<td style="color: var(--muted); white-space: nowrap;">15 Ago 2026</td>', '<td style="color: var(--muted); white-space: nowrap;" data-i18n="history.date.aug15">15 Ago 2026</td>');
html = html.replaceAll('<td style="color: var(--muted); white-space: nowrap;">04 Ago 2026</td>', '<td style="color: var(--muted); white-space: nowrap;" data-i18n="history.date.aug04">04 Ago 2026</td>');
html = html.replaceAll('<td style="color: var(--muted); white-space: nowrap;">24 Jul 2026</td>', '<td style="color: var(--muted); white-space: nowrap;" data-i18n="history.date.jul24">24 Jul 2026</td>');
html = html.replaceAll('<td style="color: var(--muted); white-space: nowrap;">23 Jul 2026</td>', '<td style="color: var(--muted); white-space: nowrap;" data-i18n="history.date.jul23">23 Jul 2026</td>');

fs.writeFileSync('landing/index.html', html, 'utf8');

// Update i18n.js with dates
let i18n = fs.readFileSync('landing/i18n.js', 'utf8');
const datesES = {
  'history.date.aug15': '15 Ago 2026',
  'history.date.aug04': '04 Ago 2026',
  'history.date.jul24': '24 Jul 2026',
  'history.date.jul23': '23 Jul 2026'
};
const datesEN = {
  'history.date.aug15': 'Aug 15, 2026',
  'history.date.aug04': 'Aug 04, 2026',
  'history.date.jul24': 'Jul 24, 2026',
  'history.date.jul23': 'Jul 23, 2026'
};

const { TRANSLATIONS, VITALFI_LANGUAGES } = require('../landing/i18n.js');
for (const lang of Object.keys(TRANSLATIONS)) {
  if (lang === 'es') Object.assign(TRANSLATIONS.es, datesES);
  else Object.assign(TRANSLATIONS[lang], datesEN);
}

// Re-write i18n.js
const { buildI18nFile } = require('./build_full_i18n_index.js');
let i18nCode = fs.readFileSync('landing/i18n.js', 'utf8');
i18nCode = i18nCode.replace(/const TRANSLATIONS = \{[\s\S]*?\n\};\n\nfunction getTranslation/, 'const TRANSLATIONS = ' + JSON.stringify(TRANSLATIONS, null, 2) + ';\n\nfunction getTranslation');
fs.writeFileSync('landing/i18n.js', i18nCode, 'utf8');

console.log('Successfully finalized all translations in landing page');
