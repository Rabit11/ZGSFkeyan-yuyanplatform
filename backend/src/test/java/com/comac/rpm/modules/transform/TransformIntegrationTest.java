package com.comac.rpm.modules.transform;

import com.comac.rpm.common.BusinessException;
import com.comac.rpm.common.permission.FlowAuditGuard;
import com.comac.rpm.modules.transform.controller.TransformController;
import com.comac.rpm.modules.transform.entity.AchvTransform;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.LocalDate;

class TransformIntegrationTest {
 @Test void orphanedHistoryIsFlaggedWithoutOfferingWriteActions() {
  var controller=new TransformController();var projects=mock(com.comac.rpm.modules.project.mapper.ProjInfoMapper.class);
  var access=mock(TransformAccess.class);doThrow(new BusinessException("项目不存在")).when(access).requireAction(eq(999L),anyString());
  ReflectionTestUtils.setField(controller,"projectMapper",projects);ReflectionTestUtils.setField(controller,"access",access);
  var t=new AchvTransform();t.setProjectId(999L);t.setStatus("DONE");t.setActualDate(LocalDate.of(2024,1,12));
  ReflectionTestUtils.invokeMethod(controller,"enrich",t);
  assertEquals(Boolean.TRUE,ReflectionTestUtils.getField(t,"orphanedProject"));assertTrue(t.getAllowedActions().isEmpty());
  assertEquals("DONE",t.getConfirmedStatus());assertEquals(LocalDate.of(2024,1,12),t.getConfirmedActualDate());
 }
 @Test void recordedHistoryRetainsAllBusinessFieldsForLaterDraftComparison() throws Exception {
  var controller=new TransformController();var json=new com.fasterxml.jackson.databind.ObjectMapper();
  var guard=mock(FlowAuditGuard.class);var user=new com.comac.rpm.modules.system.entity.SysUser();user.setId(1L);user.setRealName("备案人");when(guard.currentUser()).thenReturn(user);
  var deliveries=mock(com.comac.rpm.modules.deliverable.mapper.ProjDeliverableMapper.class);when(deliveries.selectList(any())).thenReturn(java.util.List.of());
  ReflectionTestUtils.setField(controller,"json",json);ReflectionTestUtils.setField(controller,"flowAuditGuard",guard);ReflectionTestUtils.setField(controller,"deliverableMapper",deliveries);
  var t=new AchvTransform();t.setProjectId(1L);t.setName("成果");t.setStatus("DONE");t.setWorkflowStatus("RECORDED");t.setTransformWay("MODEL");t.setTransformForm("INSTALLED");t.setDutyOrg("责任单位");t.setIntro("成果简介");
  ReflectionTestUtils.invokeMethod(controller,"event",t,"RECORD","");
  var snapshot=json.readTree(t.getHistoryJson()).get(0).get("snapshot");
  assertEquals("MODEL",snapshot.path("transformWay").asText());assertEquals("INSTALLED",snapshot.path("transformForm").asText());assertEquals("责任单位",snapshot.path("dutyOrg").asText());assertEquals("成果简介",snapshot.path("intro").asText());
 }
 @Test void guessedLegacyEvidenceUrlDoesNotProveAnUploadedProjectFile() {
  var controller=new TransformController();
  var dict=mock(com.comac.rpm.modules.system.mapper.SysDictMapper.class);
  var entry=new com.comac.rpm.modules.system.entity.SysDict();entry.setParentCode("MODEL");
  when(dict.selectList(any())).thenReturn(java.util.List.of(entry));
  ReflectionTestUtils.setField(controller,"dictMapper",dict);
  ReflectionTestUtils.setField(controller,"json",new com.fasterxml.jackson.databind.ObjectMapper());
  var t=new AchvTransform();t.setProjectId(1L);t.setName("成果");t.setDutyOrg("单位");t.setIntroDetail("说明");t.setPlanDate(LocalDate.of(2024,1,1));t.setStatus("DONE");t.setActualDate(LocalDate.of(2024,1,12));t.setTransformWay("MODEL");t.setTransformForm("INSTALLED");
  t.setEvidenceJson("[{\"fileName\":\"fake.pdf\",\"fileUrl\":\"/api/files/download?objectKey=evidence/guessed.pdf\"}]");
  assertThrows(BusinessException.class,()->ReflectionTestUtils.invokeMethod(controller,"validate",t));
 }
 @Test void adminBypassMustNotGrantTransformWrite() {
  var controller=new TransformController();
  var guard=mock(FlowAuditGuard.class);
  var user=new com.comac.rpm.modules.system.entity.SysUser(); user.setIdentityCode("admin");user.setId(99L);user.setStatus(1);
  when(guard.currentUser()).thenReturn(user);
  ReflectionTestUtils.setField(controller,"flowAuditGuard",guard);
  var access=new TransformAccess();
  var projects=mock(com.comac.rpm.modules.project.mapper.ProjInfoMapper.class);
  var members=mock(com.comac.rpm.modules.project.mapper.ProjTeamMemberMapper.class);
  var project=new com.comac.rpm.modules.project.entity.ProjInfo();project.setId(1L);project.setOwnerName("负责人");
  when(projects.selectById(1L)).thenReturn(project);when(members.selectList(any())).thenReturn(java.util.List.of());
  ReflectionTestUtils.setField(access,"guard",guard);ReflectionTestUtils.setField(access,"projects",projects);ReflectionTestUtils.setField(access,"members",members);
  ReflectionTestUtils.setField(controller,"access",access);
  assertThrows(BusinessException.class,()->ReflectionTestUtils.invokeMethod(controller,"require",1L,"fill"));
 }
 @Test void previouslyConfirmedCompletionSurvivesDraftPreparation() {
  var controller=new TransformController();
  var t=new AchvTransform();t.setId(1L);t.setStatus("DONE");t.setActualDate(LocalDate.of(2024,1,12));t.setWorkflowStatus("DRAFT");
  // Simulates the formal row being loaded before draft fields are exposed.
  ReflectionTestUtils.invokeMethod(controller,"captureConfirmed",t);
  t.setStatus("NEGOTIATING");t.setActualDate(null);
  ReflectionTestUtils.invokeMethod(controller,"prepareProgress",t);
  assertEquals("DONE",t.getStatus());assertEquals(LocalDate.of(2024,1,12),t.getActualDate());
  assertEquals("NEGOTIATING",t.getReportedStatus());assertNull(t.getReportedActualDate());
 }
 @Test void newUnrecordedCompletionCannotCountAsOfficialDone() {
  var t=new AchvTransform();t.setStatus("DONE");t.setActualDate(LocalDate.of(2024,1,12));t.setWorkflowStatus("DRAFT");
  ReflectionTestUtils.invokeMethod(new TransformController(),"prepareProgress",t);
  assertNotEquals("DONE",t.getStatus());assertNull(t.getActualDate());assertEquals("DONE",t.getReportedStatus());
 }
 @Test void recordingPublishesCurrentProgress() {
  var t=new AchvTransform();t.setStatus("DONE");t.setActualDate(LocalDate.of(2024,1,12));t.setWorkflowStatus("RECORDED");
  ReflectionTestUtils.invokeMethod(new TransformController(),"prepareProgress",t);
  assertEquals("DONE",t.getStatus());assertEquals(LocalDate.of(2024,1,12),t.getActualDate());
 }
}
