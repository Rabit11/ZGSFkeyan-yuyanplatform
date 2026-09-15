export const supplementStatusText: Record<string,string> = {DRAFT:'待补录',IN_REVIEW:'待审核',UNIT_REVIEW:'单位待审核',HQ_REVIEW:'总部待审核',RETURNED:'已退回',APPROVED:'已通过',PARTIAL:'部分通过'}
export const supplementStatusColor: Record<string,string> = {DRAFT:'default',IN_REVIEW:'processing',UNIT_REVIEW:'processing',HQ_REVIEW:'processing',RETURNED:'error',APPROVED:'success',PARTIAL:'warning'}
const accepted = (p:any,key:string) => p.supplement?.approvedSections?.find((s:any)=>s.key===key)
const done = (r:any) => ['DONE','DELIVERED','已完成','已交付','已完成转化'].includes(r.status)
// Complete section snapshots include seeded business rows: replace once, never append them twice.
export function publishedRows(projects:any[],native:any[],key:string) {
 const replaced=new Set(projects.filter(p=>accepted(p,key)).map(p=>p.id))
 return [...native.filter(r=>!replaced.has(r.projectId)),...projects.flatMap(p=>(accepted(p,key)?.rows||[]).filter((r:any)=>r.status!=='暂无成果').map((r:any)=>({
  ...r,id:`supplement-${p.id}-${key}-${r._rowId}`,projectId:p.id,projectName:p.name,
  status:done(r)?(key==='deliverable'?'DELIVERED':'DONE'):({'未启动转化':'NOT_STARTED','技术储备':'NOT_STARTED','转化中':'NEGOTIATING'} as any)[r.status]||r.status,
  type:({'专利':'PATENT','论文':'PAPER','软件著作权':'SOFTWARE','技术标准':'STANDARD','原理样机':'PROTOTYPE','设备':'EQUIPMENT','成套技术成果':'TECH_PACKAGE'} as any)[r.type]||r.type,
  transformWay:r.path,transformForm:r.form,intro:r.description,
  deliverDate:r.actualDate, dueDate:r.planDate, finishDate:r.actualDate, occurredDate:r.occurredDate,
  occurDate:r.occurredDate,year:Number(r.year),amount:Number(r.amount||0),
 })))]
}
export function publishedProject(source:any,year=new Date().getFullYear()) {
 const p={...source}; const basic=accepted(p,'basic')?.values||{}
 for(const field of ['name','projectType','major1','major2','orgName','manageOrgName','bureauOffice','leadOrgName','startDate','endDate','goal'])
  if(basic[field]!==undefined && basic[field]!==null)p[field]=basic[field]
 const fund=accepted(p,'fund')
 if(fund) {
  const rows=fund.rows||[]
  // Project totals repeated on rows are not additive; inconsistent totals require reconciliation.
  for(const field of ['totalFund','nationalFund','selfFund']) {
   const values=[...new Set(rows.map((r:any)=>r[field]).filter((v:any)=>v!==''&&v!==null&&v!==undefined).map(Number))]
   if(values.length===1 && Number.isFinite(values[0]))p[field]=values[0]
   else if(values.length>1)p.supplementReconciliation=true
  }
  const current=rows.filter((r:any)=>Number(r.year)===year)
  const annual=[...new Set(current.map((r:any)=>r.yearBudget).filter((v:any)=>v!==''&&v!==null&&v!==undefined).map(Number))]
  if(annual.length===1 && Number.isFinite(annual[0]))p.yearBudget=annual[0]
  else if(annual.length>1)p.supplementReconciliation=true
  else if(rows.length)p.yearBudget=0
  const spending=rows.filter((r:any)=>r.recordType==='支出')
  p.expenseTotal=spending.reduce((s:number,r:any)=>s+Number(r.amount||0),0)
  p.yearExpense=spending.filter((r:any)=>Number(r.year)===year).reduce((s:number,r:any)=>s+Number(r.amount||0),0)
 }
 for(const [key,prefix] of [['milestone','milestone'],['deliverable','deliverable'],['partner','partner'],['transform','transform']]) {
  const section=accepted(p,key);if(!section)continue
  const rows=(section.rows||[]).filter((r:any)=>r.status!=='暂无成果')
  p[`${prefix}Total`]=rows.length;p[`${prefix}Done`]=rows.filter((r:any)=>done(r)||(key==='partner'&&!!r.evalDate)).length
  if(key==='milestone') {
   p.milestonePercent=rows.length?Math.round(p.milestoneDone*100/rows.length):0
   p.nextMilestone=rows.filter((r:any)=>!done(r)).sort((a:any,b:any)=>String(a.planDate||'9999').localeCompare(String(b.planDate||'9999')))[0]||null
  }
 }
 return p
}
