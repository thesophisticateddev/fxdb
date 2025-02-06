package org.fxsql.dbclient.db;

import com.google.inject.AbstractModule;

public class DatabaseModule extends AbstractModule {

    @Override
    public void configure(){
        try {
            bind(DatabaseManager.class).toConstructor(DatabaseManager.class.getConstructor());
        }
        catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}
