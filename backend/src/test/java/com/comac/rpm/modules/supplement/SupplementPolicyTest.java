package com.comac.rpm.modules.supplement;
import com.comac.rpm.common.BusinessException;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class SupplementPolicyTest {
 @Test void staleRevisionAndFrozenContentAreRejected() {
  assertThrows(BusinessException.class, () -> SupplementPolicy.editable("UNIT_REVIEW", 3, 3));
  assertThrows(BusinessException.class, () -> SupplementPolicy.editable("APPROVED", 3, 3));
  assertThrows(BusinessException.class, () -> SupplementPolicy.editable("DRAFT", 3, 2));
  assertDoesNotThrow(() -> SupplementPolicy.editable("RETURNED", 3, 3));
 }
 @Test void requiredFieldsAndEveryRepeatedRowAreValidated() {
  Map<String,Object> section = new HashMap<>();
  section.put("fields", List.of(Map.of("key","name","label","姓名","required",true)));
  section.put("repeatable",true); section.put("requiredRows",true);
  assertFalse(SupplementPolicy.missing(section,Map.of(),List.of(),List.of()).isEmpty());
  assertFalse(SupplementPolicy.missing(section,Map.of(),List.of(Map.of("name","甲"),Map.of("name","")),List.of()).isEmpty());
  assertTrue(SupplementPolicy.missing(section,Map.of(),List.of(Map.of("name","甲")),List.of()).isEmpty());
 }
 @Test void missingAttachmentDoesNotPassAsComplete() {
  Map<String,Object> section=Map.of("materials",List.of(Map.of("code","A","name","批复","required",true)));
  assertFalse(SupplementPolicy.missing(section,Map.of(),List.of(),List.of()).isEmpty());
  assertTrue(SupplementPolicy.missing(section,Map.of(),List.of(),List.of(Map.of("code","A"))).isEmpty());
 }
 @Test void selfApprovalAndAdminIdentityDoNotAuthorizeReview() {
  assertFalse(SupplementPolicy.canReview(true,"unitHead",1L,1L,"UNIT_REVIEW"));
  assertFalse(SupplementPolicy.canReview(false,"admin",1L,1L,"UNIT_REVIEW"));
  assertFalse(SupplementPolicy.canReview(false,"unitHead",2L,1L,"UNIT_REVIEW"));
  assertTrue(SupplementPolicy.canReview(false,"unitHead",1L,1L,"UNIT_REVIEW"));
  assertTrue(SupplementPolicy.canReview(false,"hqStaff",null,1L,"HQ_REVIEW"));
 }
 @Test void oneNodeAttachmentCannotCompleteAnotherNode() {
  Map<String,Object> section=Map.of("repeatable",true,"materials",List.of(Map.of("code","NODE","name","节点佐证","required",true,"rowScoped",true)));
  List<Map<String,Object>> rows=List.of(Map.of("_rowId","one"),Map.of("_rowId","two"));
  List<Map<String,Object>> files=List.of(Map.of("code","NODE","rowId","one"));
  assertEquals(1,SupplementPolicy.missing(section,Map.of(),rows,files).size());
 }
 @Test void acceptedProjectCannotSkipRequiredLevels() {
  Map<String,Object> section=Map.of("repeatable",true,"requiredLevels",List.of("单位级","公司级","国家级"));
  assertEquals(2,SupplementPolicy.missing(section,Map.of(),List.of(Map.of("level","单位级","status","已完成")),List.of()).size());
 }
 @Test void invalidAmountsAndDatesAreRejected() {
  Map<String,Object> section=Map.of("fields",List.of(Map.of("key","date","label","日期","type","date")));
  assertFalse(SupplementPolicy.missing(section,Map.of("date","2026-02-31","totalFund",100,"nationalFund",60,"selfFund",30),List.of(),List.of()).isEmpty());
 }
 @Test void referencesMustBelongToCurrentProjectEvenInMultiSelection() {
  assertTrue(SupplementPolicy.referencesValid("节点一，节点二",Set.of("节点一","节点二")));
  assertFalse(SupplementPolicy.referencesValid("节点一，其他项目节点",Set.of("节点一","节点二")));
  assertFalse(SupplementPolicy.referencesValid("19",Set.of("20","节点二")));
 }
 @Test void informationCompletenessDoesNotDependOnMissingAttachments() {
  Map<String,Object> section=Map.of("fields",List.of(Map.of("key","name","label","名称","required",true)),"materials",List.of(Map.of("code","A","name","证明","required",true)),"configurationPending",true);
  assertTrue(SupplementPolicy.informationMissing(section,Map.of("name","已填信息"),List.of()).isEmpty());
  assertFalse(SupplementPolicy.missing(section,Map.of("name","已填信息"),List.of(),List.of()).isEmpty());
 }
 @Test void incompleteAcceptanceLevelsCannotProduceSettledMaterialTotals() {
  Map<String,Object> section=Map.of("repeatable",true,"requiredRows",true,"requiredLevels",List.of("单位级","公司级","国家级"));
  assertTrue(SupplementPolicy.requirementsPending(section,List.of()));
  assertTrue(SupplementPolicy.requirementsPending(section,List.of(Map.of("level","单位级"))));
  assertFalse(SupplementPolicy.requirementsPending(section,List.of(Map.of("level","单位级"),Map.of("level","公司级"),Map.of("level","国家级"))));
 }
}
