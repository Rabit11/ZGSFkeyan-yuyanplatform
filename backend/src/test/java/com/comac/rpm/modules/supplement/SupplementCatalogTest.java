package com.comac.rpm.modules.supplement;

import com.comac.rpm.modules.project.entity.ProjInfo;
import com.comac.rpm.modules.dict.entity.ProjChannel;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SupplementCatalogTest {
    @Test void actualDeclaringAndDraftStatusesNeverActivateFutureSections() {
        for(String status:List.of("DRAFT","DECLARING","APPROVING")) {
            ProjInfo p=project("MJKY");p.setStatus(status);
            List<String> active=SupplementCatalog.sections(p,null).stream().filter(s->Boolean.TRUE.equals(s.get("active"))).map(s->String.valueOf(s.get("key"))).toList();
            assertEquals(List.of("basic","team","declare"),active,status);
        }
    }
    @Test void partialAcceptanceRequiresOnlyAlreadyPassedLevels() {
        ProjInfo p=project("MJKY");p.setStatus("COMPANY_ACCEPTED");
        Map<String,Object> s=SupplementCatalog.sections(p,null).stream().filter(x->"acceptance".equals(x.get("key"))).findFirst().orElseThrow();
        assertEquals(true,s.get("active"));assertEquals(List.of("单位级","公司级"),s.get("requiredLevels"));
    }
    private ProjInfo project(String name) { ProjInfo p=new ProjInfo(); p.setChannelName(name); p.setStatus("IMPLEMENT"); p.setLevelCode("NATIONAL"); return p; }
    @SuppressWarnings("unchecked") private List<Map<String,Object>> materials(ProjInfo p,String key) {
        return (List<Map<String,Object>>) SupplementCatalog.sections(p,null).stream().filter(s->key.equals(s.get("key"))).findFirst().orElseThrow().get("materials");
    }
    @Test void allFifteenExactChannelsResolveButUnknownAndSubstringDoNot() {
        for(String n:List.of("MJKY","04专项接续","重点研发计划","XX25专项","国家自然科学基金","FGW GXJC项目","上海市科技攻关揭榜挂帅","上海市科技创新行动计划","预研三年滚动计划","重大科技创新专项","新疆大飞机气象创新中心","科技周","大飞机研究院","大飞机先进材料创新联盟","中国商飞-波音可持续航空技术研究中心项目")) assertTrue(SupplementCatalog.resolved(project(n),null),n);
        assertFalse(SupplementCatalog.resolved(project("其他MJKY项目"),null));
        ProjInfo p=project("MJKY");p.setChannelId(123L); assertFalse(SupplementCatalog.resolved(p,null));
    }
    @Test void dictionaryIdentityOverridesConflictingHistoricalName() {
        ProjInfo p=project("MJKY");p.setChannelId(2L);ProjChannel c=new ProjChannel();c.setId(2L);c.setChannelCode("KJZ");
        assertTrue(SupplementCatalog.resolved(p,c));
        assertEquals("合作需求",((List<Map<String,Object>>)SupplementCatalog.sections(p,c).stream().filter(s->"declare".equals(s.get("key"))).findFirst().orElseThrow().get("materials")).get(0).get("name"));
    }
    @Test void committeeNameRemainsSingleMaterialAndAllianceHasThreeFilingItems() {
        assertEquals(3,materials(project("新疆大飞机气象创新中心"),"declare").size());
        assertEquals("技术委员会/主任委员会/理事会审议",materials(project("新疆大飞机气象创新中心"),"declare").get(2).get("name"));
        assertEquals(3,materials(project("大飞机先进材料创新联盟"),"filing").size());
    }
    @Test void futureAcceptanceAndTransformationNeverBlockImplementation() {
        ProjInfo p=project("MJKY");
        assertTrue(materials(p,"acceptance").stream().noneMatch(m->Boolean.TRUE.equals(m.get("required"))));
        assertTrue(materials(p,"transform").stream().noneMatch(m->Boolean.TRUE.equals(m.get("required"))));
        assertTrue(SupplementCatalog.sections(project("FGW GXJC项目"),null).stream().filter(s->"inspection".equals(s.get("key"))).anyMatch(s->Boolean.TRUE.equals(s.get("configurationPending"))));
    }
    @Test void completedAcceptanceRequiresAllApplicableLevelsAndRowScopedEvidence() {
        ProjInfo p=project("MJKY");p.setStatus("FINISHED");
        Map<String,Object> section=SupplementCatalog.sections(p,null).stream().filter(s->"acceptance".equals(s.get("key"))).findFirst().orElseThrow();
        assertEquals(List.of("单位级","公司级","国家级"),section.get("requiredLevels"));
        assertTrue(materials(p,"acceptance").stream().allMatch(m->Boolean.TRUE.equals(m.get("rowScoped"))));
        assertTrue(materials(p,"transform").stream().noneMatch(m->Boolean.TRUE.equals(m.get("required"))));
    }
    @Test void scienceWeekInspectionIsNotApplicableWhileUnconfiguredChannelIsPending() {
        Map<String,Object> section=SupplementCatalog.sections(project("科技周"),null).stream().filter(s->"inspection".equals(s.get("key"))).findFirst().orElseThrow();
        assertEquals(false,section.get("active"));assertEquals(false,section.get("configurationPending"));
    }
}
