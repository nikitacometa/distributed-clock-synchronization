package ru.spbau.gorokhov.ats.server;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.spbau.gorokhov.ats.client.utils.Clock;
import ru.spbau.gorokhov.ats.client.utils.RandomUtils;
import ru.spbau.gorokhov.ats.model.ClientAddress;
import ru.spbau.gorokhov.ats.model.Request;
import ru.spbau.gorokhov.ats.model.TimeInfo;
import ru.spbau.gorokhov.ats.utils.Sleepyhead;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.stream.Collectors;


public class Server {
    private static final Logger LOG = LoggerFactory.getLogger(Server.class);

    private static final int DEFAULT_PORT = 8080;

    private static final int SHOW_TIME_DELAY = 1000;

    private final int port;

    private final List<ClientAddress> clients = new ArrayList<>();

    private final Map<ClientAddress, List<ClientAddress>> clientNeighbours = new TreeMap<>();

    private final Map<ClientAddress, TimeInfo> clientTimes = new TreeMap<>();

    private boolean running = false;

    private long startTime = Clock.getRealTime();

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

        while (running) {
            Sleepyhead.sleep(SHOW_TIME_DELAY);

            showTime();
        }
    }

    private void showTime() {
        System.out.println("Working for " + (Clock.getRealTime() - startTime) + "ms. Clients:");
        synchronized (clientTimes) {
            clientTimes.forEach((address, timeInfo) -> System.out.println(address + ": " + timeInfo));
        }
    }

    private int getNumberOfOneClientNeighbours() {
        return clients.size() / 2;
    }

    private List<ClientAddress> getNeighbours(ClientAddress clientAddress) {
        return clientNeighbours.get(clientAddress);
    }

    private synchronized void updateNeighbours(ClientAddress clientAddress) {
        List<ClientAddress> neighbours = clients.stream()
                .filter(client -> RandomUtils.nextBoolean())
                .collect(Collectors.toList());
        clientNeighbours.put(clientAddress, neighbours);

        clients.stream()
                .filter(c -> RandomUtils.nextBoolean())
                .forEach(client -> clientNeighbours.get(client).add(clientAddress));

        clients.add(clientAddress);
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

//                LOG.info("{} request from {}", Request.toString(requestId), clientAddress);

                switch (requestId) {
                    case Request.REGISTER:
                        updateNeighbours(clientAddress);

//                        LOG.info("Client {} was registered.", clientAddress);
                        break;

                    case Request.SEND_TIME:
                        double clientSkew = clientOutput.readDouble();
                        double clientOffset = clientOutput.readDouble();
                        TimeInfo timeInfo = new TimeInfo(clientSkew, clientOffset);

//                        LOG.info("Got time info from {}: {}", clientAddress, timeInfo);

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

//                            LOG.info("Neighbours were sent to {}: {}", clientAddress, neighbours);
                        } catch (IOException e) {
                            LOG.error("Failed to send neighbours.", e);
                        }
                        break;

                    case Request.UPDATE_CLIENTS_TIMES:
                        synchronized (clientTimes) {
                            clientInput.writeInt(clientTimes.size() - 1);

                            for (Map.Entry<ClientAddress, TimeInfo> entry : clientTimes.entrySet()) {
                                ClientAddress address = entry.getKey();
                                TimeInfo info = entry.getValue();
                                if (!address.equals(clientAddress)) {
                                    clientInput.writeUTF(address.getIp());
                                    clientInput.writeInt(address.getPort());
                                    clientInput.writeDouble(info.getSkew());
                                    clientInput.writeDouble(info.getOffset());
                                }
                            }
                        }
                        break;

                    default:
                        LOG.error("Unknown request: {}.", requestId);

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
