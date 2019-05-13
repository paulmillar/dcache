// $Id: PoolMgrQueryPoolsMsg.java,v 1.6 2007-07-23 10:49:09 behrmann Exp $

package diskCacheV111.vehicles;

import java.util.List;
import java.util.Optional;

import diskCacheV111.poolManager.PoolSelectionUnit.DirectionType;

import org.dcache.namespace.FileAttribute;
import org.dcache.vehicles.FileAttributes;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class PoolMgrQueryPoolsMsg extends PoolManagerMessage
{
   private static final long serialVersionUID = 2891200690839001621L;

   private final DirectionType _accessType  ;

   private final String _netUnitName;
   private final String _protocolUnitName;
   private final String _zoneName;
   private final FileAttributes _fileAttributes;
   private List<String> []_poolList;

    public PoolMgrQueryPoolsMsg(DirectionType accessType,
            String protocolUnit,
            String netUnitName,
            Optional<String> zoneName,
            FileAttributes fileAttributes) {
       _accessType       = checkNotNull(accessType);
       _protocolUnitName = checkNotNull(protocolUnit);
       _netUnitName      = checkNotNull(netUnitName);
       _fileAttributes   = checkNotNull(fileAttributes);
       _zoneName = zoneName.orElse(null);
       checkArgument(fileAttributes.isDefined(FileAttribute.STORAGEINFO));

       setReplyRequired(true);
   }
   public DirectionType getAccessType(){ return _accessType ; }

   public String getNetUnitName(){ return _netUnitName ; }
   public String getProtocolUnitName(){ return _protocolUnitName; }
   public FileAttributes getFileAttributes() { return _fileAttributes; }
   public void setPoolList( List<String> [] poolList ){ _poolList = poolList ; }
   public List<String> [] getPools(){ return _poolList ; }
   public Optional<String> getZone()
   {
       return Optional.ofNullable(_zoneName);
   }
}
