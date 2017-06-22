package ru.spbau.gorokhov.ats.client.utils;

import java.util.Random;

public class RandomUtils {
    private static final Random RANDOM = new Random(System.currentTimeMillis());


    public static long nextLong(long limit) {
        return ((RANDOM.nextLong() % limit) + limit) % limit;
    }

    public static long nextLong(long from, long to) {
        return from + nextLong(to - from + 1);
    }

    public static boolean nextBoolean() {
        return RANDOM.nextBoolean();
    }

    public static boolean trueWithProbability(double probability) {
        return RANDOM.nextDouble() < probability;
    }
}
