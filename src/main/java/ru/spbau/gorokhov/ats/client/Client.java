package ru.spbau.gorokhov.ats.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.spbau.gorokhov.ats.client.utils.Clock;
import ru.spbau.gorokhov.ats.model.ClientAddress;
import ru.spbau.gorokhov.ats.model.Request;
import ru.spbau.gorokhov.ats.model.SyncInfo;
import ru.spbau.gorokhov.ats.model.TimeInfo;
import ru.spbau.gorokhov.ats.utils.Serializer;
import ru.spbau.gorokhov.ats.utils.Sleepyhead;

import java.io.*;
import java.net.*;
import java.util.*;

public class Client {
    private static int id = 1;
    private Logger LOG = LoggerFactory.getLogger(String.format("Client-%d", id++));

    private static final int DEFAULT_SERVER_PORT = 8080;

    private final String serverHostname;
    private final int serverPort;
    private int localPort;

    private final Clock clock;

    private static final long UPDATE_TIME_DELAY = 1000;
    private static final long SEND_DATA_DELAY = 20000;
    private static final long UPDATE_NEIGHBOURS_DELAY = 2000;

    private static final double RELATIVE_SKEW_TUNE = 0.6;
    private static final double SKEW_TUNE = 0.6;
    private static final double OFFSET_ERROR_TUNE = 0.6;

    private final Map<ClientAddress, Double> relativeSkew = new TreeMap<>();
    private final Map<ClientAddress, Long> lastClientTime = new TreeMap<>();
    private final Map<ClientAddress, Long> lastLocalTime = new TreeMap<>();

    private double skew = 1;
    private double offsetError = 0;
    private double debugOffset = 0;

    private long startWork;

    private volatile Map<ClientAddress, TimeInfo> clientTimes = new TreeMap<>();

    private final List<ClientAddress> neighbours = new ArrayList<>();

    private boolean running = false;

    public Client(String serverHostname, int serverPort) {
        this.serverHostname = serverHostname;
        this.serverPort = serverPort;

        clock = new Clock();
    }

    public Client(String serverHostname) {
        this(serverHostname, DEFAULT_SERVER_PORT);
    }

    public void connect() throws IOException {
        running = true;

        new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket()) {
                localPort = socket.getLocalPort();

                LOG.info("Client started. Listening to {} port.", localPort);

                try {
                    register();

                    LOG.info("Connected to the server.");
                } catch (IOException e) {
                    LOG.error("Failed to connect to the server.", e);
                }

                startWork = Clock.getRealTime();

                sendTime();

                updateNeighbours();

                byte[] data = new byte[128];

                while (running) {
                    DatagramPacket packet = new DatagramPacket(data, data.length);

                    try {
                        socket.receive(packet);
                    } catch (IOException e) {
                        LOG.error("Failed to receive packet.", e);
                        continue;
                    }

                    long localTime = clock.getTime();

                    SyncInfo syncInfo;

                    try {
                        syncInfo = Serializer.deserialize(packet.getData(), SyncInfo.class);

                        debugOffset = debugOffset + (1 - OFFSET_ERROR_TUNE) * (syncInfo.getTime() + syncInfo.getOffset() - localTime - debugOffset);
                    } catch (IOException | ClassNotFoundException e) {
                        LOG.error("Failed to deserialize sync info.", e);
                        continue;
                    }

                    ClientAddress clientAddress = new ClientAddress(packet.getAddress().getHostAddress(), syncInfo.getPort());

//                    LOG.info("Got sync info from {}: {}", clientAddress, syncInfo);

                    process(clientAddress, syncInfo.getTime(), syncInfo.getSkew(), syncInfo.getOffset(), localTime);
                }
            } catch (SocketException e) {
                LOG.error("Failed to start client.", e);
            }
        }).start();

        new Thread(() -> {
            while (running) {
                Sleepyhead.sleep((long) (SEND_DATA_DELAY / clock.getSkew()));

                sendDataToAll();
            }
        }).start();

        new Thread(() -> {
            while (running) {
                Sleepyhead.sleep(UPDATE_TIME_DELAY);

                sendTime();

                updateOtherClientsTimes();
            }
        }).start();

        new Thread(() -> {
            while (running) {
                Sleepyhead.sleep(UPDATE_NEIGHBOURS_DELAY);

                updateNeighbours();
            }
        }).start();
    }

    private synchronized void process(ClientAddress clientAddress, long clientTime, double clientSkew, double clientOffsetError, long localTime) {
        if (!relativeSkew.containsKey(clientAddress)) {
            relativeSkew.put(clientAddress, 1D);
        }

//        if (Clock.getRealTime() - startWork < 15000) {
            double currentRelativeSkew = relativeSkew.get(clientAddress);

            if (lastLocalTime.containsKey(clientAddress)) {
                long prevClientTime = lastClientTime.get(clientAddress);
                long prevLocalTime = lastLocalTime.get(clientAddress);

                currentRelativeSkew = RELATIVE_SKEW_TUNE * currentRelativeSkew + (1 - RELATIVE_SKEW_TUNE) * (clientTime - prevClientTime) / (localTime - prevLocalTime);

                relativeSkew.put(clientAddress, currentRelativeSkew);
            }

            skew = SKEW_TUNE * skew + (1 - SKEW_TUNE) * currentRelativeSkew * clientSkew;
//        }

        offsetError = offsetError + (1 - OFFSET_ERROR_TUNE) * (clientSkew * clientTime + clientOffsetError - skew * localTime - offsetError);

        lastClientTime.put(clientAddress, clientTime);
        lastLocalTime.put(clientAddress, localTime);
    }

    private void register() throws IOException {
        try (Socket socket = new Socket(serverHostname, serverPort);
             DataOutputStream serverInput = new DataOutputStream(socket.getOutputStream())) {

            serverInput.writeInt(localPort);

            serverInput.writeInt(Request.REGISTER);

        } catch (IOException rethrown) {
            throw rethrown;
        }
    }

    private void updateNeighbours() {
        try (Socket socket = new Socket(serverHostname, serverPort);
             DataOutputStream serverInput = new DataOutputStream(socket.getOutputStream());
             DataInputStream serverOutput = new DataInputStream(socket.getInputStream())) {

            serverInput.writeInt(localPort);

            serverInput.writeInt(Request.UPDATE_NEIGHBOURS);

            List<ClientAddress> newNeighbours = new ArrayList<>();

            int count = serverOutput.readInt();

            while (count --> 0) {
                String ip = serverOutput.readUTF();
                int port = serverOutput.readInt();
                newNeighbours.add(new ClientAddress(ip, port));
            }

            synchronized (neighbours) {
                for (ClientAddress neighbour : newNeighbours) {
                    if (!neighbours.contains(neighbour)) {
                        neighbours.add(neighbour);
                    }
                }
            }

//            LOG.info("Got new neighbours: {}", newNeighbours);
        } catch (IOException e) {
            LOG.error("Failed to update neighbours.", e);
        }
    }

    private void sendTime() {
//        LOG.info("Sending time info to server..."); offsetError = debugOffset;
        try (Socket socket = new Socket(serverHostname, serverPort);
             DataOutputStream serverInput = new DataOutputStream(socket.getOutputStream())) {

            serverInput.writeInt(localPort);

            serverInput.writeInt(Request.SEND_TIME);

            TimeInfo timeInfo = getEstimateTime();

            serverInput.writeDouble(timeInfo.getSkew());
            serverInput.writeDouble(timeInfo.getOffset());

//            LOG.info("Time info was sent: {}", timeInfo);
        } catch (IOException e) {
            LOG.error("Failed to send time info.", e);
        }
    }

    private void updateOtherClientsTimes() {
        try (Socket socket = new Socket(serverHostname, serverPort);
             DataOutputStream serverInput = new DataOutputStream(socket.getOutputStream());
             DataInputStream serverOutput = new DataInputStream(socket.getInputStream())) {

            serverInput.writeInt(localPort);

            serverInput.writeInt(Request.UPDATE_CLIENTS_TIMES);

            int count = serverOutput.readInt();

            Map<ClientAddress, TimeInfo> newClientsTimes = new TreeMap<>();

            while (count --> 0) {
                String ip = serverOutput.readUTF();
                int port = serverOutput.readInt();

                double clientSkew = serverOutput.readDouble();
                double clientOffset = serverOutput.readDouble();

                newClientsTimes.put(new ClientAddress(ip, port), new TimeInfo(clientSkew, clientOffset));
            }

            clientTimes = newClientsTimes;
        } catch (IOException e) {
            LOG.error("Failed to update clients times.", e);
        }
    }

    public Map<ClientAddress, TimeInfo> getOtherClientsTimes() {
        return clientTimes;
    }

    private void sendDataToAll() {
        for (ClientAddress neighbour : neighbours) {
            sendData(neighbour);
        }
    }

    private void sendData(ClientAddress neighbour) {
        try (DatagramSocket socket = new DatagramSocket()) {
            SyncInfo syncInfo;

//            LOG.info("Sending data to {}.", neighbour); offsetError = debugOffset;

            synchronized (this) {
                syncInfo = new SyncInfo(localPort, clock.getTime(), skew, offsetError);
            }

            byte[] data = Serializer.serialize(syncInfo);
            DatagramPacket packet = new DatagramPacket(data, data.length, InetAddress.getByName(neighbour.getIp()), neighbour.getPort());
            socket.send(packet);

//            LOG.info("Sync info was sent to {}: {}", neighbour, syncInfo);
        } catch (IOException e) {
            LOG.error("Failed to send sync info to {}.", neighbour, e);
        }

    }

    public void disconnect() {
        running = false;
    }

    public boolean isRunning() {
        return running;
    }

    public TimeInfo getEstimateTime() {
        return new TimeInfo(clock.getSkew() * skew, skew * clock.getOffset() + offsetError);
    }

    public TimeInfo getRealTime() {
        return new TimeInfo(clock.getSkew(), clock.getOffset());
    }
}
