package com.atlasglobal.integration.oecd;

/** Safe operational failure without provider response payloads. */
public class OecdException extends RuntimeException {
    OecdException(String message) { super(message); }
    OecdException(String message, Throwable cause) { super(message, cause); }
}
