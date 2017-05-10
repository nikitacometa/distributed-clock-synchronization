package ru.spbau.gorokhov.ats.server;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.spbau.gorokhov.ats.utils.ClientAddress;
import ru.spbau.gorokhov.ats.utils.ServerRequest;
import ru.spbau.gorokhov.ats.utils.TimeInfo;

import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;


public class Server {
    private static final Logger LOG = LoggerFactory.getLogger(Server.class);

    private static String DEFAULT_HOSTNAME = "localhost";
    private static final int DEFAULT_PORT = 8080;

    private static final int SHOW_TIME_DELAY = 3000;
    private static final int UPDATE_NEIGHBOURS_DELAY = 6000;

    private final String hostname;
    private final int port;

    private final List<ClientAddress> clients = Collections.synchronizedList(new ArrayList<>());

    private final Map<ClientAddress, TimeInfo> clientTimes = new TreeMap<>();

    private boolean running = false;

    public Server(String hostname, int port) {
        this.hostname = hostname;
        this.port = port;
    }

    public Server() {
        this(DEFAULT_HOSTNAME, DEFAULT_PORT);
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
                // TODO
                LOG.error("blabla");
            }
        }).start();

        new Thread(() -> {
            while (running) {
                try {
                    Thread.sleep(SHOW_TIME_DELAY);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                new Thread(() -> {
                    askTime();
                    showTime();
                }).start();
            }
        }).start();
    }

    private void askTime() {
        for (ClientAddress clientAddress : new ArrayList<>(clients)) {
            try (Socket clientSocket = new Socket(clientAddress.getLocalIp(), clientAddress.getPort());
                 DataOutputStream clientInput = new DataOutputStream(clientSocket.getOutputStream());
                 ObjectInputStream clientOutput = new ObjectInputStream(clientSocket.getInputStream())) {

                clientInput.writeInt(ServerRequest.GET_TIME);

                TimeInfo clientTime = (TimeInfo) clientOutput.readObject();

                synchronized (clientTimes) {
                    clientTimes.put(clientAddress, clientTime);
                }
            } catch (ConnectException e) {
                LOG.info("Client disconnected: {}", clientAddress);

                removeClient(clientAddress);
            } catch (UnknownHostException e) {
                LOG.info("heheh");
                e.printStackTrace();
            } catch (IOException e) {
                LOG.info("hohoh");
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                LOG.info("hahah");
                e.printStackTrace();
            }
        }
    }

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");

    private void showTime() {
        System.out.println("Clients:");
        clientTimes.forEach((address, timeInfo) ->
                System.out.println(address + ": " + DATE_FORMAT.format(new Date(timeInfo.getTime()))));
    }

    private void sendNeighbours(ClientAddress clientAddress) {
        try (Socket clientSocket = new Socket(clientAddress.getLocalIp(), clientAddress.getPort());
            DataOutputStream clientInput = new DataOutputStream(clientSocket.getOutputStream())) {

            List<ClientAddress> neighbours = getHeighbours(clientAddress);

            clientInput.writeInt(ServerRequest.UPDATE_NEIGHBOURS);

            clientInput.writeInt(neighbours.size());

            for (ClientAddress neighbourAddress : neighbours) {
                clientInput.writeUTF(neighbourAddress.getLocalIp());
                clientInput.writeInt(neighbourAddress.getPort());
            }

            clientInput.flush();

            clientSocket.setSoLinger(true, 10);

            // not to allow socket closing before client reads all the infotmation
            // (otherwise it happens all the time causing terrible mistake and not system working)
            // maybe this is only on my machine
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            LOG.info("Neighbours were sent to client {}: {}", clientAddress, neighbours);
        } catch (UnknownHostException e) {
            LOG.info("pdums");
            e.printStackTrace();
        } catch (IOException e) {
            LOG.info("Client disconnected.");
//            removeClient(clientAddress);
        }
    }

    private void removeClient(ClientAddress clientAddress) {
        synchronized (this) {
            clients.remove(clientAddress);
            clientTimes.remove(clientAddress);
        }
    }

    private List<ClientAddress> getHeighbours(ClientAddress clientAddress) {
        // TODO something clever
        return clients.stream().filter(s -> !s.equals(clientAddress)).collect(Collectors.toList());
    }

    public void stop() {
        running = false;

        LOG.info("Stopping server...");
    }

    public static void main(String[] args) {
        new Server().start();
    }

    @RequiredArgsConstructor
    private class RequestHandler implements Runnable {
        private final Socket clientSocket;

        @Override
        public void run() {
            ClientAddress clientAddress = new ClientAddress(clientSocket.getInetAddress().getHostAddress(), clientSocket.getPort());

            LOG.info("New connection: {}", clientAddress);

            try (DataInputStream clientOutput = new DataInputStream(clientSocket.getInputStream())) {
                int requestId = clientOutput.readInt();

                LOG.info("Request id: {}", requestId);

                switch (requestId) {
                    case ServerRequest.REGISTER:
                        synchronized (clients) {
                            clients.add(clientAddress);
                        }

                        synchronized (clients) {
                            for (ClientAddress client : clients) {
                                new Thread(() -> {
                                    sendNeighbours(client);
                                }).start();
                            }
                        }

                        break;

                    default:
                        LOG.info("Invalid request id: {}", requestId);
                }
            } catch (IOException e) {
                LOG.error("Failed to handle new connection.");
            }
        }
    }
}
