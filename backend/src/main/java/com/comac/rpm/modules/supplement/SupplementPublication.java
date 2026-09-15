package com.comac.rpm.modules.supplement;
import java.util.*;
/** Read model of immutable submitted events; drafts never become public by editing. */
public final class SupplementPublication {
 private SupplementPublication() {}
 @SuppressWarnings("unchecked") public static Map<String,Object> snapshot(Map<String,Object> event) {return (Map<String,Object>)event.get("snapshot");}
 /** Events must be ordered newest first. */
 public static Map<String,Map<String,Object>> latest(List<Map<String,Object>> events,boolean approvedOnly) {
  Map<String,Map<String,Object>> result=new LinkedHashMap<>();
  for(var event:events) {
   if(!Set.of("SUBMIT","APPROVE","RETURN").contains(String.valueOf(event.get("action")))) continue;
   if(approvedOnly && !"APPROVED".equals(snapshot(event).get("status"))) continue;
   result.putIfAbsent(String.valueOf(event.get("section_key")),event);
  }
  return result;
 }
 public static String status(Collection<String> statuses,int activeCount) {
  if(statuses.stream().anyMatch(s->Set.of("UNIT_REVIEW","HQ_REVIEW").contains(s)))return "IN_REVIEW";
  if(statuses.contains("RETURNED"))return "RETURNED";
  long approved=statuses.stream().filter("APPROVED"::equals).count();
  if(approved>0)return approved>=activeCount?"APPROVED":"PARTIAL";
  return "DRAFT";
 }
}
