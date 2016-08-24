// $Id: PoolMoverKillMessage.java,v 1.1 2004-11-08 23:01:47 timur Exp $

package diskCacheV111.vehicles;

import javax.annotation.Nonnull;

import static java.util.Objects.requireNonNull;

public class PoolMoverKillMessage extends PoolMessage {

    private static final long serialVersionUID = -8654307136745044047L;

    private final String explanation;
    public int  moverId;

    public PoolMoverKillMessage(String poolName, int moverId,
            @Nonnull String explanation){
	super(poolName);
        this.moverId = moverId ;
        setReplyRequired(true);
        this.explanation = requireNonNull(explanation);
    }

    @Nonnull
    public String getExplanation()
    {
        return explanation;
    }

    public int getMoverId(){ return moverId ; }
}


