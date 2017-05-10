package ru.spbau.gorokhov.ats.utils;

import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.io.Serializable;

@ToString
@RequiredArgsConstructor
public class TimeInfo implements Serializable {
    private final double skew;
    private final double offset;

    public long getTime() {
        return (long) (System.currentTimeMillis() * skew + offset);
    }
}
