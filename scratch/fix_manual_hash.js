const fs = require('fs');

if (fs.existsSync('landing/manual.html')) {
  let manual = fs.readFileSync('landing/manual.html', 'utf8');
  manual = manual.replace(/href="#(cap[1-6])"/g, 'href="javascript:void(0)" onclick="scrollToSection(\'$1\')"');
  
  const helperScript = `
  <script>
    function scrollToSection(id) {
      const el = document.getElementById(id);
      if (el) {
        el.scrollIntoView({ behavior: 'smooth' });
      }
    }

    if (window.location.protocol === 'file:' && window.location.hash) {
      const hashId = window.location.hash.substring(1);
      try {
        history.replaceState(null, '', window.location.pathname);
      } catch (_) {}
      setTimeout(() => scrollToSection(hashId), 150);
    }
  </script>
`;
  manual = manual.replace('</body>', helperScript + '\n</body>');
  fs.writeFileSync('landing/manual.html', manual, 'utf8');
  console.log('Successfully updated landing/manual.html');
}
