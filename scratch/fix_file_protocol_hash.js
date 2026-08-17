const fs = require('fs');

let html = fs.readFileSync('landing/index.html', 'utf8');

// 1. Clean nav links
html = html.replace('<a href="#" class="nav-brand">', '<a href="javascript:void(0)" onclick="window.scrollTo({top: 0, behavior: \'smooth\'})" class="nav-brand">');
html = html.replace('<a href="#about" class="nav-link"', '<a href="javascript:void(0)" onclick="scrollToSection(\'about\')" class="nav-link"');
html = html.replace('<a href="#versions" class="nav-link"', '<a href="javascript:void(0)" onclick="scrollToSection(\'versions\')" class="nav-link"');
html = html.replace('<a href="#contact" class="nav-link"', '<a href="javascript:void(0)" onclick="scrollToSection(\'contact\')" class="nav-link"');

// 2. Clean hero CTA button
html = html.replace('<a class="btn btn-secondary" href="#versions"', '<a class="btn btn-secondary" href="javascript:void(0)" onclick="scrollToSection(\'versions\')"');

// 3. Add clean scrollToSection helper and initial hash cleaner in the bottom script
const helperScript = `
  <script>
    function scrollToSection(id) {
      const el = document.getElementById(id);
      if (el) {
        el.scrollIntoView({ behavior: 'smooth' });
      }
    }

    // Clean hash in file:/// protocol to prevent Chromium unique security origin warnings
    if (window.location.protocol === 'file:' && window.location.hash) {
      const hashId = window.location.hash.substring(1);
      try {
        history.replaceState(null, '', window.location.pathname);
      } catch (_) {}
      setTimeout(() => scrollToSection(hashId), 150);
    }
  </script>
`;

html = html.replace('</body>', helperScript + '\n</body>');

fs.writeFileSync('landing/index.html', html, 'utf8');
console.log('Successfully updated landing/index.html to remove hash navigation warnings');
