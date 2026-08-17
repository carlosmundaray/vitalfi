const fs = require('fs');

const indexHtml = fs.readFileSync('c:\\xampp\\htdocs\\vitalfi\\landing\\index.html', 'utf-8');
const i18nJs = fs.readFileSync('c:\\xampp\\htdocs\\vitalfi\\landing\\i18n.js', 'utf-8');

(0, eval)(i18nJs.replace('const TRANSLATIONS', 'global.TRANSLATIONS'));

console.log("ES Object:", TRANSLATIONS["es"]["object.title"], "->", TRANSLATIONS["es"]["object.body"].substring(0, 30));
console.log("ZH Object:", TRANSLATIONS["zh"]["object.title"], "->", TRANSLATIONS["zh"]["object.body"].substring(0, 30));
console.log("EN Object:", TRANSLATIONS["en"]["object.title"], "->", TRANSLATIONS["en"]["object.body"].substring(0, 30));

const hasTitleTag = indexHtml.includes('data-i18n="object.title"');
const hasBodyTag = indexHtml.includes('data-i18n="object.body"');

console.log("HTML has object.title data-i18n:", hasTitleTag);
console.log("HTML has object.body data-i18n:", hasBodyTag);
