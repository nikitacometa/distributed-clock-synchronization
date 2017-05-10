package ru.spbau.gorokhov.ats.utils;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

@Getter
@ToString
@EqualsAndHashCode
@RequiredArgsConstructor
public class ClientAddress implements Serializable, Comparable<ClientAddress> {
    private final String localIp;
    private final int port;

    @Override
    public int compareTo(@NotNull ClientAddress that) {
        if (that.getLocalIp().equals(this.getLocalIp())) {
            return this.getPort() - that.getPort();
        }
        return this.getLocalIp().compareTo(that.getLocalIp());
    }
}
