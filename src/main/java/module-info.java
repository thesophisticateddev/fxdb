module org.fxsql.dbclient {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    requires org.kordamp.ikonli.feather;
    requires eu.hansolo.tilesfx;
    requires atlantafx.base;
    requires org.kordamp.ikonli.core;
    requires java.sql;
    requires tablesaw.core;
    requires com.google.guice;

    opens org.fxsql.dbclient to javafx.fxml, com.google.guice;
    exports org.fxsql.dbclient.components;
    exports org.fxsql.dbclient.controller;
    exports org.fxsql.dbclient.utils;
    exports org.fxsql.dbclient.db;
    exports org.fxsql.dbclient;
    opens org.fxsql.dbclient.utils to javafx.fxml;

}