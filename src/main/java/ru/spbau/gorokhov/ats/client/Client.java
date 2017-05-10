package ru.spbau.gorokhov.ats.client;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.spbau.gorokhov.ats.client.utils.Clock;
import ru.spbau.gorokhov.ats.utils.ClientAddress;
import ru.spbau.gorokhov.ats.utils.ClientRequest;
import ru.spbau.gorokhov.ats.utils.ServerRequest;
import ru.spbau.gorokhov.ats.utils.TimeInfo;

import java.io.*;
import java.net.*;
import java.util.*;

public class Client {
    private static final Logger LOG = LoggerFactory.getLogger(Client.class);

    private static final String DEFAULT_SERVER_HOSTNAME = "localhost";
    private static final int DEFAULT_SERVER_PORT = 8080;
    private static final int DEFAULT_LOCAL_PORT = 62222;

    private final String serverHostname;
    private final int serverPort;
    private final int localPort;

    private final InetAddress localAddress;
    private final InetAddress serverAddress;

    private final Clock clock;

    private static final int SEND_DELAY = 1000;

    private static final double RELATIVE_SKEW_TUNE = 0.75;
    private static final double SKEW_TUNE = 0.75;
    private static final double OFFSET_ERROR_TUNE = 0.75;

    private final Map<ClientAddress, Double> relativeSkew = new TreeMap<>();
    private final Map<ClientAddress, Long> lastClientTime = new TreeMap<>();
    private final Map<ClientAddress, Long> lastLocalTime = new TreeMap<>();

    // relative to virtual clock skew estimate
    private double skew = 1;
    private double offsetError = 0;

    private final List<ClientAddress> neighbours = new ArrayList<>();

    private boolean running = false;

    public Client(String serverHostname, int serverPort, int localPort) throws UnknownHostException {
        this.serverHostname = serverHostname;
        this.serverPort = serverPort;
        this.localPort = localPort;

        this.localAddress = Inet4Address.getLocalHost();
        this.serverAddress = InetAddress.getByName(serverHostname);

        clock = new Clock();
    }

    public Client(String serverHostname, int localPort) throws UnknownHostException {
        this(serverHostname, DEFAULT_SERVER_PORT, localPort);
    }

    public Client(int localPort) throws UnknownHostException {
        this(DEFAULT_SERVER_HOSTNAME, localPort);
    }

    public Client(String serverHostname) throws UnknownHostException {
        this(serverHostname, DEFAULT_LOCAL_PORT);
    }

    public Client() throws UnknownHostException {
        this(DEFAULT_LOCAL_PORT);
    }

    public void connect() throws IOException {
        running = true;

        register();

        LOG.info("Connected to server.");

        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(localPort)) {
                while (running) {
                    Socket newConnection = serverSocket.accept();
                    new Thread(new RequestHandler(newConnection)).start();
                }
            } catch (IOException e) {
                // TODO
                LOG.error("smth went wrong");
            }
        }).start();

        new Thread(() -> {
            while (running) {
                new Thread(Client.this::sendData).start();

                try {
                    Thread.sleep(SEND_DELAY);
                } catch (InterruptedException e) {
                    // TODO
                    LOG.error("wtf");
                }
            }
        }).start();
    }

    private void register() {
        try (Socket serverSocket = new Socket(serverAddress, serverPort, localAddress, localPort);
             DataOutputStream serverInput = new DataOutputStream(serverSocket.getOutputStream())) {

            serverInput.writeInt(ServerRequest.REGISTER);
        } catch (UnknownHostException e) {
            // TODO
            LOG.error("hz345");
        } catch (IOException e) {
            // TODO
            LOG.error("hz1235");
            e.printStackTrace();
        }
    }

    private int ptr = -1;

    private void sendData() {
        if (neighbours.size() == 0) {
            return;
        }

        Random random = new Random(System.currentTimeMillis());

        if (ptr == -1) {
            ptr = random.nextInt(neighbours.size());
        }

        synchronized (this) {
//            ClientAddress neighbour = neighbours.get(random.nextInt(neighbours.size()));
            ClientAddress neighbour = neighbours.get(ptr);

            ptr = (ptr + 1) % neighbours.size();

            try (Socket socket = new Socket(neighbour.getLocalIp(), neighbour.getPort());
                DataOutputStream outputStream = new DataOutputStream(socket.getOutputStream())) {

                outputStream.writeInt(ClientRequest.SEND_DATA);

                outputStream.writeInt(localPort);

                outputStream.writeLong(clock.getTime());
                outputStream.writeDouble(skew);
                outputStream.writeDouble(offsetError);

                outputStream.flush();

                socket.setSoLinger(true, 10);

                // not to allow socket closing before client reads all the infotmation
                // (otherwise it happens all the time causing terrible mistake and not system working)
                // maybe this is only on my machine
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            } catch (UnknownHostException e) {
                // TODO
                LOG.error("hz534534");
            } catch (IOException e) {
                // TODO
                LOG.error("hz15435");
                e.printStackTrace();
            }
        }
    }

    public void disconnect() {
        running = false;
    }

    public long getTime() {
        return (long) (skew * clock.getTime() + offsetError);
    }

    public static void main(String[] args) throws IOException {
        new Client().connect();
    }

    @RequiredArgsConstructor
    private class RequestHandler implements Runnable {
        private final Socket clientSocket;

        @Override
        public void run() {
            String clientIp = clientSocket.getInetAddress().getHostAddress();

            try (DataInputStream clientOutput = new DataInputStream(clientSocket.getInputStream());
                 ObjectOutputStream clientInput = new ObjectOutputStream(clientSocket.getOutputStream())) {

                int requestId = clientOutput.readInt();

                switch (requestId) {
                    case ClientRequest.SEND_DATA:
                        int clientPort = clientOutput.readInt();

                        ClientAddress clientAddress = new ClientAddress(clientIp, clientPort);

                        long clientTime = clientOutput.readLong();
                        long localTime = clock.getTime();

                        double clientSkew = clientOutput.readDouble();
                        double clientOffsetError = clientOutput.readDouble();

                        LOG.info("{} got from {}: time={}, skew={}, offset={}", localPort, clientPort, clientTime, clientSkew, clientOffsetError);

                        synchronized (Client.this) {
                            long prevClientTime = lastClientTime.get(clientAddress);
                            long prevLocalTime = lastLocalTime.get(clientAddress);
                            double currentRelativeSkew = relativeSkew.get(clientAddress);

                            double newRelativeSkew = RELATIVE_SKEW_TUNE * currentRelativeSkew + (1 - RELATIVE_SKEW_TUNE) * (clientTime - prevClientTime) / (localTime - prevLocalTime);

                            skew = SKEW_TUNE * skew + (1 - SKEW_TUNE) * newRelativeSkew * clientSkew;

                            offsetError = OFFSET_ERROR_TUNE * offsetError + (1 - OFFSET_ERROR_TUNE) * (clientSkew * clientTime + clientOffsetError - skew * localTime - offsetError);

                            relativeSkew.put(clientAddress, newRelativeSkew);
                            lastClientTime.put(clientAddress, clientTime);
                            lastLocalTime.put(clientAddress, localTime);
                        }

                        break;


                    case ServerRequest.GET_TIME:
                        TimeInfo timeInfo;
                        synchronized (Client.this) {
                            timeInfo = new TimeInfo(clock.getSkew() * skew, skew * clock.getOffset() + offsetError);
                        }
                        clientInput.writeObject(timeInfo);

                        break;


                    case ServerRequest.UPDATE_NEIGHBOURS:
                        List<ClientAddress> newNeighbours = new ArrayList<>();

                        int count = clientOutput.readInt();

                        while (count --> 0) {
                            String ip = clientOutput.readUTF();
                            int port = clientOutput.readInt();
                            newNeighbours.add(new ClientAddress(ip, port));
                        }

                        LOG.info("Got new neighbours: {}", newNeighbours);

                        synchronized (Client.this) {
                            for (ClientAddress neighbourAddress : newNeighbours) {
                                if (!neighbours.contains(neighbourAddress)) {
                                    neighbours.add(neighbourAddress);
                                    relativeSkew.put(neighbourAddress, 1D);
                                    lastLocalTime.put(neighbourAddress, 0L);
                                    lastClientTime.put(neighbourAddress, 0L);
                                }
                            }

                            // TODO handle removed neighbours maybe
                        }

                        break;


                    default:
                        LOG.info("Invalid request id: {}", requestId);
                }
            } catch (IOException e) {
                // TODO
                LOG.error("bad");
                e.printStackTrace();
            }
        }
    }
}
