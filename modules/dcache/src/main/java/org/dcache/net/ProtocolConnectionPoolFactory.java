/*
 * $Id: ProtocolConnectionPoolFactory.java,v 1.1 2006-07-18 09:06:04 tigran Exp $
 */
package org.dcache.net;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.dcache.util.PortRange;

public class ProtocolConnectionPoolFactory
{
    private final static Map<Specification,ProtocolConnectionPool> _pools = new HashMap<>();

    private final ChallengeReader _challengeReader;

    public ProtocolConnectionPoolFactory(ChallengeReader challengeReader)
    {
            _challengeReader = challengeReader;
    }

    public ProtocolConnectionPool getConnectionPool(PortRange range, int bufferSize)
            throws IOException
    {
        Specification spec = new Specification(range, bufferSize);

        ProtocolConnectionPool pool;

        synchronized (_pools) {
            pool = _pools.get(spec);

            if (pool == null) {
                pool = new ProtocolConnectionPool(range, bufferSize, _challengeReader);
                pool.start();
                _pools.put(spec, pool);
            }
        }

        return pool;
    }


    private class Specification
    {
        private final PortRange _range;
        private final int _bufferSize;

        public Specification(PortRange range, int bufferSize)
        {
            _range = range;
            _bufferSize = bufferSize;
        }

        public PortRange getPortRange()
        {
            return _range;
        }

        public int getBufferSize()
        {
            return _bufferSize;
        }

        @Override
        public int hashCode()
        {
            return _range.hashCode() ^ _bufferSize;
        }

        @Override
        public boolean equals(Object rawOther)
        {
            if (rawOther == this) {
                return true;
            }

            if (!(rawOther instanceof Specification)) {
                return false;
            }

            Specification other = (Specification)rawOther;

            return other._range.equals(_range) &&
                    other._bufferSize == _bufferSize;
        }
    }
}
