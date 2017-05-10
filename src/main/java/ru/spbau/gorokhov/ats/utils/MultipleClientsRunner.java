package ru.spbau.gorokhov.ats.utils;

import ru.spbau.gorokhov.ats.client.Client;

import java.io.IOException;
import java.net.InetAddress;

public class MultipleClientsRunner {
    public static void main(String[] args) throws IOException {
        String serverHostname = InetAddress.getLocalHost().getHostName();

        new Client(serverHostname, 64449).connect();
        new Client(serverHostname, 64448).connect();
        new Client(serverHostname, 64447).connect();
        new Client(serverHostname, 64446).connect();
        new Client(serverHostname, 64445).connect();
        new Client(serverHostname, 64444).connect();
        new Client(serverHostname, 64443).connect();
        new Client(serverHostname, 64442).connect();
        new Client(serverHostname, 64441).connect();
        new Client(serverHostname, 64440).connect();
    }
}
