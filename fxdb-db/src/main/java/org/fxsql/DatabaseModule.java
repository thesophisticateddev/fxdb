package org.fxsql;

import com.google.inject.AbstractModule;
import org.fxsql.driverload.DriverDownloader;

public class DatabaseModule extends AbstractModule {

    @Override
    public void configure(){
        //            bind(DatabaseManager.class).toConstructor(DatabaseManager.class.getConstructor());
        bind(DatabaseManager.class).asEagerSingleton();
        bind(DriverDownloader.class).asEagerSingleton();
    }
}
