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
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import ru.spbau.gorokhov.ats.client.Client;
import ru.spbau.gorokhov.ats.model.ClientAddress;
import ru.spbau.gorokhov.ats.model.TimeInfo;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class ClientApp extends Application {

    private final TextField serverIp = new TextField();

    private final Button connectButton = new Button("Connect");
    private final Button disconnectButton = new Button("Disconnect");

    private final Label realTime = new Label();
    private final Label estimateTime = new Label();

    private Timeline updatingTimes;

    private final ObservableList<ClientInfo> clientsTimes = FXCollections.observableArrayList();

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

        HBox realTimeBox = new HBox(new Label("Real time: "), realTime);
        HBox estimateTimeBox = new HBox(new Label("Estimate time: "), estimateTime);

        TableView<ClientInfo> clientsTable = new TableView<>();

        TableColumn<ClientInfo, String> clientAddressColumn = new TableColumn<>("Client");
        clientAddressColumn.setCellValueFactory(new PropertyValueFactory<>("clientAddress"));
        clientAddressColumn.setPrefWidth(100);


        TableColumn<ClientInfo, String> clientTimeColumn = new TableColumn<>("Time");
        clientTimeColumn.setCellValueFactory(new PropertyValueFactory<>("estimateTime"));
        clientTimeColumn.setPrefWidth(100);

        clientsTable.getColumns().addAll(clientAddressColumn, clientTimeColumn);

        clientsTable.setItems(clientsTimes);

        updatingTimes = new Timeline(new KeyFrame(Duration.seconds(1), (e) -> {
            realTime.setText(client.getRealTime().toString());
            estimateTime.setText(client.getEstimateTime().toString());
            List<ClientInfo> newClientTimes = client.getOtherClientsTimes().entrySet().stream()
                    .map(entry -> new ClientInfo(entry.getKey(), entry.getValue()))
                    .collect(Collectors.toList());
            clientsTimes.clear();
            clientsTimes.addAll(newClientTimes);
        }));
        updatingTimes.setCycleCount(Timeline.INDEFINITE);

        VBox all = new VBox(serverBox, buttonsBox, realTimeBox, estimateTimeBox, clientsTable);

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

    @RequiredArgsConstructor
    @Getter
    public class ClientInfo {
        public final ClientAddress clientAddress;
        public final TimeInfo estimateTime;
    }
}
