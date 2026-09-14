package com.comac.rpm.modules.declaration;

import com.comac.rpm.common.BusinessException;
import com.comac.rpm.modules.declaration.entity.ProjDeclaration;
import com.comac.rpm.modules.dict.entity.ProjChannel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/** 仅用于申报。前端和 Mock 直接读取同一个版本化资源。 */
@Component
public class DeclarationWorkflow {
    public record Node(String code, String title, List<String> roleKeys, String kind, boolean evidence) {}
    public record Catalog(Map<String, String> channels, Map<String, String> names,
                          Map<String, List<String>> templates, List<Node> nodes) {}
    private final Catalog catalog;

    public DeclarationWorkflow(ObjectMapper mapper) throws IOException {
        try (var stream = new ClassPathResource("declaration-workflows.json").getInputStream()) {
            catalog = mapper.readValue(stream, Catalog.class);
        }
    }

    public String select(ProjChannel channel) {
        if (channel == null) return "common-v1";
        String byCode = catalog.channels().get(channel.getChannelCode());
        String byName = catalog.names().get(channel.getChannelName());
        if (byCode != null && !"common-v1".equals(byCode)) return byCode;
        if (byName != null) return byName;
        String flow = channel.getFlowNodes() == null ? "" : channel.getFlowNodes();
        if (flow.contains("无需审批") || flow.contains("直接报备")) return "report-v1";
        return byCode == null ? "common-v1" : byCode;
    }

    public String version(ProjDeclaration declaration, Map<String, String> posts) {
        String version = posts.get("__workflow");
        if (version != null && !catalog.templates().containsKey(version))
            throw new BusinessException("不支持的申报流程版本：" + version);
        if (version == null && declaration != null && declaration.getChannelName() != null) {
            String mapped = catalog.names().get(declaration.getChannelName());
            if (mapped != null) return mapped;
        }
        return version == null ? (Integer.valueOf(0).equals(declaration.getNeedApproval())
                ? "legacy-report-v0" : "legacy-v0") : version;
    }

    public List<Node> nodes(String version) {
        return catalog.templates().get(version).stream().map(code -> catalog.nodes().stream()
                .filter(node -> code.equals(node.code())).findFirst().orElseThrow()).toList();
    }

    public List<Node> auditNodes(String version) {
        return nodes(version).stream().filter(n -> !"fill".equals(n.kind()) && !"end".equals(n.kind())).toList();
    }

    public Node current(ProjDeclaration declaration, Map<String, String> posts) {
        String title = "承办部门负责人".equals(declaration.getFlowNode())
                ? "项目承担部门负责人" : declaration.getFlowNode();
        return auditNodes(version(declaration, posts)).stream().filter(n -> n.title().equals(title))
                .findFirst().orElse(null);
    }
}
