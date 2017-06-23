package ru.spbau.gorokhov.ats;

import ru.spbau.gorokhov.ats.client.Client;

import java.io.IOException;
import java.net.InetAddress;

public class MultipleClientsRunner {
    public static void main(String[] args) throws IOException {
        String serverHostname = InetAddress.getLocalHost().getHostName();

        int numberOfClients = 25;

        while (numberOfClients --> 0) {
            new Thread(() -> {
                try {
                    new Client(serverHostname).connect();
                } catch (IOException ignored) {}
            }).start();
        }
    }
}
