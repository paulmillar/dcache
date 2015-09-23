package org.dcache.macaroons.model;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

import javax.annotation.Nullable;
import javax.jdo.annotations.PersistenceCapable;
import javax.jdo.annotations.Persistent;
import javax.jdo.annotations.PrimaryKey;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

@PersistenceCapable
public class Macaroon
{
    @PrimaryKey
    protected String _id;

    @Persistent
    protected long _uid;

    @Persistent
    protected String _gids;

    @Persistent
    protected String _pnfsId;

    @Persistent
    protected byte[] _secret;

    @Persistent
    protected Date _created;

    @Persistent
    protected Date _termination;

    public String getId()
    {
        return _id;
    }

    public void setId(String id)
    {
        _id = checkNotNull(id);
    }

    public long getUid()
    {
        return _uid;
    }

    public void setUid(long uid)
    {
        _uid = uid;
    }

    public ImmutableList<Long> getGids()
    {
        ImmutableList.Builder<Long> list = ImmutableList.builder();

        if (_gids != null) {
            Splitter.on(',').split(_gids).forEach(g -> list.add(Long.valueOf(g)));
        }

        return list.build();
    }

    public void setGids(List<Long> gids)
    {
        _gids = gids.stream().map(Object::toString).
                collect(Collectors.joining(","));
    }

    public String getPnfsid()
    {
        return _pnfsId;
    }

    public void setPnfsid(String value)
    {
        _pnfsId = checkNotNull(value);
    }

    public byte[] getSecret()
    {
        return _secret;
    }

    public void setSecret(byte[] value)
    {
        _secret = checkNotNull(value);
    }

    public Date getCreated()
    {
        return _created;
    }

    public void setCreated(Date value)
    {
        _created = checkNotNull(value);
    }

    @Nullable
    public Date getTermination()
    {
        return _termination;
    }

    public void setTermination(Date value)
    {
        _termination = value;
    }
}
