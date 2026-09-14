/** 成果转化演示接口独立封装，与真实接口保持相同状态机与校验。 */
import * as DB from './data'
import { currentMockUser, visibleProjects } from './dashboard'
import { resolveWorkDuty } from '@/utils/workDuty'
import { calcColor } from '@/utils/color'
import { FORM_PARENT, parseArray, validatePackage } from '@/utils/transformPackage'
import type { TransformPackage } from '@/api/transform'

const rows = DB.transforms as TransformPackage[]
const ok = (data: any) => ({ code: 0, msg: 'ok', data })
const fail = (msg: string, code = 500) => ({ code, msg, data: null })
const fields = ['name','intro','transformWay','transformForm','planDate','actualDate','status','introDetail','dutyOrg','evidenceJson'] as const
const uploads = new Set<string>()
function allowed(projectId?: number) {
  const user = currentMockUser(), p = DB.projects.find(p => p.id === projectId)
  const duty = resolveWorkDuty('transform', p, user)
  const code = user?.identityCode
  const admin = code === 'admin' || user?.roles?.includes('ADMIN')
  return [duty.fill.can && 'fill', duty.submit.can && 'submit', duty.audit.can && (admin || code === 'unitHead') && 'audit', duty.audit.can && (admin || ['hqHead','hqStaff'].includes(code || '')) && 'record'].filter(Boolean) as string[]
}
const bound = (t: TransformPackage) => DB.deliverables.filter(d => d.achievementNo === t.achievementNo && d.projectId === t.projectId)
function decorate(t: TransformPackage) {
  const p = DB.projects.find(p => p.id === t.projectId)
  return { ...t, status: t.reportedStatus || t.status, actualDate:t.reportedStatus ? t.reportedActualDate : t.actualDate, workflowStatus: t.workflowStatus || 'DRAFT', revision: t.revision || 0, colorStatus: calcColor(t.planDate,t.status === 'DONE' && t.workflowStatus === 'RECORDED'), allowedActions: allowed(t.projectId), projectName:p?.name, projectNo:p?.projectNo, deliverables:bound(t), deliverableIds:bound(t).map(d=>d.id), itemCount:bound(t).length }
}
function validate(t: TransformPackage, ids: number[]) {
  const files = parseArray(t.evidenceJson)
  const invalid = validatePackage({...t,deliverableIds:ids}, files)
  if(invalid) throw Error(invalid)
  if((t.name?.length||0)>255 || (t.dutyOrg?.length||0)>128 || (t.introDetail?.length||0)>2000) throw Error('字段长度超过上限')
  for(const [type,code] of [['TRANSFORM_WAY',t.transformWay],['TRANSFORM_FORM',t.transformForm],['TRANSFORM_STATUS',t.status]]) {
    if(!DB.dicts.some(d=>d.dictType===type && d.dictCode===code && d.status!==0)) throw Error('转化字段不符合当前数据字典')
  }
  const form = DB.dicts.find(d=>d.dictType==='TRANSFORM_FORM'&&d.dictCode===t.transformForm)
  if((form?.parentCode || FORM_PARENT[t.transformForm!])!==t.transformWay) throw Error('转化形式与方式不匹配')
  if(t.actualDate && t.actualDate>new Date().toLocaleDateString('sv-SE')) throw Error('实际转化时间不能晚于今天')
  if(files.length>20 || files.some(f=>!f.fileName || !uploads.has(f.fileUrl))) throw Error('佐证材料必须使用平台上传的附件，且最多20份')
  validateIds(t,ids)
}
function validateIds(t: TransformPackage,ids: number[]) {
  if(!Array.isArray(ids)||!ids.length) throw Error('至少选择一项已交付交付物')
  if(new Set(ids).size!==ids.length) throw Error('交付物不能重复选择')
  for(const id of ids) {
    const d=DB.deliverables.find(d=>d.id===id)
    if(!d||d.projectId!==t.projectId) throw Error('只能绑定本项目交付物')
    if(d.status!=='DELIVERED') throw Error('仅已交付交付物可纳入成果包')
    if(d.achievementNo && d.achievementNo!==t.achievementNo) throw Error('交付物已被其他成果包占用')
  }
}
function bind(t: TransformPackage,ids:number[]) {
  bound(t).forEach(d=>{d.achievementNo=undefined})
  ids.forEach(id=>{DB.deliverables.find(d=>d.id===id)!.achievementNo=t.achievementNo})
  t.itemCount=ids.length
}
function prepare(t:TransformPackage) {
  t.reportedStatus=t.status
  t.reportedActualDate=t.actualDate
  if(t.workflowStatus!=='RECORDED')t.actualDate=undefined
  if(t.status==='DONE'&&t.workflowStatus!=='RECORDED')t.status='SIGNED'
  t.colorStatus=calcColor(t.planDate,t.status==='DONE')
}
function event(t:TransformPackage,action:string,note='') {
  const history=parseArray(t.historyJson)
  history.push({action,note,actor:currentMockUser()?.realName,at:new Date().toISOString(),workflowStatus:t.workflowStatus,snapshot:{name:t.name,status:t.status,introDetail:t.introDetail,planDate:t.planDate,actualDate:t.actualDate,evidence:parseArray(t.evidenceJson),deliverableIds:bound(t).map(d=>d.id)}})
  t.historyJson=JSON.stringify(history)
}
export function mockTransform(method:string,path:string,query:any,body:any) {
  if(path==='/files/upload' && method==='POST' && body instanceof FormData) {
    const file=body.get('file') as File
    if(!file?.size) return fail('上传文件不能为空')
    const url=URL.createObjectURL(file);uploads.add(url)
    return ok({fileName:file.name,fileSize:file.size,fileUrl:url})
  }
  if(!/^\/transforms(?:\/|$)/.test(path)) return undefined
  try {
    const visible=new Set(visibleProjects(currentMockUser()).map(p=>p.id))
    const id=Number(path.split('/')[2]), actionPath=path.split('/')[3]
    if(path==='/transforms' && method==='GET') {
      let list=rows.filter(t=>visible.has(t.projectId!)).map(decorate)
      for(const field of ['projectId','status','transformWay','dutyOrg','workflowStatus']) if(query[field]) list=list.filter(t=>String((t as any)[field])===String(query[field]))
      if(query.keyword) list=list.filter(t=>[t.name,t.achievementNo,t.projectNo].some(v=>v?.includes(query.keyword)))
      const page=Number(query.page||1),size=Math.min(200,Number(query.size||10))
      return ok({records:list.slice((page-1)*size,page*size),total:list.length,page,size})
    }
    if(path==='/transforms' && method==='POST') {
      if(!visible.has(body.projectId)||!allowed(body.projectId).includes('fill')) return fail('无权填报该项目成果',403)
      const t={id:Math.max(0,...rows.map(t=>t.id))+1,achievementNo:'CG'+new Date().getFullYear()+crypto.randomUUID().replace(/-/g,'').toUpperCase(),projectId:body.projectId,projectNo:DB.projects.find(p=>p.id===body.projectId)?.projectNo,workflowStatus:'DRAFT',revision:0,status:'NOT_STARTED',evidenceJson:'[]',historyJson:'[]'} as TransformPackage
      fields.forEach(k=>{if(body[k]!==undefined)(t as any)[k]=body[k]})
      validate(t,body.deliverableIds);bind(t,body.deliverableIds);event(t,'CREATE');prepare(t);rows.unshift(t);return ok(t.id)
    }
    const existing=rows.find(t=>t.id===id)
    if(!existing) return fail('成果包不存在')
    if(!visible.has(existing.projectId!)) return fail('无权访问该项目成果包',403)
    if(method==='GET') return ok(decorate(existing))
    const t={...existing,status:existing.reportedStatus||existing.status,actualDate:existing.reportedStatus?existing.reportedActualDate:existing.actualDate,workflowStatus:existing.workflowStatus||'DRAFT',revision:existing.revision||0}
    if(Number(body.revision ?? query.revision)!==t.revision) return fail('数据已更新，请关闭并重新打开成果包后再办理',409)
    const actions=allowed(t.projectId), editable=['DRAFT','RETURNED'].includes(t.workflowStatus)
    const require=(a:string)=>{if(!actions.includes(a))throw Error('当前账号无权办理该节点')}
    if(method==='PUT') {
      require('fill');if(!editable)throw Error('审核或备案中的成果包不能修改')
      fields.forEach(k=>{if(body[k]!==undefined)(t as any)[k]=body[k]})
      const ids=body.deliverableIds||bound(t).map(d=>d.id)
      validate(t,ids);bind(t,ids);event(t,'UPDATE')
    } else if(method==='POST'&&actionPath==='bind') {
      require('fill');if(!editable)throw Error('当前阶段不能修改绑定')
      validateIds(t,body.deliverableIds);bind(t,body.deliverableIds);event(t,'BIND')
    } else if(method==='POST'&&actionPath==='workflow') {
      const action=body.action,note=String(body.note||'').trim()
      if(note.length>1000) throw Error('办理意见不能超过1000字')
      switch(action) {
        case 'SUBMIT':require('submit');if(!editable)throw Error('当前节点不能提交');validate(t,bound(t).map(d=>d.id));t.workflowStatus='UNIT_REVIEW';break
        case 'APPROVE':case 'REJECT':require('audit');if(t.workflowStatus!=='UNIT_REVIEW')throw Error('当前不在二级单位审核节点');if(action==='REJECT'&&!note)throw Error('请填写退回原因');t.workflowStatus=action==='REJECT'?'RETURNED':'HQ_RECORD';break
        case 'RECORD':require('record');if(t.workflowStatus!=='HQ_RECORD')throw Error('请先完成二级单位审核');t.workflowStatus='RECORDED';break
        case 'REOPEN':require('fill');if(t.workflowStatus!=='RECORDED')throw Error('仅已备案成果可更新进展');t.workflowStatus='DRAFT';break
        default:throw Error('不支持的办理动作')
      }
      event(t,action,note)
    } else if(method==='DELETE') {
      require('fill');if(t.workflowStatus!=='DRAFT'||parseArray(t.historyJson).some(e=>e.action==='SUBMIT'))throw Error('仅从未提交过的草稿可删除')
      bound(t).forEach(d=>{d.achievementNo=undefined});rows.splice(rows.indexOf(existing),1);return ok(true)
    } else return fail('不支持的成果转化接口')
    t.revision++;prepare(t);Object.assign(existing,t);return ok(true)
  } catch(e:any) {return fail(e.message)}
}
