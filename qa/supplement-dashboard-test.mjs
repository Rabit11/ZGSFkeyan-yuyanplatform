import assert from 'node:assert/strict';import path from 'node:path';import {createRequire} from 'node:module';
const require=createRequire(path.resolve('frontend/package.json'));
const {outputFiles}=require('esbuild').buildSync({entryPoints:['frontend/src/utils/dashboardAgg.ts'],bundle:true,platform:'node',format:'cjs',write:false,tsconfig:'frontend/tsconfig.json'});
const module={exports:{}};new Function('module','exports','require',outputFiles[0].text)(module,module.exports,require);
const p={id:1,name:'原始项目',totalFund:10,status:'IMPLEMENTING',dataSource:'FORM_MAINT',supplement:{status:'IN_REVIEW',pending:1,approved:2,total:5,approvedSections:[{key:'basic',values:{name:'已审核项目'}},{key:'fund',rows:[{totalFund:100,year:2026,yearBudget:30,amount:12,recordType:'支出'},{totalFund:100,year:2026,yearBudget:30,amount:12,recordType:'核销'}]},{key:'deliverable',rows:[{type:'专利',name:'测试专利',status:'已交付'}]}]}};
const d=module.exports.aggregateDashboard({year:2026},{projects:[p],deliverables:[{projectId:1,type:'PATENT',status:'DELIVERED'}]});
assert.equal(d.totalFund,100);assert.equal(d.yearExpense,12);assert.equal(d.deliverableCount,1);assert.equal(d.supplementReview.pending,1);assert.equal(d.supplementReview.projects[0].name,'已审核项目');
const empty=module.exports.aggregateDashboard({year:2026,orgOffice:'不匹配'},{projects:[p]});assert.equal(empty.supplementReview.pending,0);
console.log('PASS: dashboard approved data, no duplicate deliverables, expense, pending summary follows filters');
