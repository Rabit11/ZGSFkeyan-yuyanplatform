package com.comac.rpm.modules.supplement;
import com.comac.rpm.common.R;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
@RequestMapping("/api/supplement/config/channels")
public class SupplementTemplateController {
 private final SupplementTemplateService service;
 public SupplementTemplateController(SupplementTemplateService service){this.service=service;}
 @GetMapping("/{code}") public R<Map<String,Object>> get(@PathVariable String code){return R.ok(service.get(code));}
 @PutMapping("/{code}") public R<Map<String,Object>> save(@PathVariable String code,@RequestBody Map<String,Object> body){return R.ok(service.save(code,body));}
}
