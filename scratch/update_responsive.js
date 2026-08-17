const fs = require('fs');

let html = fs.readFileSync('landing/index.html', 'utf8');

const responsiveCSS = `
    /* ===================================================
       RESPONSIVE DESIGN SYSTEM (MOBILE, TABLET & DESKTOP)
       =================================================== */

    /* —— MOBILE BASE (max-width: 639px) —— */
    @media (max-width: 639px) {
      :root {
        --pad-x: 1rem;
        --pad-y: 1.25rem;
      }

      .nav-link {
        display: none; /* Barra superior limpia y tactil */
      }

      .nav-actions {
        gap: 0.5rem;
      }

      .lang-selector-btn {
        padding: 0.35rem 0.65rem;
        font-size: 0.82rem;
      }

      .hero {
        min-height: auto;
        padding-top: calc(1rem + var(--safe-t));
        padding-bottom: 2rem;
        align-items: flex-start;
      }

      .hero-inner {
        grid-template-columns: 1fr;
        gap: 1.25rem;
        justify-items: center;
        text-align: center;
      }

      .hero-logo {
        width: min(44vw, 150px);
        margin: 0 auto;
      }

      .hero-copy {
        text-align: center;
      }

      .hero-title {
        font-size: clamp(2.1rem, 9vw, 2.75rem);
        line-height: 1.1;
      }

      .lede {
        font-size: 1rem;
        max-width: 100%;
        margin-inline: auto;
      }

      .sonar {
        width: min(90vw, 320px);
        opacity: 0.22;
        right: -30%;
        top: 15%;
        transform: none;
      }

      .cta {
        flex-direction: column;
        align-items: stretch;
        width: 100%;
        gap: 0.65rem;
      }

      .btn {
        width: 100%;
        min-height: 3rem;
        justify-content: center;
        font-size: 0.94rem;
        padding: 0.75rem 1rem;
      }

      .release-header {
        flex-direction: column;
        align-items: flex-start;
        gap: 0.4rem;
      }

      .featured-release {
        padding: 1.15rem 1rem;
      }

      .featured-release .cta {
        flex-direction: column;
        width: 100%;
      }

      .feature-pills {
        gap: 0.35rem;
      }

      .pill {
        font-size: 0.78rem;
        padding: 0.3rem 0.55rem;
      }

      .team {
        grid-template-columns: 1fr;
      }

      .contact-grid {
        grid-template-columns: 1fr;
        gap: 0.85rem;
      }

      .contact-card {
        padding: 1.15rem 1rem;
      }

      .footer-cta {
        padding-block: 2rem;
      }

      .footer-cta .shell {
        flex-direction: column;
        align-items: stretch;
        text-align: center;
        gap: 1.25rem;
      }

      .footer-cta h2 {
        max-width: 100%;
        font-size: 1.6rem;
      }

      .footer-cta .btn {
        width: 100%;
      }

      footer.site-footer {
        flex-direction: column;
        align-items: flex-start;
        gap: 0.75rem;
        font-size: 0.8rem;
      }

      .lang-modal-dialog {
        width: calc(100vw - 1.5rem);
        max-height: 88vh;
      }

      .lang-grid {
        grid-template-columns: 1fr;
        padding: 0.75rem 1rem 1.25rem;
      }
    }

    /* —— TABLET (640px to 899px) —— */
    @media (min-width: 640px) and (max-width: 899px) {
      :root {
        --pad-x: 1.75rem;
      }

      .nav-link {
        font-size: 0.88rem;
      }

      .nav-actions {
        gap: 0.85rem;
      }

      .hero-inner {
        grid-template-columns: 1.2fr auto;
        gap: 2rem;
        align-items: center;
        text-align: left;
      }

      .hero-logo {
        width: 180px;
        grid-column: 2;
        grid-row: 1;
      }

      .hero-copy {
        grid-column: 1;
        grid-row: 1;
        text-align: left;
      }

      .team {
        grid-template-columns: 1fr 1fr;
        gap: 1rem;
      }

      .contact-grid {
        grid-template-columns: 1fr 1fr;
      }
    }

    /* —— DESKTOP (min-width: 900px) —— */
    @media (min-width: 900px) {
      :root {
        --pad-x: 2.5rem;
      }

      .hero-inner {
        grid-template-columns: minmax(0, 1.2fr) auto;
        gap: 3.5rem;
        align-items: center;
        text-align: left;
      }

      .hero-logo {
        width: clamp(200px, 24vw, 270px);
        grid-column: 2;
        grid-row: 1;
        justify-self: end;
      }

      .hero-copy {
        grid-column: 1;
        grid-row: 1;
        text-align: left;
      }

      .cta {
        width: auto;
      }

      .btn {
        width: auto;
      }

      .team {
        grid-template-columns: 1fr 1fr 1fr;
        gap: 0 1.5rem;
      }

      .member {
        border-bottom: 1px solid var(--line);
      }

      .contact-grid {
        grid-template-columns: 1fr 1fr;
      }
    }
`;

const mediaRegex = /@media \(max-width: 719px\)[\s\S]*?@media \(min-width: 900px\) \{[\s\S]*?\.member \{[\s\S]*?\}[\s\S]*?\}/;
if (mediaRegex.test(html)) {
  html = html.replace(mediaRegex, responsiveCSS.trim());
  fs.writeFileSync('landing/index.html', html, 'utf8');
  console.log('Successfully updated responsive CSS in landing/index.html');
} else {
  console.log('Media block not matched by regex');
}
