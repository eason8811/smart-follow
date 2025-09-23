package xin.eason.smartfollow.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import xin.eason.smartfollow.types.exceptions.AppException;
import xin.eason.smartfollow.types.exceptions.IllegalParamException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器
 * <p>
 * 职责：集中捕获并处理应用中抛出的常见异常, 统一日志输出与响应内容。
 * 当前版本：先处理最常见的 RuntimeException, 并输出中文日志与中文响应信息。
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 处理非法参数异常 (IllegalParamException)
     * <ul>
     *     <li>使用 Slf4j 记录中文错误信息与异常对象 (包含堆栈)</li>
     *     <li>返回标准的 400 响应, 并给出中文错误提示, 提示客户端需要修改请求体或请求参数</li>
     * </ul>
     *
     * @param ex      捕获到的非法参数异常
     * @param request 当前请求对象, 用于获取请求路径等上下文信息
     * @return 标准错误响应 (HTTP 400)
     */
    @ExceptionHandler(IllegalParamException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalParamException(IllegalParamException ex, HttpServletRequest request) {
        // 记录错误日志 (中文信息 + 异常堆栈)
        log.error("[全局异常] 捕获到非法参数异常：{}, 请求路径：{}", ex.getMessage(), request.getRequestURI(), ex);

        Map<String, Object> body = packingResponseBody(HttpStatus.BAD_REQUEST, "[参数错误] " + ex.getMessage(), request);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    /**
     * 处理系统内异常 (AppException)
     * <ul>
     *     <li>使用 Slf4j 记录中文错误信息与异常对象 (包含堆栈)</li>
     *     <li>返回标准的 500 响应, 并给出中文错误提示, 避免将内部细节直接暴露给调用方</li>
     * </ul>
     *
     * @param ex      捕获到的系统内异常
     * @param request 当前请求对象, 用于获取请求路径等上下文信息
     * @return 标准错误响应 (HTTP 500)
     */
    @ExceptionHandler(AppException.class)
    public ResponseEntity<Map<String, Object>> handleAppException(AppException ex, HttpServletRequest request) {
        // 记录错误日志 (中文信息 + 异常堆栈)
        log.error("[全局异常] 捕获到系统内异常：{}, 请求路径：{}", ex.getMessage(), request.getRequestURI(), ex);

        Map<String, Object> body = packingResponseBody(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误, 请稍后重试", request);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }


    /**
     * 处理运行时异常 (RuntimeException)
     * <ul>
     *     <li>使用 Slf4j 记录中文错误信息与异常对象 (包含堆栈)</li>
     *     <li>返回标准的 500 响应, 并给出中文错误提示, 避免将内部细节直接暴露给调用方</li>
     * </ul>
     *
     * @param ex      捕获到的运行时异常
     * @param request 当前请求对象, 用于获取请求路径等上下文信息
     * @return 标准错误响应 (HTTP 500)
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(RuntimeException ex, HttpServletRequest request) {
        // 记录错误日志 (中文信息 + 异常堆栈)
        log.error("[全局异常] 捕获到运行时异常：{}, 请求路径：{}", ex.getMessage(), request.getRequestURI(), ex);

        Map<String, Object> body = packingResponseBody(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误, 请稍后重试", request);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    /**
     * 构造一个标准的 HTTP 响应体, 包含时间戳, 状态码, 错误信息, 消息以及请求路径等信息
     *
     * @param status  HTTP 状态, 用于设置响应的状态码和错误原因短语
     * @param message 自定义的消息, 将被包含在响应体中作为错误或提示信息
     * @param request 当前的 HTTP 请求对象, 用于获取请求路径等上下文信息
     * @return 一个映射, 键为字符串, 值为对象, 包含了构造好的响应体所需的所有信息
     */
    private Map<String, Object> packingResponseBody(HttpStatus status, String message, HttpServletRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());          // 例如 "Bad Request"
        body.put("message", message);
        body.put("path", request.getRequestURI());
        return body;
    }
}