package ru.spbau.gorokhov.ats.model;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

@Getter
@EqualsAndHashCode
@RequiredArgsConstructor
public class ClientAddress implements Serializable, Comparable<ClientAddress> {
    private final String ip;
    private final Integer port;

    @Override
    public int compareTo(@NotNull ClientAddress that) {
        if (that.getIp().equals(this.getIp())) {
            return this.getPort() - that.getPort();
        }
        return this.getIp().compareTo(that.getIp());
    }

    @Override
    public String toString() {
        return String.format("%s:%d", ip, port);
    }
}
