# Design System — VitalFi

> Category: Rescue Tech / Field Ops
> Wireless vital-signal detection under rubble. Signal cyan on deep terrain green; coral mark of life.

## 1. Visual Theme & Atmosphere

VitalFi feels like a field instrument opened under night floodlights: calm, urgent, and readable at a glance. The canvas is deep forest charcoal (`#07120e`), not pure black — warm enough for long reading, dark enough for a rescue-ops mood. Signal cyan (`#00e6b8`) is the brand pulse: CTAs, links, and one hero accent only. Coral (`#ff3d68`) from the logo mark is reserved for “life detected” moments — never for decoration floods.

Typography pairs a serif display (`Instrument Serif`) for the brand and section titles with a clean geometric sans (`Manrope`) for body and UI. Headlines are large and quiet; body text stays high-contrast muted green-gray so volunteers can scan on a phone outdoors.

Motion is sparse: a slow sonar pulse in the hero and a short rise-in on first paint. No glassmorphism, no purple SaaS gradients, no pill clouds of badges.

**Key Characteristics:**
- Deep terrain green background (`#07120e`) with subtle grid/sonar atmosphere
- Cyan used sparingly: primary CTA + links + one hero brand accent
- Coral only for life/vital accents tied to the logo mark
- Instrument Serif for brand + section titles; Manrope for body/UI
- Minimal elevation; hairline borders in cyan-tint alpha
- Full-bleed hero; content shell max-width 1080px
- Mobile-first: stacked CTAs, readable 16px+ body

## 2. Color Palette & Roles

### Primary
- **Terrain** (`#07120e`): Page background, immersive base.
- **Ink** (`#e8f6ef`): Primary text, headings on dark.
- **Signal Cyan** (`#00e6b8`): Brand accent, primary CTA fill, links.
- **Cyan Hover** (`#5ff0d0`): Primary button hover.
- **Cyan Deep** (`#00a88a`): Footer links, quieter interactive.

### Accent
- **Life Coral** (`#ff3d68`): Logo mark / vital accent only (sparingly).
- **Sand** (`#d4c09a`): Eyebrow labels, experimental notice tone.

### Neutral / Surface
- **Muted** (`#a8c4b6`): Body copy (higher contrast than prior `#8eaea0`).
- **Line** (`rgba(0, 230, 184, 0.18)`): Dividers, secondary button borders.
- **Panel** (`#0c1a14`): Optional photo wells / dense blocks.
- **CTA Ink** (`#042018`): Text on cyan primary buttons.

### Forbidden
- Purple/indigo gradients, pure black `#000`, neon glow stacks, warm cream `#F4F1EA` editorial tropes.

## 3. Typography Rules

### Font Family
- **Display**: `Instrument Serif`, fallbacks: `Georgia, serif`
- **Body / UI**: `Manrope`, fallbacks: `system-ui, sans-serif`

### Hierarchy

| Role | Font | Size | Weight | Line Height | Letter Spacing | Notes |
|------|------|------|--------|-------------|----------------|-------|
| Brand Hero | Instrument Serif | clamp(3rem, 11vw, 5.6rem) | 400 | 0.95 | -0.03em | Wordmark; italic accent on “Fi” in cyan |
| Section Title | Instrument Serif | clamp(1.75rem, 4vw, 2.35rem) | 400 | 1.1 | -0.02em | One purpose per section |
| Lede | Manrope | clamp(1.05rem, 2.6vw, 1.25rem) | 500 | 1.5 | normal | Hero supporting sentence |
| Body | Manrope | 1.05rem | 400 | 1.6 | normal | Mission, story, lists |
| Label / Tag | Manrope | 0.72rem | 600 | 1.2 | 0.12em | Uppercase eyebrows |
| Button | Manrope | 0.95rem | 700 | 1 | normal | CTA labels |
| Caption | Manrope | 0.85rem | 400 | 1.4 | normal | Photo captions, footer |

### Principles
- Brand name is the strongest type signal in the first viewport — stronger than the headline sentence.
- Body never drops below ~0.95rem; muted color must remain readable outdoors (`#a8c4b6` minimum).
- One section = one H2 + short support; avoid competing bold blocks.

## 4. Layout Principles

- **Shell**: `max-width: 1080px`, horizontal padding `1.25rem` → `2.5rem` desktop.
- **Hero**: full viewport height (`100dvh`), full-bleed visual plane; no inset media cards.
- **Hero budget**: brand, one lede, one CTA group, logo mark — nothing else above the fold.
- **Section rhythm**: `padding-block: clamp(2.25rem, 6vw, 3.5rem)` with hairline top border.
- **Lists**: border-separated rows, not cards (unless interactive).
- **Team**: photo full-bleed within shell; members as list rows / 3-col desktop.
- **No cards in hero.** Cards elsewhere only if interaction requires a container.

## 5. Component Stylings

### Buttons
**Primary**
- Background: `#00e6b8`; text: `#042018`
- Min-height: 3rem; padding: 0.8rem 1.35rem; radius: 2px
- Hover: `#5ff0d0`, slight `translateY(-2px)`

**Secondary**
- Transparent; text `#e8f6ef`; border `1px solid` Line
- Hover: border + text → Signal Cyan

### Links
- Color: Signal Cyan; weight 600; underline on hover only

### Notice
- Sand text; top hairline in sand alpha; experimental disclaimer

### Depth & Elevation
- Minimal. Prefer borders over shadows. Soft ambient only on team photo well if needed: `0 0 0 1px Line`.

## 6. Motion

1. Hero content `riseIn` 0.75s ease-out on load.
2. Sonar rings pulse 4.8s ease-out infinite (decorative, `aria-hidden`).
3. Button hover lift 0.2s.
Respect `prefers-reduced-motion: reduce`.

## 7. Responsive Behavior

- **<720px**: centered hero; full-width stacked CTAs; sonar opacity ~0.28.
- **≥720px**: two-column hero (copy | logo); left-aligned.
- **≥900px**: team members 3 columns.

## 8. Agent Prompt Guide

When generating or editing the VitalFi landing:
1. Read this `DESIGN.md` first; do not invent new hex tokens.
2. Keep Spanish copy tone: direct, field-serious, hopeful — never hype SaaS.
3. Tag editable nodes with `data-od-id` for Open Design comment mode.
4. Single self-contained `index.html` with inlined CSS; external assets only for logo/photo/APK/PDF.
5. Accent cyan: primary CTA fill + links + italic “Fi”; coral only if referencing the logo life mark.
6. No pricing section. Include: Hero, Qué es, Misión/Visión/Objeto, Valores, Audiencia, Instala, Manual, Equipo, Footer CTA band, Footer.
7. APK version in copy must match the file under `releases/`.
