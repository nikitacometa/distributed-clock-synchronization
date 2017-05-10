package ru.spbau.gorokhov.ats.server;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.spbau.gorokhov.ats.model.ClientAddress;
import ru.spbau.gorokhov.ats.model.Request;
import ru.spbau.gorokhov.ats.model.TimeInfo;
import ru.spbau.gorokhov.ats.utils.Sleepyhead;

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;


public class Server {
    private static final Logger LOG = LoggerFactory.getLogger(Server.class);

    private static final int DEFAULT_PORT = 8080;

    private static final int SHOW_TIME_DELAY = 2000;

    private final int port;

    private final List<ClientAddress> clients = new ArrayList<>();

    private final Map<ClientAddress, TimeInfo> clientTimes = new TreeMap<>();

    private boolean running = false;

    public Server(int port) {
        this.port = port;
    }

    public Server() {
        this(DEFAULT_PORT);
    }

    public void start() {
        running = true;

        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(port)) {
                LOG.info("Server running...");
                LOG.info("Server IP: {}", Inet4Address.getLocalHost().getHostAddress());

                while (running) {
                    Socket newConnection = serverSocket.accept();

                    new Thread(new RequestHandler(newConnection)).start();
                }
            } catch (IOException e) {
                LOG.error("Server crushed.", e);
            }
        }).start();

        new Thread(() -> {
            while (running) {
                Sleepyhead.sleep(SHOW_TIME_DELAY);

                showTime();
            }
        }).start();
    }

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");

    private void showTime() {
        System.out.println("Clients:");
        clientTimes.forEach((address, timeInfo) -> System.out.println(address + ": " + DATE_FORMAT.format(new Date(timeInfo.getTime()))));
    }

    private List<ClientAddress> getNeighbours(ClientAddress clientAddress) {
        // TODO something smarter
        synchronized (clients) {
            return clients.stream().filter(s -> !s.equals(clientAddress)).collect(Collectors.toList());
        }
    }

    @RequiredArgsConstructor
    private class RequestHandler implements Runnable {
        private final Socket clientSocket;

        @Override
        public void run() {
            String clientIp = clientSocket.getInetAddress().getHostAddress();

            try (DataInputStream clientOutput = new DataInputStream(clientSocket.getInputStream());
                 DataOutputStream clientInput = new DataOutputStream(clientSocket.getOutputStream())) {

                int clientPort = clientOutput.readInt();
                ClientAddress clientAddress = new ClientAddress(clientIp, clientPort);

                int requestId = clientOutput.readInt();

                LOG.info("{} request from {}", Request.toString(requestId), clientAddress);

                switch (requestId) {
                    case Request.REGISTER:
                        synchronized (clients) {
                            clients.add(clientAddress);
                        }
                        LOG.info("Client {} was registered.", clientAddress);
                        break;

                    case Request.SEND_TIME:
                        double clientSkew = clientOutput.readDouble();
                        double clientOffset = clientOutput.readDouble();
                        TimeInfo timeInfo = new TimeInfo(clientSkew, clientOffset);

                        LOG.info("Got time info from {}: {}", clientAddress, timeInfo);

                        synchronized (clientTimes) {
                            clientTimes.put(clientAddress, timeInfo);
                        }
                        break;

                    case Request.UPDATE_NEIGHBOURS:
                        List<ClientAddress> neighbours = getNeighbours(clientAddress);
                        try {
                            clientInput.writeInt(neighbours.size());
                            for (ClientAddress neighbour : neighbours) {
                                clientInput.writeUTF(neighbour.getIp());
                                clientInput.writeInt(neighbour.getPort());
                            }

                            LOG.info("Neighbours were sent to {}: {}", clientAddress, neighbours);
                        } catch (IOException e) {
                            LOG.error("Failed to send neighbours.", e);
                        }
                        break;

                }
            } catch (IOException e) {
                LOG.error("Failed to handle new connection.", e);
            }
        }
    }

    public void stop() {
        running = false;

        LOG.info("Stopping server...");
    }

    public static void main(String[] args) {
        new Server().start();
    }
}
