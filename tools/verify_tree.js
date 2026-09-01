// Mirrors ChartLayouts.tidyTree and checks the generated layouts are sane:
// every character present exactly once, no two pads sharing a cell, every parent
// centred over its children, dits left of dahs, and the width within reason.

const LETTERS = {
  A:'.-',B:'-...',C:'-.-.',D:'-..',E:'.',F:'..-.',G:'--.',H:'....',I:'..',J:'.---',
  K:'-.-',L:'.-..',M:'--',N:'-.',O:'---',P:'.--.',Q:'--.-',R:'.-.',S:'...',T:'-',
  U:'..-',V:'...-',W:'.--',X:'-..-',Y:'-.--',Z:'--..',
};
const DIGITS = {
  1:'.----',2:'..---',3:'...--',4:'....-',5:'.....',
  6:'-....',7:'--...',8:'---..',9:'----.',0:'-----',
};
const PUNCT = {
  '.':'.-.-.-',',':'--..--','?':'..--..',"'":'.----.','!':'-.-.--','/':'-..-.',
  '(':'-.--.',')':'-.--.-','&':'.-...',':':'---...',';':'-.-.-.','=':'-...-',
  '+':'.-.-.','-':'-....-','_':'..--.-','"':'.-..-.','$':'...-..-','@':'.--.-.',
};

function tidyTree(chars) {
  const labelOf = {};
  for (const [k, v] of Object.entries(chars)) labelOf[v] = String(k);

  const present = new Set();
  for (const code of Object.values(chars)) {
    for (let i = 1; i <= code.length; i++) present.add(code.substring(0, i));
  }

  const xs = new Map();
  let nextLeaf = 0;
  const stack = [['', false]];
  while (stack.length) {
    const [code, expanded] = stack.pop();
    const kids = [code + '.', code + '-'].filter((k) => present.has(k));
    if (!kids.length) { xs.set(code, nextLeaf); nextLeaf += 1; continue; }
    if (!expanded) {
      stack.push([code, true]);
      for (const kid of [...kids].reverse()) stack.push([kid, false]);
    } else {
      const kx = kids.map((k) => xs.get(k));
      xs.set(code, (Math.min(...kx) + Math.max(...kx)) / 2);
    }
  }

  const nodes = [...present].map((code) => ({
    code, label: labelOf[code] || '', x: xs.get(code), y: code.length,
  }));
  return { nodes, rootX: xs.get(''), columns: nextLeaf, rows: Math.max(...nodes.map((n) => n.y)) + 1, present, xs };
}

let failures = 0;
function check(cond, msg) {
  console.log(`  ${cond ? 'PASS' : 'FAIL'}  ${msg}`);
  if (!cond) failures++;
}

for (const [name, chars] of [
  ['letters + digits', { ...LETTERS, ...DIGITS }],
  ['everything', { ...LETTERS, ...DIGITS, ...PUNCT }],
]) {
  console.log(`\n=== ${name} ===`);
  const t = tidyTree(chars);
  const charCount = Object.keys(chars).length;
  const labelled = t.nodes.filter((n) => n.label !== '');

  console.log(`  nodes ${t.nodes.length}, characters ${labelled.length}, ` +
    `junctions ${t.nodes.length - labelled.length}, ${t.columns} cols x ${t.rows} rows`);

  check(labelled.length === charCount, `all ${charCount} characters placed`);

  const codes = new Set(t.nodes.map((n) => n.code));
  check(codes.size === t.nodes.length, 'no duplicate codes');

  // Every character's code round-trips to the right pad.
  let roundTrip = true;
  for (const [ch, code] of Object.entries(chars)) {
    const n = t.nodes.find((m) => m.code === code);
    if (!n || n.label !== String(ch)) roundTrip = false;
  }
  check(roundTrip, 'every character sits on its own code');

  // No two pads in the same cell.
  const cells = new Set(t.nodes.map((n) => `${n.x},${n.y}`));
  check(cells.size === t.nodes.length, 'no two pads share a cell');

  // Every non-root node's parent exists.
  const missing = t.nodes.filter((n) => n.code.length > 1 && !codes.has(n.code.slice(0, -1)));
  check(missing.length === 0, 'every pad has its parent on the chart');

  // Parents centred over their children; dit strictly left of dah.
  let centred = true, ordered = true;
  for (const n of [...t.nodes, { code: '', x: t.rootX, y: 0 }]) {
    const kids = [n.code + '.', n.code + '-'].filter((k) => codes.has(k))
      .map((k) => t.nodes.find((m) => m.code === k));
    if (!kids.length) continue;
    const mid = (Math.min(...kids.map((k) => k.x)) + Math.max(...kids.map((k) => k.x))) / 2;
    if (Math.abs(mid - n.x) > 1e-6) centred = false;
    if (kids.length === 2 && !(kids[0].x < kids[1].x)) ordered = false;
  }
  check(centred, 'every parent centred over its children');
  check(ordered, 'dit branch always left of dah branch');

  check(t.columns <= 32, `width ${t.columns} columns stays under 32`);
}

console.log(failures ? `\n${failures} FAILURE(S)` : '\nAll checks passed.');
process.exit(failures ? 1 : 0);
