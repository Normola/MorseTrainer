// Mirrors Timing.kt and checks the standard and Farnsworth cases against known values.
const round = (x) => Math.round(x);

function timing(charWpm, effWpm) {
  const c = Math.min(60, Math.max(5, charWpm));
  const s = Math.min(c, Math.max(5, effWpm));
  const dit = round(1200 / c);
  const pad = (60 * c - 37.2 * s) / (c * s);
  return {
    c, s, dit, dah: dit * 3,
    charGap: s >= c ? dit * 3 : round((pad * 3) / 19 * 1000),
    wordGap: s >= c ? dit * 7 : round((pad * 7) / 19 * 1000),
  };
}

// Time for one "PARIS " word: 50 units at standard spacing.
function parisMs(t) {
  const word = 'PARIS';
  const CODES = { P: '.--.', A: '.-', R: '.-.', I: '..', S: '...' };
  let total = 0;
  word.split('').forEach((ch, i) => {
    const code = CODES[ch];
    code.split('').forEach((sym, j) => {
      total += sym === '.' ? t.dit : t.dah;
      if (j !== code.length - 1) total += t.dit;
    });
    if (i !== word.length - 1) total += t.charGap;
  });
  total += t.wordGap; // trailing word space
  return total;
}

const fails = [];
const check = (label, actual, expected, tolMs) => {
  const ok = Math.abs(actual - expected) <= tolMs;
  console.log(`  ${ok ? 'PASS' : 'FAIL'}  ${label}: ${actual} (expected ~${expected})`);
  if (!ok) fails.push(label);
};

console.log('--- standard spacing, 20 wpm ---');
let t = timing(20, 20);
check('dit', t.dit, 60, 1);
check('char gap = 3 units', t.charGap, 180, 2);
check('word gap = 7 units', t.wordGap, 420, 2);
check('PARIS takes 60s/20wpm = 3000ms', parisMs(t), 3000, 5);

console.log('\n--- Farnsworth 18/12 ---');
t = timing(18, 12);
check('dit still at 18 wpm', t.dit, round(1200 / 18), 1);
check('PARIS takes 60s/12wpm = 5000ms', parisMs(t), 5000, 20);
console.log(`  info  char gap ${t.charGap} ms, word gap ${t.wordGap} ms`);
console.log(`  info  word gap / char gap = ${(t.wordGap / t.charGap).toFixed(3)} (should be 2.333)`);

console.log('\n--- Farnsworth 20/5 (very slow overall) ---');
t = timing(20, 5);
check('PARIS takes 60s/5wpm = 12000ms', parisMs(t), 12000, 30);

console.log('\n--- word-gap accounting in the player ---');
// The player emits charGap after every character including the space, so the
// space itself must only contribute the remainder.
[[20, 20], [18, 12], [20, 5]].forEach(([c, s]) => {
  const tt = timing(c, s);
  const remainder = Math.max(0, tt.wordGap - tt.charGap * 2);
  const totalBetweenWords = tt.charGap + remainder + tt.charGap;
  check(`${c}/${s}: gap between words equals wordGap`, totalBetweenWords, tt.wordGap, 1);
});

console.log(fails.length ? `\n${fails.length} FAILURE(S)` : '\nAll checks passed.');
process.exit(fails.length ? 1 : 0);
