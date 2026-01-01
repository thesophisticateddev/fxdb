package org.fxsql.exceptions;

public class DriverNotFoundException extends Exception {
    public DriverNotFoundException(String message, Exception e){
        super(message,e);
    }

    public DriverNotFoundException(Exception e){
        super("Driver not found for the database",e);
    }
}
