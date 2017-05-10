package ru.spbau.gorokhov.ats.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.io.Serializable;

@Getter
@ToString
@RequiredArgsConstructor
public class TimeInfo implements Serializable {
    private final double skew;
    private final double offset;

    public long getTime() {
        return (long) (System.currentTimeMillis() * skew + offset);
    }
}
