package com.dunowljj.board.adapter.in.web.exception;

import com.dunowljj.board.adapter.in.web.dto.response.ErrorResponse;
import com.dunowljj.board.adapter.in.web.error.ErrorCategoryHttpStatusMapper;
import com.dunowljj.board.common.error.BusinessException;
import com.dunowljj.board.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex,
                                                        HttpServletRequest request) {
        HttpStatus status = ErrorCategoryHttpStatusMapper.toHttpStatus(ex.errorCode().category());
        ErrorResponse body = ErrorResponse.of(ex.errorCode(), request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex,
                                                          HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.of(ErrorCode.INTERNAL_ERROR, request.getRequestURI());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}
