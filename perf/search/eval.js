// 질문 검색 정확도 측정 (결과·해석: 위키 "질문 검색 정확도 측정"): questions.json의 질문으로 실제 DB 검색을 돌려 후보 수(K)·최소 점수별 결과를 낸다
// 실행 (backend 폴더, compose의 MySQL이 떠 있을 때): node perf/search/eval.js
const { execFileSync } = require('child_process');
const { positives, paraphrases, negatives } = require('./questions.json');

// QuestionProcessor와 같은 MySQL FULLTEXT(ngram) 검색. 점수와 함께 상위 30개
function search(q) {
  const s = q.replace(/'/g, "''");
  const sql = `select article_id, match(title, description) against ('${s}' in natural language mode) score
    from news_articles where match(title, description) against ('${s}' in natural language mode)
    order by score desc limit 30`;
  const out = execFileSync('docker', ['compose', 'exec', '-T', 'mysql', 'mysql', '-unewsbrief', '-pnewsbrief',
    'newsbrief', '--default-character-set=utf8mb4', '-N', '-e', sql], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] });
  return out.trim().split('\n').filter(Boolean).map((l) => { const [id, score] = l.split('\t'); return { id: +id, score: +score }; });
}

const pos = positives.map((p) => ({ ...p, results: search(p.q) }));
const para = paraphrases.map((p) => ({ ...p, results: search(p.q) }));
const neg = negatives.map((q) => ({ q, results: search(q) }));

console.log('\n질문별: 정답 기사의 최고 순위·점수 / 1위 점수');
for (const p of pos) {
  const hit = p.results.findIndex((r) => p.expected.includes(r.id));
  const h = hit < 0 ? '못 찾음(30위 밖)' : `${hit + 1}위, 점수 ${p.results[hit].score.toFixed(2)}`;
  console.log(`  ${p.q.padEnd(30)} ${h}   (1위 ${p.results[0]?.score.toFixed(2) ?? '-'})`);
}
console.log('\n정답이 없어야 하는 질문: 1위 점수');
for (const n of neg) console.log(`  ${n.q.padEnd(30)} ${n.results[0]?.score.toFixed(2) ?? '결과 없음'} (후보 ${n.results.length}개)`);

console.log('\nK(후보 수)·최소 점수별 결과');
console.log('  K   최소점수  정답포함(20)  다른표현 정답포함(6)  정답없는질문 거절(5)  평균 후보 수');
for (const k of [8, 15]) {
  for (const min of [0, 1, 2, 3, 4, 5, 6, 8]) {
    const kept = (rs) => rs.filter((r) => r.score >= min).slice(0, k);
    const found = pos.filter((p) => kept(p.results).some((r) => p.expected.includes(r.id))).length;
    const foundPara = para.filter((p) => kept(p.results).some((r) => p.expected.includes(r.id))).length;
    const rejected = neg.filter((n) => kept(n.results).length === 0).length;
    const avg = pos.reduce((a, p) => a + kept(p.results).length, 0) / pos.length;
    console.log(`  ${String(k).padEnd(4)}${String(min).padEnd(9)}${String(found).padEnd(14)}${String(foundPara).padEnd(22)}${String(rejected).padEnd(22)}${avg.toFixed(1)}`);
  }
}
