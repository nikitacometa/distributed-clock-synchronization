package ru.spbau.gorokhov.ats.client.utils;

import lombok.Getter;

@Getter
public class Clock {
    private static final long MAX_SKEW_DEVIATION = 1000 * 60 * 60 * 24; // one day
    private static final long MAX_OFFSET_DEVIATION = 1000 * 60 * 60 * 24;

    private final double skew;
    private final double offset;

    public Clock() {
        long realTime = getRealTime();

        long alphaDeviation = RandomUtils.nextLong(-MAX_SKEW_DEVIATION, MAX_SKEW_DEVIATION);
        skew = (realTime + alphaDeviation) * 1D / realTime;

        offset = RandomUtils.nextLong(-MAX_OFFSET_DEVIATION, MAX_OFFSET_DEVIATION);
    }

    public long getTime() {
        return (long) (skew * getRealTime() + offset);
    }

    public static long getRealTime() {
        return System.currentTimeMillis();
    }
}
