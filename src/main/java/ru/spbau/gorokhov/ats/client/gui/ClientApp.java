package ru.spbau.gorokhov.ats.client.gui;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;
import ru.spbau.gorokhov.ats.client.Client;
import ru.spbau.gorokhov.ats.model.ClientAddress;
import ru.spbau.gorokhov.ats.model.ClientTimeInfo;

import java.io.IOException;

public class ClientApp extends Application {

    private final TextField serverIp = new TextField();

    private final Button connectButton = new Button("Connect");
    private final Button disconnectButton = new Button("Disconnect");

    private Timeline updatingTimes;

    private final ObservableList<ClientTimeInfo> clientsTimes = FXCollections.observableArrayList();

    private Client client;

    @Override
    public void start(Stage primaryStage) throws Exception {
        primaryStage.setTitle("ATS Client 1.0");

        Scene mainScene = buildMainScene();
        primaryStage.setScene(mainScene);
        primaryStage.show();
    }

    private Scene buildMainScene() {
        HBox serverBox = new HBox(new Label("Server IP: "), serverIp);

        connectButton.setOnMouseClicked(e -> {
            connectButton.setDisable(true);
            disconnectButton.setDisable(false);
            connect();
        });

        disconnectButton.setDisable(true);
        disconnectButton.setOnMouseClicked(e -> {
            disconnectButton.setDisable(true);
            connectButton.setDisable(false);
            updatingTimes.stop();
            client.disconnect();
        });

        HBox buttonsBox = new HBox(connectButton, disconnectButton);

        TableView<ClientTimeInfo> clientsTable = new TableView<>();

        TableColumn<ClientTimeInfo, String> clientAddressColumn = new TableColumn<>("Client");
        clientAddressColumn.setCellValueFactory(new PropertyValueFactory<>("clientAddress"));
        clientAddressColumn.setPrefWidth(150);


        TableColumn<ClientTimeInfo, String> clientRealTimeColumn = new TableColumn<>("Real time");
        clientRealTimeColumn.setCellValueFactory(new PropertyValueFactory<>("realTime"));
        clientRealTimeColumn.setPrefWidth(150);

        TableColumn<ClientTimeInfo, String> clientEstimateTimeColumn = new TableColumn<>("Estimate time");
        clientEstimateTimeColumn.setCellValueFactory(new PropertyValueFactory<>("estimateTime"));
        clientEstimateTimeColumn.setPrefWidth(150);

        clientsTable.getColumns().addAll(clientAddressColumn, clientRealTimeColumn, clientEstimateTimeColumn);

        clientsTable.setItems(clientsTimes);

        updatingTimes = new Timeline(new KeyFrame(Duration.seconds(1), (e) -> {
            clientsTimes.clear();
            clientsTimes.add(new ClientTimeInfo(
                    new ClientAddress("Me: " + client.getIp(), client.getPort()),
                    client.getRealTime(),
                    client.getEstimateTime()
                    ));
            clientsTimes.addAll(client.getOtherClientsTimes());
        }));
        updatingTimes.setCycleCount(Timeline.INDEFINITE);

        VBox all = new VBox(serverBox, buttonsBox, clientsTable);

        return new Scene(all);
    }

    private void connect() {
        client = new Client(serverIp.getText());

        try {
            client.connect();
        } catch (IOException e) {
            e.printStackTrace();
        }

        updatingTimes.play();
    }
}
