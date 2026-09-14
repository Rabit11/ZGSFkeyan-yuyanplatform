package com.comac.rpm.modules.supplement;

import com.comac.rpm.modules.project.entity.ProjInfo;
import com.comac.rpm.modules.dict.entity.ProjChannel;
import java.util.*;

/** Versioned, exact-channel supplement schema. Business state is never changed by this catalog. */
public final class SupplementCatalog {
    private SupplementCatalog() {}
    private record Template(String code,String name,String[] declaration,String[] filing,String[] checks) {}
    private static final Map<String,Template> TEMPLATES=new LinkedHashMap<>();
    private static final Map<String,String> ALIASES=new HashMap<>();
    static {
        add("MJKY","MJKY","建议书|建议书意见","立项批复","中期评估相关材料");
        add("04ZXJX","04专项接续","建议书|建议书意见","立项批复","中期评估相关材料");
        add("ZDYFJH","重点研发计划","申请书|申请书评审","立项批复","中期评估相关材料");
        add("XX25","XX25专项","申报通知|任务清单|任务清单评估","立项批复","季度会相关材料|季度报|年度评估相关材料|国资委现场督导相关材料");
        add("ZRJJ","国家自然科学基金","申请书|申请书评审","批准通知","年度实施报告|中期评估相关材料");
        add("FGGGXJC","FGW GXJC项目","建议书|建议书意见","立项批复","");
        add("SHJBGS","上海市科技攻关揭榜挂帅","榜单答疑","申请书评审","中期评审相关材料");
        add("SHKJCX","上海市科技创新行动计划","建议书|建议书评审","立项通知","阶段性检查相关材料");
        add("YYGD","预研三年滚动计划","建议书|建议书评审","立项通知","阶段性检查相关材料");
        add("ZDZX","重大科技创新专项","建议书|建议书评审","立项通知","");
        add("XJQX","新疆大飞机气象创新中心","申请书|申请书评审|技术委员会/主任委员会/理事会审议","立项通知","阶段性检查相关材料");
        add("KJZ","科技周","合作需求|需求对接总结|技术发展战略委员会审议","拟立项通知|立项文件","");
        add("DFJYJY","大飞机研究院","项目申请书|学术委员会审议","立项通知","中期检查相关材料");
        add("CLM","大飞机先进材料创新联盟","项目申请书","立项建议清单|联盟专委会审议意见|联盟理事会审议意见","阶段性检查相关材料");
        add("BOKH","中国商飞-波音可持续航空技术研究中心项目","波音指导委员会会议纪要","三方合同","阶段性检查相关材料");
        alias("BOKH","“中国商飞-波音”可持续航空技术研究中心项目","中国商飞-波音项目");
        alias("FGGGXJC","FGW_GXJC");
    }
    private static void add(String code,String name,String d,String f,String checks) {
        TEMPLATES.put(code,new Template(code,name,split(d),split(f),split(checks)));alias(code,code,name);
    }
    private static String[] split(String value) { return value.isEmpty()?new String[0]:value.split("\\|"); }
    private static void alias(String code,String... names) { for(String n:names) ALIASES.put(normalize(n),code); }
    private static String normalize(String n) { return n==null?"":n.replaceAll("[\\s\\u3000]+","").toUpperCase(Locale.ROOT); }
    private static Template resolve(ProjInfo p,ProjChannel c) {
        if(p==null)return null;
        if(p.getChannelId()!=null && (c==null || !Objects.equals(p.getChannelId(),c.getId())))return null;
        if(c!=null) {
            // An existing dictionary identity must not fall back to a stale project label.
            String code=ALIASES.get(normalize(c.getChannelCode()));
            if(code==null)code=ALIASES.get(normalize(c.getChannelName()));
            return TEMPLATES.get(code);
        }
        return TEMPLATES.get(ALIASES.get(normalize(p.getChannelName())));
    }
    public static boolean resolved(ProjInfo p,ProjChannel c) { return resolve(p,c)!=null; }
    public static String channelCode(ProjInfo p,ProjChannel c) { Template t=resolve(p,c); return t==null?"":t.code; }
    public static String channelName(ProjInfo p,ProjChannel c) { Template t=resolve(p,c); return t==null?"渠道待核对":t.name; }
    private static boolean is(String value,String... options) { return Arrays.asList(options).contains(value); }
    private static int phase(ProjInfo p) {
        if(is(p.getTransformStatus(),"DONE","COMPLETED","已完成","已转化"))return 4;
        if(is(p.getStatus(),"FINISHED","ACCEPTED","已完成","已验收") || is(p.getAcceptStatus(),"ACCEPTED","FINISHED","PASS","PASSED","已验收","已通过"))return 3;
        if(is(p.getStatus(),"ACCEPT","ACCEPTING","COMPANY_ACCEPTED","GOV_ACCEPTED","验收中","已通过公司级验收","已通过机关验收"))return 3;
        if(is(p.getStatus(),"DRAFT","DECLARING","APPROVING","DECLARE","DECLARED","草稿","申报中","已申报"))return 0;
        if(is(p.getStatus(),"FILING","FILED","PENDING_FILING","已立项","立项中"))return 1;
        return 2;
    }
    private static boolean acceptanceCompleted(ProjInfo p) { return is(p.getStatus(),"FINISHED","ACCEPTED","已完成","已验收") || is(p.getAcceptStatus(),"ACCEPTED","FINISHED","PASS","PASSED","已验收","已通过"); }
    private static Map<String,Object> field(String key,String label,String type,boolean required) { return map("key",key,"label",label,"type",type,"required",required); }
    private static Map<String,Object> f(String key,String label) { return field(key,label,"text",true); }
    private static Map<String,Object> optional(String key,String label) { return field(key,label,"text",false); }
    private static Map<String,Object> num(String key,String label) { return field(key,label,"number",true); }
    private static Map<String,Object> date(String key,String label) { return field(key,label,"date",true); }
    private static Map<String,Object> select(String key,String label,String... options) { Map<String,Object> f=field(key,label,"select",true);f.put("options",List.of(options));return f; }
    private static Map<String,Object> when(Map<String,Object> field,String key,String value) { field.put("required",false);field.put("requiredWhen",map("field",key,"value",value));return field; }
    private static Map<String,Object> material(String code,String name,boolean required,String applicability) { return map("code",code,"name",name,"required",required,"applicability",applicability); }
    private static Map<String,Object> map(Object... entries) { Map<String,Object> m=new LinkedHashMap<>();for(int i=0;i<entries.length;i+=2)m.put(entries[i].toString(),entries[i+1]);return m; }
    private static void value(Map<String,Object> m,String key,Object value) { if(value!=null)m.put(key,value.toString()); }
    @SafeVarargs private static Map<String,Object> section(String key,String title,String stage,boolean repeated,boolean active,Map<String,Object>... fields) {
        return map("key",key,"title",title,"stage",stage,"repeatable",repeated,"active",active,"requiredRows",repeated&&active,"templateVersion","1.0","fields",new ArrayList<>(List.of(fields)),"defaultValues",new LinkedHashMap<>(),"materials",new ArrayList<>(),"applicability",active?"按项目实际情况补齐；已完成记录需提供实际结果":"尚未到达形成时点，可保存草稿，不阻塞当前阶段","configurationPending",false);
    }
    @SuppressWarnings("unchecked") private static List<Map<String,Object>> mats(Map<String,Object> s) { return (List<Map<String,Object>>)s.get("materials"); }
    private static void channelMaterials(Map<String,Object> s,Template t,String[] names,String prefix,boolean required,String condition) {
        for(String name:names) mats(s).add(material(t.code+"_"+prefix+"_"+stable(name),name,required,condition));
    }
    // Stable semantic identity survives row reordering (never derive codes from array positions).
    private static String stable(String name) { return java.util.UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString().replace("-",""); }
    public static List<Map<String,Object>> sections(ProjInfo p,ProjChannel c) {
        Objects.requireNonNull(p,"project");Template t=resolve(p,c);int phase=phase(p);boolean impl=phase>=2,accepted=acceptanceCompleted(p);
        List<Map<String,Object>> all=new ArrayList<>();
        Map<String,Object> basic=section("basic","项目基本信息","BASIC",false,true,
            f("name","项目名称"),f("projectNo","项目编号"),f("levelCode","项目层级"),f("channelName","项目来源/渠道"),f("projectType","项目类型"),f("major1","一级专业"),f("major2","二级专业"),f("orgName","责任单位"),f("manageOrgName","管理/需求单位"),f("bureauOffice","所中心/司局处室"),f("leadOrgName","牵头单位"),f("participantOrgs","参研单位（无则填写无）"),date("startDate","开始日期"),date("endDate","结束日期"),field("goal","总体目标","textarea",true),field("indicators","考核指标","textarea",true),f("status","业务状态"));
        Map<String,Object> defaults=map();value(defaults,"name",p.getName());value(defaults,"projectNo",p.getProjectNo());value(defaults,"levelCode",p.getLevelCode());value(defaults,"channelName",t==null?p.getChannelName():t.name);value(defaults,"projectType",p.getProjectType());value(defaults,"major1",p.getMajor1());value(defaults,"major2",p.getMajor2());value(defaults,"orgName",p.getOrgName());value(defaults,"manageOrgName",p.getManageOrgName());value(defaults,"bureauOffice",p.getBureauOffice());value(defaults,"leadOrgName",p.getLeadOrgName());value(defaults,"startDate",p.getStartDate());value(defaults,"endDate",p.getEndDate());value(defaults,"goal",p.getGoal());value(defaults,"status",p.getStatus());basic.put("defaultValues",defaults);
        for(Map<String,Object> field:(List<Map<String,Object>>)basic.get("fields"))if(is((String)field.get("key"),"channelName","levelCode","status"))field.put("readonly",true);
        all.add(basic);
        all.add(section("team","项目团队","BASIC",true,true,select("role","岗位","项目负责人","技术负责人","项目主管","联系人","其他"),f("name","姓名"),f("employeeNo","工号"),f("organization","所属单位"),f("responsibility","岗位职责")));
        Map<String,Object> declare=section("declare","项目申报","DECLARE",false,true,f("declarationNo","申报编号"),date("declarationDate","申报时间"),f("summary","申报基本信息"),f("result","申报结果/当前状态"),optional("opinion","相关意见"));all.add(declare);
        Map<String,Object> filing=section("filing","立项备案","FILING",false,phase>=1,f("filingNo","备案/立项文号"),date("filingDate","立项日期"),f("filingDept","立项部门"),num("approvedFund","批复经费（万元）"),date("startDate","项目开始日期"),date("endDate","项目结束日期"));filing.put("defaultValues",map("filingDept",Objects.toString(p.getFilingDept(),""),"approvedFund",p.getTotalFund()==null?"":p.getTotalFund().toPlainString()));all.add(filing);
        all.add(section("milestone","里程碑","IMPLEMENT",true,impl,num("year","年度"),f("name","节点名称"),f("goal","节点目标"),f("indicators","考核指标"),f("owner","负责人"),date("planDate","计划完成日期"),select("status","状态","未开始","进行中","已完成"),when(date("actualDate","实际完成日期"),"status","已完成"),f("deliverableRefs","关联交付物编号"),num("budget","节点预算（万元）")));
        all.add(section("plan","年度计划","IMPLEMENT",true,impl,num("year","年度"),f("name","任务名称"),f("source","计划来源"),f("goal","目标与指标"),f("owner","负责人"),date("planDate","计划完成日期"),select("status","完成状态","未开始","进行中","已完成"),when(date("actualDate","实际完成日期"),"status","已完成"),optional("completion","完成情况")));
        Map<String,Object> fund=section("fund","经费信息","IMPLEMENT",true,impl,select("recordType","记录类型","预算","支出","核销"),num("year","年度"),num("totalFund","批复总预算（万元）"),num("nationalFund","国拨经费（万元）"),num("selfFund","自筹经费（万元）"),num("yearBudget","年度预算（万元）"),f("milestoneRef","关联里程碑编号（无则填写无）"),num("milestoneBudget","里程碑预算（万元）"),num("amount","本条金额（万元）"),date("occurredDate","发生日期"),optional("voucherNo","凭证号（支出/核销必填）"),optional("description","说明"));all.add(fund);
        Map<String,Object> inspection=section("inspection","评估检查","IMPLEMENT",true,impl,select("occurred","发生情况","未发生","已发生"),when(f("batch","年度/季度/检查批次"),"occurred","已发生"),when(f("type","检查类型"),"occurred","已发生"),when(date("date","检查日期"),"occurred","已发生"),when(f("result","检查结论"),"occurred","已发生"),optional("issues","问题清单"),optional("rectification","整改情况"));all.add(inspection);
        all.add(section("change","项目变更","IMPLEMENT",true,impl,select("occurred","发生情况","未发生","已发生"),when(f("name","变更事项"),"occurred","已发生"),when(f("before","调整前内容"),"occurred","已发生"),when(f("after","调整后内容"),"occurred","已发生"),when(f("reason","原因"),"occurred","已发生"),when(date("approvalDate","批准日期"),"occurred","已发生"),when(f("result","批准结果"),"occurred","已发生")));
        all.add(section("deliverable","交付物","IMPLEMENT",true,impl,f("deliverableNo","交付物编号"),f("name","名称"),f("type","类型"),f("indicators","对应考核指标"),date("planDate","计划交付日期"),select("status","交付状态","未交付","已交付"),when(date("actualDate","实际交付日期"),"status","已交付"),f("ownership","权属"),f("milestoneRef","关联里程碑编号"),optional("achievementRef","关联成果编号")));
        Map<String,Object> acceptance=section("acceptance","项目验收","ACCEPT",true,phase>=3,select("level","验收层级",acceptanceLevels(p,c).toArray(new String[0])),date("applicationDate","申请日期"),select("status","验收状态","申请中","已完成"),when(date("completionDate","完成日期"),"status","已完成"),when(f("result","验收结论"),"status","已完成"),optional("issues","遗留问题"),optional("rectification","整改情况"));all.add(acceptance);
        all.add(section("partner","协作单位评价","ACCEPT",true,phase>=3,select("occurred","外协情况","无外协","有外协"),when(f("organization","协作单位"),"occurred","有外协"),when(f("type","协作类型"),"occurred","有外协"),when(f("contractRef","关联合同/项目"),"occurred","有外协"),when(date("date","评价日期"),"occurred","有外协"),when(num("score","评分"),"occurred","有外协"),when(f("grade","等级"),"occurred","有外协")));
        all.add(section("transform","成果及成果转化","TRANSFORM",true,impl,select("status","成果状态","暂无成果","未启动转化","技术储备","转化中","已完成转化"),optional("achievementNo","成果编号"),optional("name","成果名称"),optional("description","成果简介"),optional("deliverableRef","关联交付物编号"),optional("organization","责任单位"),optional("path","转化路径"),optional("form","转化形式"),field("planDate","计划日期","date",false),when(date("actualDate","实际转化日期"),"status","已完成转化"),optional("aircraftModel","应用型号"),optional("applicationOrg","应用单位"),optional("transaction","交易信息")));
        if(t!=null) {
            channelMaterials(declare,t,t.declaration,"DECLARE",true,"本渠道申报材料，按已经形成的结果补齐");
            channelMaterials(filing,t,t.filing,"FILING",phase>=1,"立项备案已完成时应上传");
            boolean pending=is(t.code,"FGGGXJC","ZDZX");inspection.put("configurationPending",pending);inspection.put("applicability",pending?"需求表5-15未明确，待配置，不得认定完整":t.code.equals("KJZ")?"科技周评估检查不适用":t.code.equals("DFJYJY")?"仅重大项目中期检查适用，按实际批次补录":"按实际检查批次/报告期补录，未发生不要求附件");
            if(t.code.equals("KJZ")){inspection.put("active",false);inspection.put("requiredRows",false);}
            channelMaterials(inspection,t,t.checks,"CHECK",false,"对应检查批次已发生时上传；大飞机研究院仅重大项目适用");
            for(Map<String,Object> m:mats(inspection)){m.put("requiredWhenAll",List.of(map("field","type","value",m.get("name")),map("field","occurred","value","已发生")));m.put("rowScoped",true);}
            for(Map<String,Object> f:(List<Map<String,Object>>)inspection.get("fields"))if("type".equals(f.get("key"))){ f.put("type","select");f.put("options",List.of(t.checks)); }
        } else {declare.put("configurationPending",true);filing.put("configurationPending",true);inspection.put("configurationPending",true);}
        for(String level:acceptanceLevels(p,c)) {
            String[] names=switch(level){case "单位级"->split("验收申请书|技术总结报告|经费决算表|交付物清单");case "公司级"->split("公司级验收申请表|评审专家意见|验收结论");case "国家级"->split("国家级验收申请|主管机关批复|综合绩效评价材料");default->split("属地验收申请|科委验收意见|综合绩效评价材料");};
            for(String name:names){
                Map<String,Object> m=material("ACCEPT_"+stable(level+":"+name),level+"·"+name,false,"该层级材料已经形成时补录；验收完成需补齐结果材料");
                boolean application=name.contains("申请")||is(name,"技术总结报告","经费决算表","交付物清单");
                m.put("requiredWhenAll",application?List.of(map("field","level","value",level)):List.of(map("field","level","value",level),map("field","status","value","已完成")));
                m.put("rowScoped",true);mats(acceptance).add(m);
            }
        }
        acceptance.put("requiredLevels",accepted||is(p.getStatus(),"GOV_ACCEPTED","已通过机关验收")?acceptanceLevels(p,c):is(p.getStatus(),"COMPANY_ACCEPTED","已通过公司级验收")?acceptanceLevels(p,c).stream().filter(l->is(l,"单位级","公司级")).toList():List.of());
        if(acceptanceLevels(p,c).isEmpty())acceptance.put("configurationPending",true);
        for(Map<String,Object> s:all) {
            String key=(String)s.get("key");
            if(is(key,"milestone","plan","deliverable","fund","change","partner","transform"))mats(s).add(material("EVIDENCE_"+key.toUpperCase(Locale.ROOT),switch(key){case "milestone"->"节点完成佐证/核验记录";case "plan"->"计划及办结佐证";case "fund"->"预算/支出/核销凭证";case "change"->"变更及批准依据";case "deliverable"->"交付文件/证书/报告";case "partner"->"评价报告及佐证";default->"成果及转化佐证（按实际路径配置）";},false,"按关联业务记录和形成时点提供，多年度/多批次可上传多份；成果转化不设置通用必传文件"));
        }
        for(Map<String,Object> s:all) {
            String key=(String)s.get("key");
            for(Map<String,Object> m:mats(s)) {
                if(is(key,"milestone","plan","deliverable","fund","change","partner"))m.put("rowScoped",true);
                if("milestone".equals(key)||"plan".equals(key))m.put("requiredWhen",map("field","status","value","已完成"));
                if("fund".equals(key))m.put("required",true);
                if("deliverable".equals(key))m.put("requiredWhen",map("field","status","value","已交付"));
                if("change".equals(key))m.put("requiredWhen",map("field","occurred","value","已发生"));
                if("partner".equals(key))m.put("requiredWhen",map("field","occurred","value","有外协"));
            }
        }
        return all;
    }
    private static List<String> acceptanceLevels(ProjInfo p,ProjChannel c) {
        String level=p.getLevelCode();if(level==null&&c!=null)level=c.getLevelCode();
        if(is(level,"NATIONAL","国家级"))return List.of("单位级","公司级","国家级");
        if(is(level,"LOCAL","地方级"))return List.of("单位级","属地主管部门");
        if(is(level,"COMPANY","公司级"))return List.of("单位级","公司级");return List.of();
    }
}
