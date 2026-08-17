const fs = require('fs');
let html = fs.readFileSync('landing/index.html', 'utf8');

const regex = /\.badge-legacy\s*\{\s*\.action-group/;
const replacement = `.badge-legacy {
      background: rgba(168, 196, 182, 0.1);
      color: var(--muted);
      border: 1px solid rgba(168, 196, 182, 0.2);
    }

    .feature-pills {
      display: flex;
      flex-wrap: wrap;
      gap: 0.5rem;
      margin: 1.25rem 0;
    }

    .pill {
      background: rgba(0, 230, 184, 0.06);
      border: 1px solid var(--line);
      border-radius: 4px;
      padding: 0.4rem 0.75rem;
      font-size: 0.85rem;
      color: var(--ink);
    }

    .releases-table-container {
      margin-top: 2rem;
      overflow-x: auto;
      -webkit-overflow-scrolling: touch;
      border: 1px solid var(--line);
      border-radius: 4px;
      background: var(--panel);
    }

    .releases-table {
      width: 100%;
      min-width: 580px;
      border-collapse: collapse;
      text-align: left;
      font-size: 0.92rem;
    }

    .releases-table th,
    .releases-table td {
      padding: 0.9rem 1rem;
      border-bottom: 1px solid rgba(0, 230, 184, 0.1);
    }

    .releases-table th {
      background: rgba(0, 230, 184, 0.06);
      color: var(--cyan);
      font-weight: 700;
      font-size: 0.8rem;
      text-transform: uppercase;
      letter-spacing: 0.05em;
    }

    .releases-table tr:last-child td {
      border-bottom: 0;
    }

    .releases-table tr:hover td {
      background: rgba(0, 230, 184, 0.03);
    }

    .action-group`;

html = html.replace(regex, replacement);
fs.writeFileSync('landing/index.html', html, 'utf8');
console.log('Successfully fixed CSS cleanly via scratch/fix_regex.js');
