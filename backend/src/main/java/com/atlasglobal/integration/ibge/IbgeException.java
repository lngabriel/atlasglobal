package com.atlasglobal.integration.ibge;

class IbgeException extends RuntimeException {
    IbgeException(String message) { super(message); }
    IbgeException(String message, Throwable cause) { super(message, cause); }
}
