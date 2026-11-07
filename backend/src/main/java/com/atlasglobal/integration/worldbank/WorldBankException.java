package com.atlasglobal.integration.worldbank;

public class WorldBankException extends RuntimeException {
    public WorldBankException(String message) { super(message); }
    public WorldBankException(String message, Throwable cause) { super(message, cause); }
}
