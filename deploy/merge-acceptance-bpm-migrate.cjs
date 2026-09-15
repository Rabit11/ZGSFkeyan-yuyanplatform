// Additive, repeatable upgrade. Run on a backed-up database; never executes account cleanup or seed data.
const fs = require('fs'), cp = require('child_process'), path = require('path');
const db = process.argv[2];
if (!/^rpm(?:_merge_qa_20260915)?$/.test(db || '')) throw Error('Unexpected database');
const sql = q => cp.execFileSync('mysql', ['--default-character-set=utf8mb4', '-N', db, '-e', q]).toString().trim();
const before = sql('SELECT COUNT(*) FROM sys_user');
let added = 0;
for (const file of ['migration_impl_stage_v2.sql', 'migration_acceptance_material_file_v1.sql']) {
  const source = fs.readFileSync(path.join(__dirname, '../backend/src/main/resources/db', file), 'utf8').replace(/^\s*--.*$/gm, '');
  for (const statement of source.split(';').map(s => s.trim()).filter(Boolean)) {
    if (/^SET NAMES/i.test(statement)) continue;
    if (/^CREATE TABLE IF NOT EXISTS/i.test(statement)) { sql(statement); continue; }
    const match = statement.match(/^ALTER TABLE\s+`?(\w+)`?\s+([\s\S]+)$/i);
    if (!match) throw Error('Unsupported migration statement');
    const table = match[1];
    for (const clause of match[2].split(/,\s*\n/).map(s => s.trim())) {
      const column = clause.match(/^ADD COLUMN\s+`?(\w+)`?/i);
      const index = clause.match(/^ADD KEY\s+`?(\w+)`?/i);
      if (!column && !index) throw Error('Unsupported ALTER clause');
      const kind = column ? 'columns' : 'statistics', key = column ? 'COLUMN_NAME' : 'INDEX_NAME', name = (column || index)[1];
      const count = sql(`SELECT COUNT(*) FROM information_schema.${kind} WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='${table}' AND ${key}='${name}'`);
      if (count === '0') { sql('ALTER TABLE `' + table + '` ' + clause); added++; }
    }
  }
}
sql("UPDATE proj_acceptance SET current_node=CASE status WHEN 'DONE' THEN 'ACCEPT_ARCHIVE' WHEN 'APPLYING' THEN 'ACCEPT_UNIT_REVIEW' WHEN 'ACCEPTING' THEN IF(expert_review=1,'ACCEPT_CHIEF_REVIEW','ACCEPT_HQ_TECH') ELSE 'ACCEPT_GATE' END WHERE current_node IS NULL");
if (before !== sql('SELECT COUNT(*) FROM sys_user')) throw Error('User count changed');
console.log('MIGRATION_PASS database=' + db + ' added=' + added + ' users_preserved=' + before);
