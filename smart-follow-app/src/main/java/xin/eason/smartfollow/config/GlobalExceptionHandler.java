package xin.eason.smartfollow.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器
 * <p>
 * 职责：集中捕获并处理应用中抛出的常见异常，统一日志输出与响应内容。
 * 当前版本：先处理最常见的 RuntimeException，并输出中文日志与中文响应信息。
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 处理运行时异常（RuntimeException）。
     * <p>
     * - 使用 Slf4j 记录中文错误信息与异常对象（包含堆栈）。
     * - 返回标准的 500 响应，并给出中文错误提示，避免将内部细节直接暴露给调用方。
     *
     * @param ex   捕获到的运行时异常
     * @param request 当前请求对象，用于获取请求路径等上下文信息
     * @return 标准错误响应（HTTP 500）
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex, HttpServletRequest request) {
        // 记录错误日志（中文信息 + 异常堆栈）
        log.error("[全局异常] 捕获到运行时异常：{}，请求路径：{}", ex.getMessage(), request.getRequestURI(), ex);

        // 构造一个简单统一的错误响应体（中文）
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now().toString()); // 时间戳
        body.put("status", HttpStatus.INTERNAL_SERVER_ERROR.value()); // HTTP 状态码
        body.put("error", "服务器内部错误"); // 错误标题（中文）
        body.put("message", "服务器内部错误，请稍后重试"); // 统一的中文错误提示
        body.put("path", request.getRequestURI()); // 请求路径

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}