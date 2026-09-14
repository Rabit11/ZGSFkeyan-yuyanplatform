package com.comac.rpm.modules.file;

import com.comac.rpm.common.BusinessException;
import com.comac.rpm.config.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * MinIO 对象存储：政策文件 / 业务附件。
 */
@Service
public class MinioStorageService {

    private final MinioClient minioClient;
    private final MinioProperties props;

    public MinioStorageService(MinioClient minioClient, MinioProperties props) {
        this.minioClient = minioClient;
        this.props = props;
    }

    @PostConstruct
    public void ensureBucket() {
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder()
                    .bucket(props.getBucket()).build());
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(props.getBucket()).build());
            }
        } catch (Exception e) {
            throw new IllegalStateException("MinIO 初始化失败：" + e.getMessage(), e);
        }
    }

    public Map<String, Object> upload(MultipartFile file, String bizType) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("上传文件不能为空");
        }
        String original = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String safeName = original.replaceAll("[\\\\/\\s]+", "_");
        String prefix = (bizType == null || bizType.isBlank()) ? "misc" : bizType.trim().toLowerCase();
        String objectKey = prefix + "/" + LocalDate.now() + "/" + UUID.randomUUID().toString().replace("-", "")
                + "_" + safeName;
        try (InputStream in = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(props.getBucket())
                    .object(objectKey)
                    .stream(in, file.getSize(), -1)
                    .contentType(file.getContentType() == null ? "application/octet-stream" : file.getContentType())
                    .build());
        } catch (Exception e) {
            throw new BusinessException("上传到 MinIO 失败：" + e.getMessage());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("objectKey", objectKey);
        data.put("fileName", original);
        data.put("fileSize", file.getSize());
        data.put("contentType", file.getContentType());
        data.put("fileUrl", buildAccessUrl(objectKey));
        data.put("bucket", props.getBucket());
        return data;
    }

    public InputStream download(String objectKey) {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(props.getBucket()).object(objectKey).build());
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(props.getBucket()).object(objectKey).build());
        } catch (Exception e) {
            throw new BusinessException("下载失败：" + e.getMessage());
        }
    }

    public void delete(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(props.getBucket()).object(objectKey).build());
        } catch (Exception e) {
            throw new BusinessException("删除对象失败：" + e.getMessage());
        }
    }

    public String buildAccessUrl(String objectKey) {
        String base = props.getPublicBaseUrl();
        if (base != null && !base.isBlank()) {
            if (base.endsWith("/")) {
                return base + objectKey;
            }
            return base + "/" + objectKey;
        }
        // 默认走后端下载接口
        return "/api/files/download?objectKey=" + objectKey;
    }
}
