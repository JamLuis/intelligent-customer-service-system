package com.company.smartsupport.common;

public class SmartSupportException extends RuntimeException {

    private final String code;

    public SmartSupportException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
