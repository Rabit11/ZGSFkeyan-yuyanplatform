export function sourceLabel(source?: string) {
  if (source === 'FORM_MAINT') return '表单导入'
  return ['DECLARATION', 'PLATFORM', 'PLATFORM_DECLARE'].includes(source || '') ? '平台立项' : '来源待确认'
}
export function detailStages(status?: string, transformDone = false) {
  const index: Record<string, number> = { DRAFT:0, DECLARING:0, APPROVING:0, FILING:0, FILED:0, PENDING_FILING:0, IMPLEMENTING:1, DELAYED:1, ACCEPTING:2, COMPANY_ACCEPTED:2, GOV_ACCEPTED:2, ACCEPTED:2, FINISHED:3 }
  const current = index[status || ''] ?? -1
  return ['立项备案','实施阶段','验收转化'].map((name, i) => {
    const state = current < 0 ? 'unknown' : i < current || (i === 2 && transformDone) ? 'done' : i === current ? 'current' : 'future'
    const label = i === 2 && ['COMPANY_ACCEPTED','GOV_ACCEPTED','ACCEPTED'].includes(status || '') && !transformDone
      ? '验收已完成 · 转化待办理' : ({unknown:'阶段待确认',done:'已完成',current:'办理中',future:'未开始'} as const)[state]
    return { id:i, name, status:state, label }
  })
}
export function sectionTab(key: string) {
  return ({basic:'overview',team:'overview',milestone:'ms',plan:'plan',fund:'fund',deliverable:'dv',partner:'pe',transform:'tf'} as Record<string,string>)[key] || 'archive'
}
export function sectionStage(section: any) {
  return ['DECLARE','FILING'].includes(section.stage) ? 0 : ['ACCEPT','TRANSFORM'].includes(section.stage) ? 2 : 1
}
export function attachNativeMaterials(sections:any[], records:any[]) {
  return sections.map(section => {
    const remaining = records.filter(f => f.sectionKey === section.key)
    const materials = (section.materials || []).map((m:any) => {
      const files = remaining.filter(f => f.fieldCode === m.code || f.fieldName === m.name)
      return {...m, files:[...(m.files || []),...files]}
    })
    for (const f of remaining) {
      if (!materials.some((m:any) => m.files.some((file:any) => file.id === f.id)))
        materials.push({code:`legacy-${f.id}`,name:f.fieldName || f.fileName || '历史附件',required:false,files:[f],applicability:'已有业务附件；与当前材料要求的对应关系待核对'})
    }
    return {...section,materials}
  })
}
export const materialStateText: Record<string,string> = { uploaded:'已上传（审核状态待确认）',complete:'审核通过',review:'已上传待审',returned:'已退回',missing:'待补充',future:'后续需提供',condition:'适用条件待确认',inapplicable:'不适用',optional:'按需提供',unknown:'材料要求待确认' }
export function materialRows(sections: any[], status?: string) {
  const stages = detailStages(status)
  return sections.flatMap(section => (section.materials || []).map((m: any) => {
    const stage = sectionStage(section)
    const files: any[] = m.files || []
    const conditions = m.requiredWhen ? [m.requiredWhen] : m.requiredWhenAll || []
    const values: any[] = section.repeatable ? section.rows || [] : [section.values || section.defaultValues || {}]
    const matches = (v:any) => conditions.every((c:any) => String(v[c.field]) === String(c.value))
    const hasUnknown = conditions.length > 0 && (!values.length || values.some(v => conditions.some((c:any) => v[c.field] == null || v[c.field] === '')))
    const requiredValues = conditions.length ? values.filter(matches) : values
    const required = !!m.required || requiredValues.length > 0 && conditions.length > 0
    const covered = files.length > 0 && (!m.rowScoped || requiredValues.length > 0 && requiredValues.every(v => files.some(f => f.rowId === v._rowId)))
    let state = !covered && required ? 'missing' : files.length ? section.status === 'APPROVED' ? 'complete' : section.status === 'RETURNED' ? 'returned' : ['UNIT_REVIEW','HQ_REVIEW','APPROVING'].includes(section.status) ? 'review' : 'uploaded' : required ? 'missing' : 'optional'
    if (!files.length && conditions.length && !required) state = hasUnknown ? 'condition' : 'inapplicable'
    if (!files.length && stages[stage].status === 'future') state = 'future'
    if (section.active === false && stages[stage].status !== 'future' && !files.length) state = 'inapplicable'
    if (section.configurationPending || stages[stage].status === 'unknown') state = 'unknown'
    return { key:`${section.key}:${m.code}`,stage,sectionKey:section.key,sectionName:section.title,name:m.name,code:m.code,required,state,files,
      timing:m.applicability || section.applicability || '按渠道对应环节要求提供',
      condition:conditions.map((c:any) => `${(section.fields || []).find((f:any) => f.key === c.field)?.label || c.field}=${c.value}`).join(' 且 '),
      version:section.templateVersion || '—' }
  }))
}
