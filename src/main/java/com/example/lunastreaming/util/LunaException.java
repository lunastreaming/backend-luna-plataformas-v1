package com.example.lunastreaming.util;

public class LunaException {
    private LunaException() { /* utilitario */ }

    // -----------------------
    // Subclases de excepción
    // -----------------------

    public static class NotFoundException extends RuntimeException {
        public NotFoundException(String message) { super(message); }
        public NotFoundException(String message, Throwable cause) { super(message, cause); }
    }

    public static class AccessDeniedException extends RuntimeException {
        public AccessDeniedException(String message) { super(message); }
        public AccessDeniedException(String message, Throwable cause) { super(message, cause); }
    }

    public static class InvalidStateException extends RuntimeException {
        public InvalidStateException(String message) { super(message); }
        public InvalidStateException(String message, Throwable cause) { super(message, cause); }
    }

    // -----------------------
    // Métodos factory (opcionales)
    // -----------------------

    public static NotFoundException notFound(String message) {
        return new NotFoundException(message);
    }

    public static AccessDeniedException accessDenied(String message) {
        return new AccessDeniedException(message);
    }

    public static InvalidStateException invalidState(String message) {
        return new InvalidStateException(message);
    }

    // Métodos factory con causa
    public static NotFoundException notFound(String message, Throwable cause) {
        return new NotFoundException(message, cause);
    }

    public static AccessDeniedException accessDenied(String message, Throwable cause) {
        return new AccessDeniedException(message, cause);
    }

    public static InvalidStateException invalidState(String message, Throwable cause) {
        return new InvalidStateException(message, cause);
    }

}
