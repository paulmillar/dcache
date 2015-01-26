package org.dcache.cdmi.utils;

import org.junit.Test;

import diskCacheV111.util.PnfsId;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.*;

/**
 *
 * @author paul
 */
public class IDsTest
{
    @Test
    public void shouldEncodeOldPnfsId()
    {
        PnfsId id = new PnfsId("000F00000000000000389FC0");

        String objectId = IDs.toObjectID(id);

        assertThat(objectId, equalTo("0000053F001532E600000F00000000000000389FC0"));
    }

    @Test
    public void shouldEncodeNewPnfsId()
    {
        PnfsId id = new PnfsId("80D1B8B90CED30430608C58002811B3285FC");

        String objectId = IDs.toObjectID(id);

        assertThat(objectId, equalTo("0000053F001BBA950180D1B8B90CED30430608C58002811B3285FC"));
    }

    @Test
    public void shouldDecodeOldPnfsId()
    {
        PnfsId id = IDs.toPnfsID("0000053F001532E600000F00000000000000389FC0");

        assertThat(id.getId(), equalTo("000F00000000000000389FC0"));
    }

    @Test
    public void shouldDecodeNewPnfsId()
    {
        PnfsId id = IDs.toPnfsID("0000053F001BBA950180D1B8B90CED30430608C58002811B3285FC");

        assertThat(id.getId(), equalTo("80D1B8B90CED30430608C58002811B3285FC"));
    }

    @Test(expected=IllegalArgumentException.class)
    public void shouldRejectIfHasWrongLengthForObjectSize()
    {
        IDs.toPnfsID("0000053F001532E600000F00000000000000389FC000");
    }


    @Test(expected=IllegalArgumentException.class)
    public void shouldRejectIfHasWrongLengthForDcache()
    {
        IDs.toPnfsID("0000053F001632E600000F00000000000000389FC000");
    }

    @Test(expected=IllegalArgumentException.class)
    public void shouldRejectIfHasWrongEnterpriseNumber()
    {
        IDs.toPnfsID("000005FF001532E600000F00000000000000389FC0");
    }

    @Test(expected=IllegalArgumentException.class)
    public void shouldRejectIfHasWrongChecksum()
    {
        IDs.toPnfsID("0000053F0015000000000F00000000000000389FC0");
    }

    @Test(expected=IllegalArgumentException.class)
    public void shouldRejectIfHasNewLengthWithOldPnfsid()
    {
        IDs.toPnfsID("0000053F001B32E600000F00000000000000389FC0");
    }

    @Test(expected=IllegalArgumentException.class)
    public void shouldRejectIfHasOldLengthWithNewPnfsid()
    {
        IDs.toPnfsID("0000053F0015BA950180D1B8B90CED30430608C58002811B3285FC");
    }
}
