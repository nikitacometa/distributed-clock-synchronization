package ru.spbau.gorokhov.ats.server;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.spbau.gorokhov.ats.model.ClientAddress;
import ru.spbau.gorokhov.ats.model.Request;
import ru.spbau.gorokhov.ats.model.TimeInfo;

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

    private final Map<ClientAddress, TimeInfo> clientEstimateTimes = new TreeMap<>();
    private final Map<ClientAddress, TimeInfo> clientRealTimes = new TreeMap<>();

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
    }

    private static final int MAX_NEIGHBOURS = 4;

    private int getNumberOfOneClientNeighbours() {
        return Math.min(clients.size(), MAX_NEIGHBOURS);
    }

    private List<ClientAddress> getNeighbours(ClientAddress clientAddress) {
        return clientNeighbours.get(clientAddress);
    }

    private synchronized void updateNeighbours(ClientAddress newClientAddress) {
        List<ClientAddress> neighbours = clients.stream()
                .sorted(Comparator.comparingInt(client -> clientNeighbours.get(client).size()))
                .limit(getNumberOfOneClientNeighbours())
                .collect(Collectors.toList());
        neighbours.forEach(neighbour -> clientNeighbours.get(neighbour).add(newClientAddress));
        clientNeighbours.put(newClientAddress, neighbours);
        clients.add(newClientAddress);
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

                LOG.info("{} request from {}", requestId, clientAddress);

                switch (requestId) {
                    case Request.REGISTER:
                        updateNeighbours(clientAddress);

                        LOG.info("Client {} was registered.", clientAddress);
                        break;

                    case Request.SEND_TIME:
                        double clientRealSkew = clientOutput.readDouble();
                        double clientRealOffset = clientOutput.readDouble();
                        TimeInfo realTime = new TimeInfo(clientRealSkew, clientRealOffset);

                        double clientEstimateSkew = clientOutput.readDouble();
                        double clientEstimateOffset = clientOutput.readDouble();
                        TimeInfo estimateTime = new TimeInfo(clientEstimateSkew, clientEstimateOffset);

                        LOG.info("Got time info from {}: {}, {}", clientAddress, realTime, estimateTime);

                        synchronized (clientRealTimes) {
                            clientRealTimes.put(clientAddress, realTime);
                        }
                        synchronized (clientEstimateTimes) {
                            clientEstimateTimes.put(clientAddress, estimateTime);
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

                    case Request.UPDATE_CLIENTS_TIMES:
                        synchronized (this) {
                            clientInput.writeInt(clients.size() - 1);

                            for (ClientAddress client : clients) {
                                if (!client.equals(clientAddress)) {
                                    TimeInfo realTimeInfo = clientRealTimes.get(client);
                                    TimeInfo estimateTimeInfo = clientEstimateTimes.get(client);
                                    clientInput.writeUTF(client.getIp());
                                    clientInput.writeInt(client.getPort());
                                    clientInput.writeDouble(realTimeInfo.getSkew());
                                    clientInput.writeDouble(realTimeInfo.getOffset());
                                    clientInput.writeDouble(estimateTimeInfo.getSkew());
                                    clientInput.writeDouble(estimateTimeInfo.getOffset());
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
