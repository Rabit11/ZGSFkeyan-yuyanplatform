package com.comac.rpm.modules.file;

import com.comac.rpm.common.R;
import com.comac.rpm.common.permission.FlowAuditGuard;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 政策 / 附件对象存储接口（MinIO）。
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final MinioStorageService storageService;
    private final FlowAuditGuard flowAuditGuard;

    public FileController(MinioStorageService storageService, FlowAuditGuard flowAuditGuard) {
        this.storageService = storageService;
        this.flowAuditGuard = flowAuditGuard;
    }

    /**
     * 上传附件。bizType 建议：policy / declaration / acceptance / evaluation / evidence
     */
    @PostMapping("/upload")
    public R<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                         @RequestParam(value = "bizType", required = false, defaultValue = "misc") String bizType) {
        flowAuditGuard.requireIdentities("上传附件", "contactLogin", "owner", "techLead", "projectPm",
                "unitHead", "unitStaff", "deptHead", "hqHead", "hqStaff", "finHq", "finHead", "finStaff");
        return R.ok(storageService.upload(file, bizType));
    }

    @GetMapping("/download")
    public void download(@RequestParam("objectKey") String objectKey, HttpServletResponse response) throws Exception {
        String name = objectKey.contains("/") ? objectKey.substring(objectKey.lastIndexOf('/') + 1) : objectKey;
        response.setHeader("Content-Disposition",
                "attachment; filename*=UTF-8''" + URLEncoder.encode(name, StandardCharsets.UTF_8));
        response.setContentType("application/octet-stream");
        try (InputStream in = storageService.download(objectKey)) {
            StreamUtils.copy(in, response.getOutputStream());
        }
    }

    @DeleteMapping
    public R<Boolean> delete(@RequestParam("objectKey") String objectKey) {
        flowAuditGuard.requireAdmin("删除存储附件");
        storageService.delete(objectKey);
        return R.ok(true);
    }
}
