package com.comac.rpm.modules.supplement;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class SupplementPublicationTest {
 private Map<String,Object> event(String key,String action,String status,int batch){return new HashMap<>(Map.of("section_key",key,"action",action,"snapshot",Map.of("status",status,"batch",batch)));}
 @Test void draftAndUploadsNeverReplaceSubmittedSnapshot(){
  var events=List.of(event("basic","SAVE","DRAFT",2),event("basic","NEW_BATCH","DRAFT",2),event("basic","APPROVE","APPROVED",1));
  assertEquals("APPROVED",SupplementPublication.snapshot(SupplementPublication.latest(events,false).get("basic")).get("status"));
 }
 @Test void returnedEditsDoNotLeakAndPendingBatchKeepsApprovedVersion(){
  var events=List.of(event("basic","SAVE","RETURNED",2),event("basic","RETURN","RETURNED",2),event("basic","SUBMIT","UNIT_REVIEW",2),event("basic","APPROVE","APPROVED",1));
  assertEquals("RETURN",SupplementPublication.latest(events,false).get("basic").get("action"));
  assertEquals(1,SupplementPublication.snapshot(SupplementPublication.latest(events,true).get("basic")).get("batch"));
 }
 @Test void pendingWinsOverReturnedAndPartialApprovalIsNotAllApproved(){
  assertEquals("IN_REVIEW",SupplementPublication.status(List.of("RETURNED","HQ_REVIEW"),3));
  assertEquals("PARTIAL",SupplementPublication.status(List.of("APPROVED"),3));
  assertEquals("APPROVED",SupplementPublication.status(List.of("APPROVED","APPROVED"),2));
  assertEquals("DRAFT",SupplementPublication.status(List.of(),2));
 }
}
