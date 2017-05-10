package ru.spbau.gorokhov.ats.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import java.io.Serializable;

@ToString
@Getter
@RequiredArgsConstructor
public class SyncInfo implements Serializable {
    private final int port;
    private final long time;
    private final double skew;
    private final double offset;
}
