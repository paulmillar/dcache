package org.dcache.macaroons;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import javax.jdo.PersistenceManager;
import javax.jdo.PersistenceManagerFactory;
import javax.jdo.Query;

import org.dcache.macaroons.model.Macaroon;

@Repository
public class JdoDao implements MacaroonDao
{
    private final static Logger _log = LoggerFactory.getLogger(JdoDao.class);

    private PersistenceManagerFactory _pmf;

    @Required
    public void setPersistenceManagerFactory(PersistenceManagerFactory pmf)
    {
        _pmf = pmf;
    }

    @Override @Transactional
    public Macaroon storeMacaroon(Macaroon macaroon)
    {
        PersistenceManager pm = _pmf.getPersistenceManager();
        macaroon = pm.detachCopy(pm.makePersistent(macaroon));
        if (_log.isDebugEnabled()) {
            _log.debug(macaroon.toString());
        }
        return macaroon;
    }

    @Override @Transactional(readOnly=true)
    public Macaroon getMacaroon(String id)
    {
        PersistenceManager pm = _pmf.getPersistenceManager();
        Query query = pm.newQuery(Macaroon.class, "_id == :id");
        query.setUnique(true);
        Macaroon macaroon = (Macaroon) query.execute(id);
        return (macaroon == null) ? null : pm.detachCopy(macaroon);
    }
}
