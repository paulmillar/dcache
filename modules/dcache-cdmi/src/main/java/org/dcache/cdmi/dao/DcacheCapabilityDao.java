/* dCache - http://www.dcache.org/
 *
 * Copyright (C) 2015 Deutsches Elektronen-Synchrotron
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.dcache.cdmi.dao;

import com.google.common.collect.ImmutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snia.cdmiserver.dao.CapabilityDao;
import org.snia.cdmiserver.model.Capability;

import org.dcache.cdmi.utils.IDs;
import org.dcache.cdmi.utils.IDs.Type;


/**
 * <p>
 * Concrete implementation of {@link CapabilityObjectDao} using the local filesystem as the backing
 * store.
 * </p>
 */
public class DcacheCapabilityDao implements CapabilityDao
{
    private final static Logger _log = LoggerFactory.getLogger(DcacheCapabilityDao.class);

    private static final String ROOT_OBJECTID = IDs.toObjectID(Type.ROOT);
    private static final String SYSTEM_CAPABILITIES_OBJECTID = IDs.toObjectID(Type.SYSTEM_CAPABILITIES);
    private static final String CONTAINER_CAPABILITIES_OBJECTID = IDs.toObjectID(Type.CONTAINER_CAPABILITIES);
    private static final String DATAOBJECT_CAPABILITIES_OBJECTID = IDs.toObjectID(Type.DATAOBJECT_CAPABILITIES);

    private final ImmutableMap<String,Type> pathToType;

    public DcacheCapabilityDao()
    {
        ImmutableMap.Builder<String,Type> builder = ImmutableMap.builder();
        builder.put("", Type.SYSTEM_CAPABILITIES);
        builder.put("container/", Type.CONTAINER_CAPABILITIES);
        builder.put("dataobject/", Type.DATAOBJECT_CAPABILITIES);
        pathToType = builder.build();

        _log.warn("DCacheCapabilityDaoImpl created");
    }

    @Override
    public Capability findByObjectId(String objectId)
    {
        Type type = IDs.toType(objectId);
        return buildCapability(type);
    }

    @Override
    public Capability findByPath(String path)
    {
        Type type = pathToType.get(path);

        if (type == null) {
            throw new IllegalArgumentException("Unknown capability: " + path);
        }

        return buildCapability(type);
    }

    private Capability buildCapability(Type type)
    {
        Capability capability = new Capability();

        switch (type) {
        case SYSTEM_CAPABILITIES:
            capability.getMetadata().put("domains", "false");
            capability.getMetadata().put("cdmi_export_occi_iscsi", "true");
            capability.getMetadata().put("cdmi_metadata_maxitems", "1024");
            capability.getMetadata().put("cdmi_metadata_maxsize", "4096");
            capability.getMetadata().put("cdmi_assignedsize", "false");
            capability.getMetadata().put("cdmi_data_redundancy", "");
            capability.getMetadata().put("cdmi_data_dispersion", "false");
            capability.getMetadata().put("cdmi_data_retention", "false");
            capability.getMetadata().put("cdmi_data_autodelete", "false");
            capability.getMetadata().put("cdmi_data_holds", "false");
            capability.getMetadata().put("cdmi_encryption", "{}");
            capability.getMetadata().put("cdmi_geographic_placement", "false");
            capability.getMetadata().put("cdmi_immediate_redundancy", "");
            capability.getMetadata().put("cdmi_infrastructure_redundancy", "");
            capability.getMetadata().put("cdmi_latency", "false");
            capability.getMetadata().put("cdmi_RPO", "false");
            capability.getMetadata().put("cdmi_RTO", "false");
            capability.getMetadata().put("cdmi_sanitization_method", "{}");
            capability.getMetadata().put("cdmi_throughput", "false");
            capability.getMetadata().put("cdmi_value_hash", "{}");

            capability.getChildren().add("container");
            capability.getChildren().add("dataobject");

            capability.setObjectID(SYSTEM_CAPABILITIES_OBJECTID);
            capability.setParentURI("/");
            capability.setParentID(ROOT_OBJECTID);
            break;

        case CONTAINER_CAPABILITIES:
            capability.getMetadata().put("cdmi_list_children", "true");
            capability.getMetadata().put("cdmi_read_metadata", "true");
            capability.getMetadata().put("cdmi_modify_metadata", "true");
            capability.getMetadata().put("cdmi_create_dataobject", "true");
            capability.getMetadata().put("cdmi_create_container", "true");
            capability.setObjectID(CONTAINER_CAPABILITIES_OBJECTID);
            capability.setParentURI("cdmi_capabilities/");
            capability.setParentID(SYSTEM_CAPABILITIES_OBJECTID);
            break;

        case DATAOBJECT_CAPABILITIES:
            capability.getMetadata().put("cdmi_read_value", "true");
            capability.getMetadata().put("cdmi_read_metadata", "true");
            capability.getMetadata().put("cdmi_modify_metadata", "true");
            capability.getMetadata().put("cdmi_modify_value", "true");
            capability.getMetadata().put("cdmi_delete_dataobject", "true");
            capability.setObjectID(DATAOBJECT_CAPABILITIES_OBJECTID);
            capability.setParentURI("cdmi_capabilities/");
            capability.setParentID(SYSTEM_CAPABILITIES_OBJECTID);
            break;

        default:
            throw new IllegalArgumentException("Unknown capabilities type: " + type);
        }

        capability.setObjectType("application/cdmi-capability");

        return capability;
    }
}
