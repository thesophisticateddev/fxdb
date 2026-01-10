package org.fxsql;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import org.fxsql.driverload.DriverDownloader;
import org.fxsql.driverload.JDBCDriverLoader;
import org.fxsql.service.WindowManager;

public class DatabaseModule extends AbstractModule {

    @Override
    public void configure(){
        //            bind(DatabaseManager.class).toConstructor(DatabaseManager.class.getConstructor());
        bind(DatabaseManager.class).asEagerSingleton();
        bind(DriverDownloader.class).asEagerSingleton();
        System.out.println("driver downloader init");
        bind(JDBCDriverLoader.class).asEagerSingleton();
        bind(WindowManager.class).asEagerSingleton();
    }
}
