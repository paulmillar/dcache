// $Id: PoolMoverKillMessage.java,v 1.1 2004-11-08 23:01:47 timur Exp $

package diskCacheV111.vehicles;

import javax.annotation.Nullable;

public class PoolMoverKillMessage extends PoolMessage {

    private static final long serialVersionUID = -8654307136745044047L;

    public int  moverId;
    private String explanation;

    public PoolMoverKillMessage(String poolName, int moverId){
	super(poolName);
        this.moverId = moverId ;
        setReplyRequired(true);
    }

    /**
     * A short explanation on why the mover is being killed.
     */
    public void setExplanation(@Nullable String explanation)
    {
        this.explanation = explanation;
    }

    @Nullable
    public String getExplanation()
    {
        return explanation;
    }

    public int getMoverId(){ return moverId ; }
}


