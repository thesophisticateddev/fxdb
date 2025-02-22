module org.fxsql.fxdb {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;


    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    requires eu.hansolo.tilesfx;
    requires org.kordamp.ikonli.core;
    requires atlantafx.base;
    requires com.google.guice;
    requires org.kordamp.ikonli.feather;
    requires java.sql;
    requires tablesaw.core;
    requires com.fasterxml.jackson.databind;
    requires org.fxmisc.richtext;

    opens org.fxsql.fxdb to javafx.fxml, com.google.guice, com.fasterxml.jackson.databind;
    exports org.fxsql.fxdb;
    exports org.fxsql.fxdb.databaseManagement to com.google.guice, com.fasterxml.jackson.databind;

    exports org.fxsql.fxdb.components;
    exports org.fxsql.fxdb.controller;
    exports org.fxsql.fxdb.utils;
    exports org.fxsql.fxdb.listeners;
    exports org.fxsql.fxdb.services;

}