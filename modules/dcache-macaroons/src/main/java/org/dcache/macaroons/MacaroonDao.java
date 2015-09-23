package org.dcache.macaroons;

import java.util.Collection;

import org.dcache.macaroons.model.Macaroon;

public interface MacaroonDao
{
    Macaroon storeMacaroon(Macaroon macaroon);

    Macaroon getMacaroon(String id);
}
