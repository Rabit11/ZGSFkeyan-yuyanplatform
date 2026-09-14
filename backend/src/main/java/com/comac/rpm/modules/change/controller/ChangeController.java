package com.comac.rpm.modules.change.controller;

import com.comac.rpm.common.*;
import com.comac.rpm.modules.change.service.ChangeService;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/changes")
public class ChangeController {
  private final ChangeService service;

  public ChangeController(ChangeService service) {
    this.service = service;
  }

  @GetMapping
  public R<PageVO<Map<String, Object>>> page(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "10") int size,
      @RequestParam(required = false) Long projectId,
      @RequestParam(required = false) String changeType,
      @RequestParam(required = false) String status,
      @RequestParam(required = false) String keyword,
      @RequestParam(defaultValue = "false") boolean mine) {
    return R.ok(service.page(page, size, projectId, changeType, status, keyword, mine));
  }

  @GetMapping("/context")
  public R<?> context(@RequestParam(required = false) Long projectId) {
    return R.ok(service.context(projectId));
  }

  @GetMapping("/{id}")
  public R<?> detail(@PathVariable long id) {
    return R.ok(service.detail(id));
  }

  @PostMapping
  public R<?> create(@RequestBody ChangeService.SaveRequest body) {
    return R.ok(service.save(null, body));
  }

  @PutMapping("/{id}")
  public R<?> update(@PathVariable long id, @RequestBody ChangeService.SaveRequest body) {
    return R.ok(service.save(id, body));
  }

  @PostMapping("/{id}/submit")
  public R<?> submit(@PathVariable long id, @RequestBody Map<String, Integer> body) {
    return R.ok(service.submit(id, body.get("revision")));
  }

  @PostMapping("/{id}/audit")
  public R<?> audit(@PathVariable long id, @RequestBody ChangeService.ReviewRequest body) {
    return R.ok(service.audit(id, body));
  }

  @PostMapping("/{id}/archive")
  public R<?> archive(@PathVariable long id, @RequestBody ChangeService.ArchiveRequest body) {
    return R.ok(service.archive(id, body));
  }

  @DeleteMapping("/{id}")
  public R<?> delete(@PathVariable long id, @RequestParam Integer revision) {
    service.delete(id, revision);
    return R.ok(true);
  }

  @PostMapping("/{id}/files")
  public R<?> upload(
      @PathVariable long id,
      @RequestParam Integer revision,
      @RequestParam String kind,
      @RequestParam MultipartFile file) {
    return R.ok(service.upload(id, revision, kind, file));
  }

  @DeleteMapping("/{id}/files/{fileId}")
  public R<?> removeFile(
      @PathVariable long id, @PathVariable long fileId, @RequestParam Integer revision) {
    return R.ok(service.removeFile(id, fileId, revision));
  }

  @GetMapping("/{id}/files/{fileId}/download")
  public void download(
      @PathVariable long id, @PathVariable long fileId, HttpServletResponse response)
      throws Exception {
    var f = service.file(id, fileId);
    response.setContentType("application/octet-stream");
    response.setHeader(
        "Content-Disposition",
        "attachment; filename*=UTF-8''"
            + URLEncoder.encode(f.get("file_name").toString(), StandardCharsets.UTF_8));
    try (var in = service.download(f.get("object_key").toString())) {
      StreamUtils.copy(in, response.getOutputStream());
    }
  }
}
