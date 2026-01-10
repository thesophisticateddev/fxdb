package org.fxsql.exceptions;

public class DriverNotInstalledException extends RuntimeException {
    public DriverNotInstalledException(String message) {
        super(message);
    }
}
