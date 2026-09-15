package com.comac.rpm.modules.declaration;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.comac.rpm.modules.dict.entity.ProjChannel;
import com.comac.rpm.modules.declaration.entity.ProjDeclaration;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class DeclarationWorkflowYzhTest {
 @Test void newChannelsUseOwnerFirstYzhChain() throws Exception {
  var w=new DeclarationWorkflow(new ObjectMapper());var c=new ProjChannel();c.setChannelCode("KJZ");
  assertEquals("yzh-v1",w.selectForNew(c));assertEquals("项目负责人",w.auditNodes(w.selectForNew(c)).get(0).title());
  assertEquals(8,w.auditNodes(w.selectForNew(c)).size());
  c.setFlowNodes("直接报备");assertEquals(1,w.auditNodes(w.selectForNew(c)).size());
 }
 @Test void oldVersionRemainsUsable() throws Exception {
  var w=new DeclarationWorkflow(new ObjectMapper());var d=new ProjDeclaration();d.setChannelName("科技周");
  assertEquals("week-v1",w.version(d,Map.of("__workflow","week-v1")));
  assertEquals("三级专业总师",w.auditNodes("week-v1").get(0).title());
 }
}
