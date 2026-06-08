package com.dunowljj.board.adapter.out.persistence.common;

import org.springframework.dao.DataIntegrityViolationException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * unique constraint 위반을 도메인 예외로 변환하며 persistence 호출을 감싸는 재사용 가드.
 *
 * <p>application 의 사전 {@code exists} 검증과 INSERT 사이 race 로 동시 가입이 일어나면
 * {@link DataIntegrityViolationException} 이 발생한다. constraint name → 도메인 예외 supplier 를
 * 등록한 뒤 {@link #execute} 로 저장 호출을 감싸면, try/catch 가 가드 안으로 숨는다. 어느
 * constraint 와도 매칭되지 않으면 원본을 그대로 전파한다.
 *
 * <p>예외 supplier 가 closure 로 필요한 값을 캡처하므로 가드는 도메인 객체에 의존하지 않는다.
 * unique 칼럼이 늘면 {@link #on} 한 줄만 추가하면 된다.
 *
 * <p>{@code org.springframework.dao.*} 예외가 application 으로 새지 않도록 adapter 경계 안에서만 사용.
 */
public final class UniqueViolationGuard {

    private final Map<String, Supplier<? extends RuntimeException>> mappings = new LinkedHashMap<>();

    public UniqueViolationGuard on(String constraintName, Supplier<? extends RuntimeException> exceptionSupplier) {
        mappings.put(constraintName, exceptionSupplier);
        return this;
    }

    /** persistence 호출을 실행하고, unique 위반 시 등록된 도메인 예외로 변환해 던진다. */
    public <T> T execute(Supplier<T> persistence) {
        try {
            return persistence.get();
        } catch (DataIntegrityViolationException e) {
            throw translate(e);
        }
    }

    private RuntimeException translate(DataIntegrityViolationException cause) {
        String message = rootMessage(cause);
        for (Map.Entry<String, Supplier<? extends RuntimeException>> entry : mappings.entrySet()) {
            if (message.contains(entry.getKey())) {
                return entry.getValue().get();
            }
        }
        return cause;
    }

    private static String rootMessage(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (Throwable cause = t; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null) {
                sb.append(cause.getMessage()).append('\n');
            }
        }
        return sb.toString();
    }
}
