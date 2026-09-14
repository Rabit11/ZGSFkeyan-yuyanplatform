import http from './request'
import { PROJECT_STATUS_TEXT } from './types'

export function supplementBusinessStatus(value?:string) {
  if(!value)return '待核对'
  return PROJECT_STATUS_TEXT[value] || ({ACTIVE:'实施中',IN_PROGRESS:'实施中',IMPLEMENT:'实施中',ACCEPTED:'已验收',DECLARE:'申报中',DECLARED:'已申报',FILED:'已立项',TRANSFORM:'成果转化',TRANSFORMING:'成果转化中'} as Record<string,string>)[value] || value
}

export interface SupplementField { key: string; label: string; type?: string; required?: boolean; readonly?:boolean; requiredWhen?:{field:string;value:any}; requiredWhenAll?:{field:string;value:any}[]; options?: Array<string | {label:string;value:string}> }
export interface SupplementFile { id: string; name?: string; fileName?: string; version?: number; createdAt?: string; rowId?:string }
export interface SupplementMaterial { code:string; name:string; required:boolean; requiredWhen?:{field:string;value:any}; requiredWhenAll?:{field:string;value:any}[]; applicability?:string; files:SupplementFile[] }
export interface SupplementSection {
  key:string; title:string; stage:string; fields:SupplementField[]; repeatable?:boolean;
  values:Record<string,any>; rows:Record<string,any>[]; materials:SupplementMaterial[];
  status:string; version:number; canEdit:boolean; canSubmit:boolean; canAudit:boolean; canReopen?:boolean;
  missing?:string[]; opinion?:string; active?:boolean; requiredRows?:boolean; configurationPending?:boolean; applicability?:string;
}
export interface SupplementDetail { project:Record<string,any>; channel:{code:string;name:string;resolved:boolean}; sections:SupplementSection[]; history:Record<string,any>[]; legacyMaterials?:Record<string,any>[] }
const base = '/api/supplement'
export const supplementApi = {
  list: (view:string) => http.get<Record<string,any>[]>(base, {view}),
  detail: (id:string) => http.get<SupplementDetail>(`${base}/${id}`),
  save: (id:string, section:SupplementSection) => http.put(`${base}/${id}/sections/${section.key}`, {values:section.values,rows:section.rows,version:section.version}),
  submit: (id:string, section:SupplementSection) => http.post(`${base}/${id}/sections/${section.key}/submit`, {version:section.version}),
  reopen: (id:string, section:SupplementSection) => http.post(`${base}/${id}/sections/${section.key}/reopen`, {version:section.version}),
  snapshot: (id:string, historyId:number) => http.get(`${base}/${id}/history/${historyId}`),
  audit: (id:string, section:SupplementSection, action:string, opinion:string) => http.post(`${base}/${id}/sections/${section.key}/audit`, {version:section.version,action,opinion}),
  upload: (id:string, section:SupplementSection, code:string, file:File, rowId?:string) => {
    const data = new FormData(); data.append('file',file); data.append('version',String(section.version))
    if(rowId)data.append('rowId',rowId)
    return http.post(`${base}/${id}/sections/${section.key}/materials/${encodeURIComponent(code)}`,data)
  },
  fileUrl: (id:string,fileId:string) => `${base}/${id}/files/${fileId}`,
}


