package com.atlasglobal.integration.bcb;

/** Operational error messages omit upstream payloads. */
public class BcbException extends RuntimeException {
    BcbException(String message) { super(message); }
    BcbException(String message, Throwable cause) { super(message, cause); }
}
