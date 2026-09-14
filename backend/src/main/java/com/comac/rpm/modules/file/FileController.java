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
    private final TransformFileService transformFiles;

    public FileController(MinioStorageService storageService, FlowAuditGuard flowAuditGuard, TransformFileService transformFiles) {
        this.storageService = storageService;
        this.flowAuditGuard = flowAuditGuard;
        this.transformFiles = transformFiles;
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
        if (objectKey.startsWith("supplement-private/")) throw new com.comac.rpm.common.BusinessException(403, "请从导入项目补录授权入口下载");
        if (objectKey.startsWith("transform-private/")) throw new com.comac.rpm.common.BusinessException(403, "请从成果转化授权入口下载");
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
        if (objectKey.startsWith("supplement-private/")) throw new com.comac.rpm.common.BusinessException(403, "补录历史附件不可删除");
        if (objectKey.startsWith("transform-private/")) throw new com.comac.rpm.common.BusinessException(403, "成果转化历史附件不可删除");
        flowAuditGuard.requireAdmin("删除存储附件");
        storageService.delete(objectKey);
        return R.ok(true);
    }

    @PostMapping("/transform/upload")
    public R<Map<String,Object>> uploadTransform(@RequestParam("projectId") Long projectId,@RequestParam("file") MultipartFile file) {
        return R.ok(transformFiles.upload(projectId,file));
    }

    @GetMapping("/transform/download")
    public void downloadTransform(@RequestParam("fileId") String fileId,HttpServletResponse response) throws Exception {
        Map<String,Object> file=transformFiles.file(fileId);
        response.setHeader("X-Content-Type-Options","nosniff");
        response.setHeader("Cache-Control","private, no-store");
        response.setHeader("Content-Disposition","attachment; filename*=UTF-8''"+URLEncoder.encode(String.valueOf(file.get("file_name")),StandardCharsets.UTF_8));
        response.setContentType("application/octet-stream");
        try(InputStream in=storageService.download(String.valueOf(file.get("object_key")))) {StreamUtils.copy(in,response.getOutputStream());}
    }
}
