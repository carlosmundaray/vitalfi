const fs = require('fs');
let html = fs.readFileSync('landing/index.html', 'utf8');

const target = `    // Smooth scroll for anchor navigation (safe for file:/// and http://)
    document.querySelectorAll('a[href^="#"]').forEach((anchor) => {
      anchor.addEventListener("click", function(e) {
        const targetId = this.getAttribute("href");
        if (targetId && targetId.length > 1) {
          const target = document.querySelector(targetId);
          if (target) {
            e.preventDefault();
            target.scrollIntoView({ behavior: "smooth" });
          }
        }
      });
    });`;

const replacement = `    // Smooth scroll for anchor navigation (safe for file:/// and http://)
    document.querySelectorAll('a[href^="#"]').forEach((anchor) => {
      anchor.addEventListener("click", function(e) {
        const targetId = this.getAttribute("href");
        if (targetId && targetId.length > 1) {
          const target = document.querySelector(targetId);
          if (target) {
            e.preventDefault();
            target.scrollIntoView({ behavior: "smooth" });
          }
        }
      });
    });

    if (window.location.hash) {
      setTimeout(() => {
        try {
          const target = document.querySelector(window.location.hash);
          if (target) target.scrollIntoView({ behavior: "smooth" });
        } catch (_) {}
      }, 100);
    }`;

html = html.replace(target, replacement);
fs.writeFileSync('landing/index.html', html, 'utf8');
console.log('Successfully updated landing/index.html with safe hash scrolling');
