/**
 * Framework-neutral shared kernel for the project's error model.
 * <p>
 * Domain, Application, and Adapter layers may all depend on this package.
 * <p>
 * MUST NOT import: {@code org.springframework.web.*}, {@code org.springframework.http.*},
 * {@code jakarta.servlet.*}, {@code jakarta.persistence.*}, or any framework-coupled type.
 * The {@code ErrorCategory -> HttpStatus} mapping lives in
 * {@code adapter/in/web/error/ErrorCategoryHttpStatusMapper}.
 * <p>
 * The name "error" here refers to the HTTP/API contract sense and is unrelated to
 * {@link java.lang.Error} (a JVM-level type for unrecoverable conditions).
 * The package bundles {@link com.dunowljj.board.common.error.BusinessException},
 * {@link com.dunowljj.board.common.error.ErrorCode}, and
 * {@link com.dunowljj.board.common.error.ErrorCategory} as a single failure model.
 * <p>
 * See ADR-0003 (Hexagonal Architecture) and ADR-0005 (Exception/Error Response Policy).
 */
package com.dunowljj.board.common.error;
