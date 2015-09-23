package org.dcache.macaroons;

import com.google.common.collect.ImmutableList;

import diskCacheV111.util.PnfsId;
import diskCacheV111.vehicles.Message;

/**
 * Request a new Macaroon.
 */
public class MacaroonRequestMessage extends Message
{
    private final String path;
    private final ImmutableList<String> caveats;
    private final PnfsId id;

    private String macaroon;

    public MacaroonRequestMessage(String path, PnfsId id)
    {
        this(path,id,ImmutableList.of());
    }

    public MacaroonRequestMessage(String path, PnfsId id, ImmutableList<String> caveats)
    {
        super(true);
        this.path = path;
        this.caveats = caveats;
        this.id = id;
    }

    public String getPath()
    {
        return path;
    }

    public ImmutableList<String> getCaveats()
    {
        return caveats;
    }

    public String getMacaroon()
    {
        return macaroon;
    }

    public void setMacaroon(String macaroon)
    {
        this.macaroon = macaroon;
    }

    public PnfsId getPnfsId()
    {
        return id;
    }
}
