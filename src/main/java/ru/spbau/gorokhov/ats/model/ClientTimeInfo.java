package ru.spbau.gorokhov.ats.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class ClientTimeInfo {
    public final ClientAddress clientAddress;
    public final TimeInfo realTime;
    public final TimeInfo estimateTime;
}