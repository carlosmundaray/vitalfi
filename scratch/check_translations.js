const fs = require('fs');
const html = fs.readFileSync('landing/index.html', 'utf8');
const { TRANSLATIONS } = require('../landing/i18n.js');

const regex = /data-i18n=["']([^"']+)["']/g;
const dataI18nMatches = [];
let match;
while ((match = regex.exec(html)) !== null) {
  dataI18nMatches.push(match[1]);
}

console.log('Total data-i18n tags in index.html:', dataI18nMatches.length);
const uniqueHtmlKeys = [...new Set(dataI18nMatches)];
console.log('Unique data-i18n keys in index.html:', uniqueHtmlKeys.length);

for (const lang of Object.keys(TRANSLATIONS)) {
  const missing = uniqueHtmlKeys.filter(k => !TRANSLATIONS[lang][k]);
  if (missing.length > 0) {
    console.log(`Language ${lang} missing keys:`, missing);
  }
}

// Now let's find sections in index.html that DO NOT have data-i18n attributes
console.log('\n--- SCANNING UNTRANSLATED ELEMENTS IN HTML ---');
// Let's inspect headings, paragraphs, buttons, etc.
const tagRegex = /<(h1|h2|h3|h4|p|span|a|strong|th|td|figcaption|div)[^>]*>(.*?)<\/\1>/gis;
let tagMatch;
const untranslatedBlocks = [];
while ((tagMatch = tagRegex.exec(html)) !== null) {
  const fullTag = tagMatch[0];
  const tagContent = tagMatch[2].trim();
  // If the tag does not have data-i18n AND does not contain children with data-i18n AND contains spanish text > 4 chars
  if (!fullTag.includes('data-i18n=') && tagContent.length > 4 && !tagContent.startsWith('<svg') && !tagContent.startsWith('<img') && !tagContent.includes('<script')) {
    // Check if it has spanish words
    if (/[a-záéíóúñ]/i.test(tagContent) && !tagContent.includes('data-i18n')) {
      untranslatedBlocks.push(fullTag.replace(/\s+/g, ' ').substring(0, 100));
    }
  }
}

console.log(`Found ${untranslatedBlocks.length} untranslated candidate blocks:`);
untranslatedBlocks.slice(0, 30).forEach(b => console.log(' - ', b));
