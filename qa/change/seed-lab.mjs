// Idempotent, persistent scenario projects. Never runs against the formal database.
import fs from 'node:fs';
import { fixture, clean, sql, quote, root } from './helpers.mjs';
if (process.env.CHANGE_QA_DB !== 'rpm_change_lab') throw new Error('Lab database required');
const scenarios = JSON.parse(fs.readFileSync(root + 'frontend/src/views/implement/change/scenarios.json', 'utf8'));
if (process.argv.includes('--reset')) {
  const ids = sql("SELECT id FROM proj_info WHERE project_no IN (" + scenarios.map(s => quote(`LAB_CHANGE_${s.id}`)).join(',') + ")");
  if (ids) clean(ids.split('\n').map(id => ({id:Number(id)})), {labScenarios:true});
}
for (const s of scenarios) {
  const key = `LAB_CHANGE_${s.id}`;
  if (sql(`SELECT id FROM proj_info WHERE project_no=${quote(key)} AND deleted=0`)) continue;
  const f = fixture(s.channel, `【${s.id} 演练】${s.title}`);
  sql(`UPDATE proj_info SET project_no=${quote(key)} WHERE id=${f.id}`);
  console.log('Seeded', s.id, f.id);
}
