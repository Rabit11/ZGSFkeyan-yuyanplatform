package com.comac.rpm.modules.supplement;
import com.comac.rpm.common.R;
import com.comac.rpm.modules.file.MinioStorageService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.StreamUtils;
import java.util.*;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/supplement")
public class SupplementController {
 private final SupplementService service;
 private final MinioStorageService storage;
 public SupplementController(SupplementService service,MinioStorageService storage){this.service=service;this.storage=storage;}
 @GetMapping public R<List<Map<String,Object>>> list(@RequestParam(defaultValue="mine") String view){return R.ok(service.list(view));}
 @GetMapping("/{id}") public R<Map<String,Object>> detail(@PathVariable Long id){return R.ok(service.detail(id));}
 @GetMapping("/{id}/publication") public R<Map<String,Object>> publication(@PathVariable Long id){return R.ok(service.publication(id));}
 @GetMapping("/{id}/approved") public R<List<Map<String,Object>>> approved(@PathVariable Long id){return R.ok(service.approved(id));}
 @PutMapping("/{id}/sections/{key}") public R<Map<String,Object>> save(@PathVariable Long id,@PathVariable String key,@RequestBody Map<String,Object> body){return R.ok(service.save(id,key,body));}
 @PostMapping("/{id}/sections/{key}/submit") public R<Map<String,Object>> submit(@PathVariable Long id,@PathVariable String key,@RequestBody Map<String,Object> body){return R.ok(service.submit(id,key,body));}
 @PostMapping("/{id}/sections/{key}/audit") public R<Map<String,Object>> audit(@PathVariable Long id,@PathVariable String key,@RequestBody Map<String,Object> body){return R.ok(service.audit(id,key,body));}
 @PostMapping("/{id}/sections/{key}/reopen") public R<Map<String,Object>> reopen(@PathVariable Long id,@PathVariable String key,@RequestBody Map<String,Object> body){return R.ok(service.reopen(id,key,body));}
 @PostMapping("/{id}/sections/{key}/materials/{code}") public R<Map<String,Object>> upload(@PathVariable Long id,@PathVariable String key,@PathVariable String code,@RequestParam long version,@RequestParam(required=false) String rowId,@RequestParam MultipartFile file){return R.ok(service.upload(id,key,code,version,rowId,file));}
 @GetMapping("/{id}/history/{historyId}") public R<Map<String,Object>> history(@PathVariable Long id,@PathVariable Long historyId){return R.ok(service.history(id,historyId));}
 @GetMapping("/{id}/legacy-files/{materialId}") public void legacyDownload(@PathVariable Long id,@PathVariable Long materialId,HttpServletResponse response) throws Exception {
  Map<String,Object> file=service.legacyFile(id,materialId);
  response.setHeader("Content-Disposition","attachment; filename*=UTF-8''"+URLEncoder.encode(String.valueOf(file.get("file_name")),StandardCharsets.UTF_8));
  response.setHeader("X-Content-Type-Options","nosniff");response.setHeader("Cache-Control","private, no-store");response.setContentType("application/octet-stream");
  try(InputStream in=storage.download(String.valueOf(file.get("object_key")))){StreamUtils.copy(in,response.getOutputStream());}
 }
 @GetMapping("/{id}/files/{fileId}") public void download(@PathVariable Long id,@PathVariable String fileId,@RequestParam(defaultValue="false") boolean inline,HttpServletResponse response) throws Exception {
  Map<String,Object> file=service.file(id,fileId);String mime=String.valueOf(file.get("content_type"));
  boolean safe=inline && Set.of("application/pdf","image/png","image/jpeg","image/gif","image/webp","text/plain").contains(mime);
  response.setHeader("Content-Disposition",(safe?"inline":"attachment")+"; filename*=UTF-8''"+URLEncoder.encode(String.valueOf(file.get("file_name")),StandardCharsets.UTF_8));
  response.setHeader("X-Content-Type-Options","nosniff");response.setHeader("Cache-Control","private, no-store");response.setHeader("Content-Security-Policy","sandbox");
  response.setContentType(safe?mime:"application/octet-stream");
  try(InputStream in=storage.download(String.valueOf(file.get("object_key")))){StreamUtils.copy(in,response.getOutputStream());}
 }
}
