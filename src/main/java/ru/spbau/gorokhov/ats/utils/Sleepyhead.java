package ru.spbau.gorokhov.ats.utils;

public class Sleepyhead {
    public static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }
}
