package org.fxsql.listeners;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.EventHandler;
import javafx.scene.control.ComboBox;
import org.fxsql.DatabaseManager;
import org.fxsql.events.EventBus;
import org.fxsql.events.NewConnectionAddedEvent;

import java.util.Set;

public class NewConnectionAddedListener extends BaseListener {

    private DatabaseManager databaseManager;

    private ComboBox<String> comboBox;
    public NewConnectionAddedListener() {
        EventHandler<NewConnectionAddedEvent> handler = event -> Platform.runLater(() -> {
            assert databaseManager != null;
            //Get connection list
            Set<String> connectionList = databaseManager.getConnectionList();
//            connectionList.add("none");
            //Update the list in the combo box
            assert comboBox != null;
            comboBox.setItems(FXCollections.observableArrayList(connectionList));

            System.out.println("Combo box updated");
        });

        EventBus.addEventHandler(NewConnectionAddedEvent.NEW_CONNECTION_ADDED, handler);
    }

    public void setDatabaseManager(DatabaseManager dm){
        this.databaseManager = dm;
    }

    public void setComboBox(ComboBox<String> cmb){
        this.comboBox = cmb;
    }

}
