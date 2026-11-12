package com.demo.ragingest.api;

import com.demo.ragingest.api.dto.ErrorResponse;
import com.demo.ragingest.application.NotAcceptingException;
import com.demo.ragingest.application.QueueFullException;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** 将领域异常映射为 HTTP 状态码（429 反压 / 503 停机等）。 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 内存队列满 → 429 QUEUE_FULL。 */
    @ExceptionHandler(QueueFullException.class)
    public ResponseEntity<ErrorResponse> handleQueueFull(QueueFullException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .body(new ErrorResponse("QUEUE_FULL", ex.getMessage()));
    }

    /** 优雅停机中 → 503 NOT_ACCEPTING。 */
    @ExceptionHandler(NotAcceptingException.class)
    public ResponseEntity<ErrorResponse> handleNotAccepting(NotAcceptingException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(new ErrorResponse("NOT_ACCEPTING", ex.getMessage()));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(err -> err.getField() + " " + err.getDefaultMessage())
            .orElse("validation failed");
        return ResponseEntity.badRequest().body(new ErrorResponse("VALIDATION_ERROR", message));
    }
}
