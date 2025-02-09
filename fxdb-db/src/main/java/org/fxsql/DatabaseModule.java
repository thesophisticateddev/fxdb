package org.fxsql;

import com.google.inject.AbstractModule;

public class DatabaseModule extends AbstractModule {

    @Override
    public void configure(){
        //            bind(DatabaseManager.class).toConstructor(DatabaseManager.class.getConstructor());
        bind(DatabaseManager.class).asEagerSingleton();

    }
}
