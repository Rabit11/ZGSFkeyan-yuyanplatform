package com.comac.rpm.modules.supplement;
import com.comac.rpm.common.BusinessException;
import com.comac.rpm.modules.project.entity.ProjInfo;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class SupplementTemplateServiceTest {
 private List<Map<String,Object>> schema(){ProjInfo p=new ProjInfo();p.setChannelName("MJKY");p.setStatus("IMPLEMENT");return SupplementCatalog.sections(p,null);}
 private Map<String,Object> item(String code){return Map.of("code",code,"name","年度材料","required",false,"applicability","检查已发生","requiredWhen",Map.of("field","occurred","value","已发生"));}
 @Test void configuredGapRequiresExplicitMaterialsOrNotApplicable(){
  assertThrows(BusinessException.class,()->SupplementTemplateService.validate(Map.of("inspection",Map.of("mode","CONFIGURED","applicability","需求核对","materials",List.of())),schema()));
  assertEquals(1,SupplementTemplateService.validate(Map.of("inspection",Map.of("mode","NOT_APPLICABLE","applicability","主管部门确认不适用","materials",List.of())),schema()).size());
 }
 @Test void conditionsCannotReferenceUnrecognizedFieldsAndCodesCannotDuplicate(){
  Map<String,Object> item=new LinkedHashMap<>(item("MAT_A"));item.put("requiredWhen",Map.of("field","admin","value",true));
  assertThrows(BusinessException.class,()->SupplementTemplateService.validate(Map.of("inspection",Map.of("mode","CONFIGURED","applicability","核对","materials",List.of(item))),schema()));
  assertThrows(BusinessException.class,()->SupplementTemplateService.validate(Map.of("inspection",Map.of("mode","CONFIGURED","applicability","核对","materials",List.of(item("MAT_A"),item("MAT_A")))),schema()));
 }
 @Test void configNeverCarriesArbitraryProjectOrPermissionFields(){
  Map<String,Object> override=new LinkedHashMap<>(Map.of("mode","CONFIGURED","applicability","实际检查","materials",List.of(item("MAT_A"))));override.put("canEdit",true);override.put("fields",List.of());
  Map<String,Object> clean=SupplementTemplateService.validate(Map.of("inspection",override),schema());
  assertFalse(((Map<?,?>)clean.get("inspection")).containsKey("canEdit"));assertFalse(((Map<?,?>)clean.get("inspection")).containsKey("fields"));
 }
}
