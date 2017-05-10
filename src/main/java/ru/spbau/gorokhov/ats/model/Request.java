package ru.spbau.gorokhov.ats.model;

public class Request {
    public static final int REGISTER = 11111;
    public static final int UPDATE_NEIGHBOURS = 22222;
    public static final int SEND_TIME = 33333;
    public static final int SEND_DATA = 44444;

    public static String toString(int requestId) {
        switch (requestId) {
            case REGISTER:
                return "REGISTER";

            case UPDATE_NEIGHBOURS:
                return "UPDATE_NEIGHBOURS";

            case SEND_TIME:
                return "SEND_TIME";

            case SEND_DATA:
                return "SEND_DATA";

            default:
                return String.format("INVALID(%d)", requestId);
        }
    }
}
