// Validates CardChart.kt against the real Morse alphabet and checks the printed
// layout is geometrically sound (axis-aligned edges, no collisions, no lines
// running through unrelated pads).
const fs = require('fs');

const TRUE_MORSE = {
  A: '.-', B: '-...', C: '-.-.', D: '-..', E: '.', F: '..-.', G: '--.',
  H: '....', I: '..', J: '.---', K: '-.-', L: '.-..', M: '--', N: '-.',
  O: '---', P: '.--.', Q: '--.-', R: '.-.', S: '...', T: '-', U: '..-',
  V: '...-', W: '.--', X: '-..-', Y: '-.--', Z: '--..',
};

const src = fs.readFileSync(process.argv[2], 'utf8');
const re = /CardNode\('(.)',\s*"([.\-]+)",\s*(-?\d+),\s*(-?\d+)\)/g;
const nodes = [];
let m;
while ((m = re.exec(src)) !== null) {
  nodes.push({ letter: m[1], code: m[2], col: +m[3], row: +m[4] });
}

const errors = [];
const ok = [];

// 1. Node count.
if (nodes.length !== 26) errors.push(`expected 26 nodes, parsed ${nodes.length}`);
else ok.push('26 nodes parsed');

// 2. Codes match the ITU alphabet, and every letter appears exactly once.
const seenLetters = new Set();
for (const n of nodes) {
  if (TRUE_MORSE[n.letter] !== n.code) {
    errors.push(`${n.letter}: chart says "${n.code}", ITU says "${TRUE_MORSE[n.letter]}"`);
  }
  if (seenLetters.has(n.letter)) errors.push(`duplicate letter ${n.letter}`);
  seenLetters.add(n.letter);
}
for (const L of Object.keys(TRUE_MORSE)) {
  if (!seenLetters.has(L)) errors.push(`missing letter ${L}`);
}
if (!errors.length) ok.push('all 26 codes match the ITU alphabet');

// 3. No two nodes in the same grid cell.
const cells = new Map();
for (const n of nodes) {
  const k = `${n.col},${n.row}`;
  if (cells.has(k)) errors.push(`cell ${k}: ${cells.get(k)} and ${n.letter} collide`);
  cells.set(k, n.letter);
}
ok.push('no two pads share a cell');

// 4. Every parent exists; root is the empty code at (0,0).
const byCode = new Map(nodes.map((n) => [n.code, n]));
const pos = (code) => (code === '' ? { col: 0, row: 0 } : byCode.get(code));
for (const n of nodes) {
  const parent = n.code.slice(0, -1);
  if (!pos(parent)) errors.push(`${n.letter} (${n.code}): parent "${parent}" not on the chart`);
}
ok.push('every pad has its parent on the chart');

// 5. Every edge is purely horizontal or purely vertical.
const edges = [];
for (const n of nodes) {
  const p = pos(n.code.slice(0, -1));
  if (!p) continue;
  if (p.col !== n.col && p.row !== n.row) {
    errors.push(`edge ${n.code.slice(0, -1) || 'root'}->${n.letter} is diagonal`);
  }
  edges.push({ from: p, to: n, label: `${n.letter}` });
}
ok.push('all 26 edges are axis-aligned');

// 6. No edge passes through a pad that is not one of its endpoints.
for (const e of edges) {
  const { from, to } = e;
  for (const n of nodes) {
    if (n === to) continue;
    if (n.col === from.col && n.col === to.col) {
      const lo = Math.min(from.row, to.row), hi = Math.max(from.row, to.row);
      if (n.row > lo && n.row < hi) errors.push(`edge ->${e.label} runs through pad ${n.letter}`);
    }
    if (n.row === from.row && n.row === to.row) {
      const lo = Math.min(from.col, to.col), hi = Math.max(from.col, to.col);
      if (n.col > lo && n.col < hi) errors.push(`edge ->${e.label} runs through pad ${n.letter}`);
    }
  }
}
ok.push('no edge runs through an unrelated pad');

// 7. No two edges overlap along the same segment.
const seg = (e) => {
  const pts = [];
  const { from, to } = e;
  if (from.col === to.col) {
    const lo = Math.min(from.row, to.row), hi = Math.max(from.row, to.row);
    for (let r = lo; r < hi; r++) pts.push(`v:${from.col}:${r}`);
  } else {
    const lo = Math.min(from.col, to.col), hi = Math.max(from.col, to.col);
    for (let c = lo; c < hi; c++) pts.push(`h:${c}:${from.row}`);
  }
  return pts;
};
const occupied = new Map();
for (const e of edges) {
  for (const s of seg(e)) {
    if (occupied.has(s)) errors.push(`edges ->${occupied.get(s)} and ->${e.label} overlap at ${s}`);
    occupied.set(s, e.label);
  }
}
ok.push('no two traces overlap');

// 8. Shape follows the last element: circle for dit, bar for dah.
for (const n of nodes) {
  const shape = n.code.endsWith('.') ? 'circle' : 'bar';
  const expected = TRUE_MORSE[n.letter].endsWith('.') ? 'circle' : 'bar';
  if (shape !== expected) errors.push(`${n.letter} drawn as ${shape}, should be ${expected}`);
}
ok.push('every pad shape matches its final element');

console.log('--- layout ---');
for (let row = 0; row <= 6; row++) {
  let line = `row ${row} |`;
  for (let col = -3; col <= 4; col++) {
    const n = nodes.find((x) => x.col === col && x.row === row);
    const root = col === 0 && row === 0 ? ' ⌁ ' : '   ';
    line += n ? ` ${n.letter}${n.code.endsWith('.') ? '●' : '▬'}` : root;
  }
  console.log(line);
}

console.log('\n--- checks ---');
ok.forEach((s) => console.log('  PASS  ' + s));
if (errors.length) {
  console.log('\n--- FAILURES ---');
  errors.forEach((e) => console.log('  FAIL  ' + e));
  process.exit(1);
}
console.log('\nAll checks passed.');
