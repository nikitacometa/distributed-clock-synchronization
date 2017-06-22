package ru.spbau.gorokhov.ats.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import ru.spbau.gorokhov.ats.client.utils.Clock;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;

@Getter
@RequiredArgsConstructor
public class TimeInfo implements Serializable {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss:SSS");

    private final double skew;
    private final double offset;

    public long getTime() {
        return (long) (Clock.getRealTime() * skew + offset);
    }

    @Override
    public String toString() {
        return DATE_FORMAT.format(new Date(getTime()));
    }
}
