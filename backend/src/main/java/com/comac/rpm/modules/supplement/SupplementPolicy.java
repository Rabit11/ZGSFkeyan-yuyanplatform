package com.comac.rpm.modules.supplement;

import com.comac.rpm.common.BusinessException;
import java.util.*;

/** Shared authoritative write, review and completeness rules. */
public final class SupplementPolicy {
 private SupplementPolicy() {}
 public static List<String> informationMissing(Map<String,Object> section,Map<String,Object> values,List<Map<String,Object>> rows) {
  Map<String,Object> info=new LinkedHashMap<>(section);
  info.put("materials",List.of());info.put("configurationPending",false);
  return missing(info,values,rows,List.of());
 }
 public static boolean requirementsPending(Map<String,Object> section,List<Map<String,Object>> rows) {
  if(Boolean.FALSE.equals(section.get("active"))) return false;
  if(Boolean.TRUE.equals(section.get("configurationPending"))) return true;
  if(Boolean.TRUE.equals(section.get("repeatable")) && Boolean.TRUE.equals(section.get("requiredRows")) && rows.isEmpty()) return true;
  if(section.get("requiredLevels") instanceof List<?> levels) return levels.stream().anyMatch(level->rows.stream().noneMatch(r->Objects.equals(r.get("level"),level)));
  return false;
 }
 public static boolean referencesValid(String raw,Set<String> allowed) {
  if(allowed.contains(raw)) return true;
  return Arrays.stream(raw.split("[,，;；\\n]+",-1)).map(String::trim).allMatch(v->!v.isEmpty() && allowed.contains(v));
 }
 public static void editable(String status,long current,long requested) {
  if(current!=requested) throw new BusinessException(409,"内容已更新，请刷新后重试");
  if(!Set.of("DRAFT","RETURNED").contains(status)) throw new BusinessException(403,"审核中或已通过的版本不可修改");
 }
 public static boolean canReview(boolean owner,String identity,Long userOrg,Long projectOrg,String status) {
  if(owner) return false;
  return "UNIT_REVIEW".equals(status) && "unitHead".equals(identity) && userOrg!=null && userOrg.equals(projectOrg)
    || !owner && "HQ_REVIEW".equals(status) && Set.of("hqHead","hqStaff").contains(identity==null?"":identity);
 }
 @SuppressWarnings("unchecked")
 public static boolean required(Map<String,Object> item, Map<String,Object> values) {
  if(item.get("requiredWhenAll") instanceof List<?> conditions) return conditions.stream().allMatch(x -> x instanceof Map<?,?> c && Objects.equals(values.get(c.get("field")),c.get("value")));
  if(Boolean.TRUE.equals(item.get("required"))) return true;
  if(item.get("requiredWhen") instanceof Map<?,?> c) return Objects.equals(values.get(c.get("field")), c.get("value"));
  return false;
 }
 @SuppressWarnings("unchecked")
 public static List<String> missing(Map<String,Object> section,Map<String,Object> values,List<Map<String,Object>> rows,List<Map<String,Object>> files) {
  List<String> errors=new ArrayList<>();
  if(Boolean.FALSE.equals(section.get("active"))) return errors;
  if(Boolean.TRUE.equals(section.get("configurationPending"))) errors.add("渠道材料待配置，暂不可提交");
  List<Map<String,Object>> fields=(List<Map<String,Object>>)section.getOrDefault("fields",List.of());
  boolean repeated=Boolean.TRUE.equals(section.get("repeatable"));
  if(repeated && Boolean.TRUE.equals(section.get("requiredRows")) && rows.isEmpty()) errors.add("至少补录一条记录");
  if(section.get("requiredLevels") instanceof List<?> levels) for(Object level:levels) if(rows.stream().noneMatch(r->Objects.equals(r.get("level"),level) && "已完成".equals(r.get("status")))) errors.add(level+"验收完成记录未补齐");
  List<Map<String,Object>> inputs=repeated?rows:List.of(values);
  int n=0;
  for(Map<String,Object> input:inputs) {
   n++;
   for(Map<String,Object> field:fields) {
    Object v=input.get(field.get("key"));
    if(required(field,input) && (v==null || v.toString().isBlank())) errors.add((repeated?"第"+n+"行 ":"")+field.get("label")+"未填写");
   if(v!=null && !v.toString().isBlank() && "number".equals(field.get("type"))) {
     try { if(new java.math.BigDecimal(v.toString()).signum()<0) errors.add(field.get("label")+"不能为负数"); }
     catch(NumberFormatException ex) { errors.add(field.get("label")+"必须为数字"); }
    }
    if(v!=null && !v.toString().isBlank() && "date".equals(field.get("type"))) {
     try { java.time.LocalDate.parse(v.toString()); } catch(java.time.format.DateTimeParseException ex) {errors.add(field.get("label")+"日期无效");}
    }
    if(v!=null && !v.toString().isBlank() && field.get("options") instanceof List<?> options && !options.contains(v)) errors.add(field.get("label")+"选项无效");
   }
   if(Set.of("支出","核销").contains(String.valueOf(input.get("recordType"))) && Objects.toString(input.get("voucherNo"),"").isBlank()) errors.add("第"+n+"行凭证号未填写");
   if(input.containsKey("totalFund") && input.containsKey("nationalFund") && input.containsKey("selfFund")) {
    try {if(new java.math.BigDecimal(input.get("totalFund").toString()).compareTo(new java.math.BigDecimal(input.get("nationalFund").toString()).add(new java.math.BigDecimal(input.get("selfFund").toString())))!=0) errors.add("第"+n+"行总经费须等于国拨加自筹");}catch(RuntimeException ignored) { /* numeric validation above reports this */ }
   }
   if("transform".equals(section.get("key")) && !"暂无成果".equals(input.get("status"))) for(String k:List.of("achievementNo","name","description","deliverableRef","organization")) if(Objects.toString(input.get(k),"").isBlank()) errors.add("第"+n+"行成果信息未完善："+k);
   if(input.get("startDate")!=null && input.get("endDate")!=null && input.get("startDate").toString().compareTo(input.get("endDate").toString())>0) errors.add("结束日期不能早于开始日期");
  }
  for(Map<String,Object> material:(List<Map<String,Object>>)section.getOrDefault("materials",List.of())) {
   if(repeated && Boolean.TRUE.equals(material.get("rowScoped"))) {
    int rowNumber=0;
    for(Map<String,Object> row:rows) {
     rowNumber++;
     if(required(material,row) && files.stream().noneMatch(f->Objects.equals(f.get("code"),material.get("code")) && row.get("_rowId")!=null && Objects.equals(f.get("rowId"),row.get("_rowId")))) errors.add("第"+rowNumber+"行 "+material.get("name")+"未上传");
    }
    continue;
   }
   boolean needed=required(material,values) || rows.stream().anyMatch(r->required(material,r));
   if(needed && files.stream().noneMatch(f->Objects.equals(f.get("code"),material.get("code")))) errors.add(material.get("name")+"未上传");
  }
  return errors;
 }
}
