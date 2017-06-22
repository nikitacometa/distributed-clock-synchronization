package ru.spbau.gorokhov.ats.client.utils;

import lombok.Getter;

@Getter
public class Clock {
    private static final long MAX_SKEW_DEVIATION = (long) 1000 * 60 * 60 * 24; // one year
    private static final long MAX_OFFSET_DEVIATION = (long) 1000 * 60 * 60 * 24;

    private final double skew;
    private final double offset;

    public Clock() {
        long realTime = getRealTime();

        long skewDeviation = RandomUtils.nextLong(-MAX_SKEW_DEVIATION, MAX_SKEW_DEVIATION);
        skew = (realTime + skewDeviation) * 1D / realTime;

        offset = RandomUtils.nextLong(-MAX_OFFSET_DEVIATION, MAX_OFFSET_DEVIATION);
    }

    public long getTime() {
        return (long) (skew * getRealTime() + offset);
    }

    public static long getRealTime() {
        return System.currentTimeMillis();
    }
}
