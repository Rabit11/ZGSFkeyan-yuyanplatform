package com.comac.rpm.modules.change.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/** Module-only bridge behind the existing authenticated API. No browser-side second port. */
@Controller
@RequestMapping("/api/changes/lab")
public class ChangeLabBridgeController {
  private final HttpClient client = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();

  static boolean allowed(String path, String method) {
    if ("/auth/login".equals(path)) return "POST".equals(method);
    if ("/changes".equals(path)) return Set.of("GET", "POST").contains(method);
    if ("/changes/context".equals(path)) return "GET".equals(method);
    if (path.matches("/changes/[0-9]+")) return Set.of("GET", "PUT", "DELETE").contains(method);
    if (path.matches("/changes/[0-9]+/(submit|audit|archive|files)")) return "POST".equals(method);
    if (path.matches("/changes/[0-9]+/files/[0-9]+")) return "DELETE".equals(method);
    return path.matches("/changes/[0-9]+/files/[0-9]+/download") && "GET".equals(method);
  }

  @RequestMapping("/**")
  public void forward(HttpServletRequest request, HttpServletResponse response) throws Exception {
    // Spring Security has already verified the ORIGINAL platform Authorization/JWT.
    // The independent lab token is used only for the fixed internal upstream.
    String path = request.getRequestURI().substring(request.getContextPath().length() + "/api/changes/lab".length());
    if (!allowed(path, request.getMethod())) {
      error(response, 404, "该入口仅支持项目变更演练接口");
      return;
    }
    String labToken = request.getHeader("X-Change-Lab-Token");
    if (!"/auth/login".equals(path) && (labToken == null || labToken.isBlank())) {
      error(response, 401, "演练会话失效，请在项目变更页面重新连接演练");
      return;
    }
    String query = request.getQueryString();
    // Both host and path are fixed/allowlisted; caller cannot choose a destination or recurse.
    var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:8081/api" + path
        + (query == null ? "" : "?" + query))).timeout(Duration.ofSeconds(30));
    if (labToken != null && !labToken.isBlank()) builder.header("Authorization", "Bearer " + labToken);
    byte[] body;
    String contentType = request.getContentType();
    if (contentType != null && contentType.toLowerCase().startsWith("multipart/form-data")) {
      // Spring's multipart resolver may already have consumed the raw body.
      String boundary = "ChangeLab" + UUID.randomUUID().toString().replace("-", "");
      var bytes = new ByteArrayOutputStream();
      for (Part part : request.getParts()) {
        if (!"file".equals(part.getName())) continue;
        bytes.write(("--" + boundary + "\r\nContent-Disposition: " + part.getHeader("Content-Disposition")
            + "\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        try (var stream = part.getInputStream()) { stream.transferTo(bytes); }
        bytes.write("\r\n".getBytes(StandardCharsets.UTF_8));
      }
      bytes.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
      body = bytes.toByteArray();
      builder.header("Content-Type", "multipart/form-data; boundary=" + boundary);
    } else {
      body = request.getInputStream().readAllBytes();
      if (contentType != null) builder.header("Content-Type", contentType);
    }
    builder.method(request.getMethod(), body.length == 0 ? HttpRequest.BodyPublishers.noBody()
        : HttpRequest.BodyPublishers.ofByteArray(body));
    try {
      var upstream = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
      response.setStatus(upstream.statusCode());
      for (String header : Set.of("Content-Type", "Content-Disposition"))
        upstream.headers().firstValue(header).ifPresent(value -> response.setHeader(header, value));
      response.setHeader("Cache-Control", "no-store");
      try (var stream = upstream.body()) { stream.transferTo(response.getOutputStream()); }
    } catch (IOException e) {
      if (!response.isCommitted()) error(response, 503, "演练服务暂不可用，请重试；无需增加隧道端口");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      if (!response.isCommitted()) error(response, 503, "演练连接被中断，请重试");
    }
  }

  private static void error(HttpServletResponse response, int code, String message) throws IOException {
    response.setStatus(code);
    response.setContentType("application/json;charset=UTF-8");
    response.getWriter().write("{\"code\":" + code + ",\"msg\":\"" + message + "\"}");
  }
}
