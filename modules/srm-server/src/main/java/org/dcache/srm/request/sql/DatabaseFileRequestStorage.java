/*
 * DatabaseFileRequestStorage.java
 *
 * Created on June 17, 2004, 3:18 PM
 */

package org.dcache.srm.request.sql;

import org.springframework.dao.DataAccessException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.dcache.srm.request.FileRequest;
import org.dcache.srm.util.Configuration;

/**
 *
 * @author  timur
 */
public abstract class DatabaseFileRequestStorage<F extends FileRequest<?>> extends DatabaseJobStorage<F>  {

    /** Creates a new instance of FileRequestStorage */
    public DatabaseFileRequestStorage(Configuration.DatabaseParameters configuration)
            throws DataAccessException
    {
        super(configuration);
    }

    @Override
    protected void dbInit(boolean clean) throws DataAccessException
    {
           super.dbInit(clean);
           String columns[] = {
		    "REQUESTID"};
	   createIndex(columns, getTableName().toLowerCase());
    }


    public abstract String getFileRequestCreateTableFields();

    public abstract String getRequestTableName();

    @Override
    public String getCreateTableFields() {
        return
        ","+
        "REQUESTID "+  longType+
        ", CREDENTIALID "+  longType+
        ", "+
        "STATUSCODE "+ stringType+
        getFileRequestCreateTableFields();
    }

    private static int ADDITIONAL_FIELDS_NUM=3;

    protected abstract F getFileRequest(
    Connection _con,
    long ID,
    Long NEXTJOBID,
    long CREATIONTIME,
    long LIFETIME,
    int STATE,
    String ERRORMESSAGE,
    String SCHEDULERID,
    long SCHEDULER_TIMESTAMP,
    int NUMOFRETR,
    int MAXNUMOFRETR,
    long LASTSTATETRANSITIONTIME,
    long REQUESTID,
    Long CREDENTIALID,
    String STATUSCODE,
    ResultSet set,
    int next_index)throws SQLException;

    @Override
    protected F
    getJob(
    Connection _con,
    long ID,
    Long NEXTJOBID,
    long CREATIONTIME,
    long LIFETIME,
    int STATE,
    String ERRORMESSAGE,
    String SCHEDULERID,
    long SCHEDULER_TIMESTAMP,
    int NUMOFRETR,
    int MAXNUMOFRETR,
    long LASTSTATETRANSITIONTIME,
    ResultSet set,
    int next_index) throws SQLException {
        long REQUESTID = set.getLong(next_index++);
        Long CREDENTIALID = set.getLong(next_index++);
        String STATUSCODE= set.getString(next_index++);
        return getFileRequest(
        _con,
        ID,
        NEXTJOBID ,
        CREATIONTIME,
        LIFETIME,
        STATE,
        ERRORMESSAGE,
        SCHEDULERID,
        SCHEDULER_TIMESTAMP,
        NUMOFRETR,
        MAXNUMOFRETR,
        LASTSTATETRANSITIONTIME,
        REQUESTID,
        CREDENTIALID,
        STATUSCODE,
        set,
        next_index );
    }

    @Override
    public abstract String getTableName();

    protected abstract void __verify(int nextIndex, int columnIndex, String tableName, String columnName, int columnType) throws SQLException ;

    @Override
    protected void _verify(int nextIndex, int columnIndex, String tableName, String columnName, int columnType) throws SQLException {
        /*
         *additional fields:
         "REQUESTID "+  longType+
        ", CREDENTIALID "+  stringType+*/
        if(columnIndex == nextIndex) {
            verifyLongType("REQUESTID",columnIndex,tableName, columnName, columnType);
        }
        else if(columnIndex == nextIndex+1)
        {
            verifyLongType("CREDENTIALID",columnIndex,tableName, columnName, columnType);

        }
        else if(columnIndex == nextIndex+2)
        {
            verifyStringType("STATUSCODE",columnIndex,tableName, columnName, columnType);
        }
        else
        {
            __verify(nextIndex+3,columnIndex,tableName, columnName, columnType);
        }
   }

    protected abstract int getMoreCollumnsNum();
    @Override
    protected int getAdditionalColumnsNum() {
        return ADDITIONAL_FIELDS_NUM +getMoreCollumnsNum();
    }

    /*protected java.util.Set getFileRequests(String requestId) throws java.sql.SQLException{
        return getJobsByCondition(" REQUESTID = '"+requestId+"'");
    }*/


    /* FIXME: need to use JdbcTemplate

    Set<Long> queryIdsForRequest(long requestId) throws SQLException
    {
        Set<Long> ids = new HashSet<>();

        String sql = "SELECT ID FROM " + getTableName() + " WHERE REQUESTID=?";

        Connection con = pool.getConnection();
        try (PreparedStatement statement = con.prepareStatement(sql)) {
            statement.setLong(1, requestId);

            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    ids.add(results.getLong(1));
                }
            }
        } finally {
            pool.returnConnection(con);
        }

        return ids;
    }

    long ordinalForRequest(long requestId, long id) throws SQLException
    {
        Set<Long> ids = queryIdsForRequest(requestId);

        if (ids.isEmpty()) {
            throw new IllegalArgumentException("No request with ID " + requestId);
        }

        List<Long> ordered = new ArrayList<>(ids);
        Collections.sort(ordered);

        if (ordered.get(0) < 0 && ordered.get(ordered.size()-1) > 0) {
            int i = 0;
            while (ordered.get(i) < 0) {
                i++;
            }
            Collections.rotate(ordered, -i);
        }

        int index = 1;
        for (long thisId : ordered) {
            if (thisId == id) {
                return index;
            }
            index++;
        }

        throw new IllegalArgumentException("FileRequest ID " + id +
                " not associated with request ID " + requestId);
    }
    */

    long ordinalForRequest(long requestId, long id)
    {
        return 0;
    }

    Set<Long> queryIdsForRequest(long requestId)
    {
        return Collections.emptySet();
    }

}
