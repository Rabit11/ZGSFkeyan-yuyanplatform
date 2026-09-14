package com.comac.rpm.modules.file;

import com.comac.rpm.common.BusinessException;
import com.comac.rpm.common.UserContext;
import com.comac.rpm.modules.transform.TransformAccess;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.InputStream;
import java.util.*;

@Service
public class TransformFileService {
    private final JdbcTemplate jdbc;
    private final MinioStorageService storage;
    private final TransformAccess access;
    public TransformFileService(JdbcTemplate jdbc,MinioStorageService storage,TransformAccess access) {
        this.jdbc=jdbc;this.storage=storage;this.access=access;
    }
    @PostConstruct public void initialize() {
        new ResourceDatabasePopulator(new ClassPathResource("db/migration_transform_files_v1.sql")).execute(Objects.requireNonNull(jdbc.getDataSource()));
    }
    public Map<String,Object> upload(Long projectId,MultipartFile file) {
        access.requireOwner(projectId);
        if(file==null || file.isEmpty() || file.getSize()>100L*1024*1024) throw new BusinessException(400,"文件不能为空且不能超过100MB");
        if(Objects.toString(file.getOriginalFilename(),"file").length()>500) throw new BusinessException(400,"文件名过长");
        Map<String,Object> uploaded=storage.upload(file,"transform-private");
        String id=UUID.randomUUID().toString();
        try {
            jdbc.update("INSERT INTO achv_transform_file(id,project_id,object_key,file_name,file_size,uploaded_by) VALUES(?,?,?,?,?,?)",id,projectId,uploaded.get("objectKey"),uploaded.get("fileName"),file.getSize(),UserContext.getUserId());
        } catch(RuntimeException ex) {storage.delete(String.valueOf(uploaded.get("objectKey")));throw ex;}
        uploaded.put("fileUrl",TransformFilePolicy.URL_PREFIX+id);
        return uploaded;
    }
    public Map<String,Object> file(String id) {
        TransformFilePolicy.fileId(TransformFilePolicy.URL_PREFIX+id);
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM achv_transform_file WHERE id=?",id);
        if(rows.isEmpty()) throw new BusinessException(404,"附件不存在");
        Map<String,Object> row=rows.get(0);
        access.requireReadable(((Number)row.get("project_id")).longValue());
        return row;
    }
    public void validateEvidence(Long projectId,String url) {
        String id=TransformFilePolicy.fileId(url);
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT object_key FROM achv_transform_file WHERE id=? AND project_id=?",id,projectId);
        if(rows.isEmpty()) throw new BusinessException(400,"附件未上传或不属于本项目");
        try(InputStream ignored=storage.download(String.valueOf(rows.get(0).get("object_key")))) {
            // Opening through storage performs an object existence check.
        } catch(Exception ex) {throw new BusinessException(400,"佐证文件不可读取，请重新上传");}
    }
}
