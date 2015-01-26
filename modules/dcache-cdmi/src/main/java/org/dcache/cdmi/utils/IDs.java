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
package org.dcache.cdmi.utils;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.BaseEncoding;

import diskCacheV111.util.PnfsId;

import static org.dcache.cdmi.utils.CRC16Calculator.doCRC;
import static com.google.common.base.Preconditions.checkArgument;

/**
 * Utility methods for handling translation to and from CDMI ObjectIDs
 */
public class IDs
{
    //see: http://www.iana.org/assignments/enterprise-numbers/enterprise-numbers
    private static final int DESY_ENTERPRISE_NUMBER = 1343;
    private static final byte[] DESY_ENTERPRISE_NUMBER_BYTEARRAY =
            toNetworkByteOrder3(DESY_ENTERPRISE_NUMBER);

    private static final byte RESERVED = 0;

    private static final int MINIMUM_OBJECTID_SIZE = 9;

    /**
     * The different types of CDMI ObjectID.
     */
    public enum Type {
        /** The shorter IDs as used by PNFS. *//** The shorter IDs as used by PNFS. */
        PNFS_ID(0),

        /** The longer IDs as used by Chimera. */
        CHIEMRA_ID(1),

        /** The CDMI root element ("/"). */
        ROOT(2),

        /** The CDMI Capabilities root element ("/cdmi_capabilities/"). */
        SYSTEM_CAPABILITIES(3),

        /** The Domain Capabilities ("/cdmi_capabilities/domain/"). */
        DOMAIN_CAPABILITIES(4),

        /** The Container Capabilities ("/cdmi_capabilities/container/"). */
        CONTAINER_CAPABILITIES(5),

        /** The DataObject Capabilities ("/cdmi_capabilities/dataobject/"). */
        DATAOBJECT_CAPABILITIES(6),

        /** The Queue Capabilities ("/cdmi_capabilities/queue/"). */
        QUEUE_CAPABILITIES(7);

        /** How this ObjectID is identified within the ObjectID */
        final byte id;

        Type(int id) {
            this.id = (byte)id;
        }
    }

    private static ImmutableMap<Byte,Type> TYPE_BYTES;

    static {
        ImmutableMap.Builder<Byte,Type> builder = ImmutableMap.builder();
        for (Type type : Type.values()) {
            builder.put(type.id, type);
        }
        TYPE_BYTES = builder.build();
    }

    public static PnfsId toPnfsID(String objectID) throws IllegalArgumentException
    {
        byte[] id = asBytes(objectID);

        Type type = TYPE_BYTES.get(id[8]);
        checkArgument(type != null, "Unknown type of ObjectID");

        int pnfsSize = id.length - MINIMUM_OBJECTID_SIZE;
        if ((pnfsSize != PnfsId.OLD_ID_SIZE && pnfsSize != PnfsId.NEW_ID_SIZE) ||
                (pnfsSize == PnfsId.OLD_ID_SIZE && type != Type.PNFS_ID) ||
                (pnfsSize == PnfsId.NEW_ID_SIZE && type != Type.CHIEMRA_ID)) {
            throw new IllegalArgumentException("Not a dCache ObjectID: " + objectID);
        }

        byte[] bytes = new byte [pnfsSize];
        System.arraycopy(id, 9, bytes, 0, pnfsSize);
        return new PnfsId(bytes);
    }

    public static Type toType(String objectID)
    {
        byte[] id = asBytes(objectID);
        Type type = TYPE_BYTES.get(id[8]);
        checkArgument(type != null, "Unknown type of ObjectID");
        return type;
    }

    private static byte[] asBytes(String objectID) throws IllegalArgumentException
    {
        byte[] id = BaseEncoding.base16().decode(objectID);

        checkArgument(id.length == id [5], "Length field does not match object size");
        checkArgument(id.length < MINIMUM_OBJECTID_SIZE, "Length too small");

        checkArgument(id [1] == DESY_ENTERPRISE_NUMBER_BYTEARRAY[0] &&
                id [2] == DESY_ENTERPRISE_NUMBER_BYTEARRAY[1] &&
                id [3] == DESY_ENTERPRISE_NUMBER_BYTEARRAY[2], "Not a dCache ObjectID");

        byte crcHigh = id[6];
        byte crcLow = id[7];

        long expectedCrc = (((long)id[6] << 8) | ((long)id[7] & 0xff)) & 0xffff;
        id [6] = 0;
        id [7] = 0;
        long actualCrc = toCRC16(id);
        if (expectedCrc != actualCrc) {
            throw new IllegalArgumentException("Corrupted ObjectID " + Long.toHexString(expectedCrc) + " (" + Byte.toString(id[6]) + Byte.toString(id[7]) + ") != " + Long.toHexString(actualCrc) + ":  " + objectID);
        }

        id [6] = crcHigh;
        id [7] = crcLow;

        return id;
    }

    public static String toObjectID(Type type)
    {
        checkArgument(type != Type.PNFS_ID && type != Type.CHIEMRA_ID,
                "Cannot create filesystem ObjectID without ID");
        return toObjectID(new byte[MINIMUM_OBJECTID_SIZE], type);
    }

    public static String toObjectID(PnfsId pnfsid)
    {
        byte[] bytes = pnfsid.getBytes();
        byte length = (byte)(bytes.length + MINIMUM_OBJECTID_SIZE);

        byte[] id = new byte[length];
        System.arraycopy(pnfsid.getBytes(), 0, id, 9, bytes.length);

        Type type = (bytes.length == PnfsId.OLD_ID_SIZE) ? Type.PNFS_ID :
                Type.CHIEMRA_ID;
        return toObjectID(id, type);
    }

    private static String toObjectID(byte[] id, Type type)
    {
        id [0] = RESERVED;
        id [1] = DESY_ENTERPRISE_NUMBER_BYTEARRAY[0];
        id [2] = DESY_ENTERPRISE_NUMBER_BYTEARRAY[1];
        id [3] = DESY_ENTERPRISE_NUMBER_BYTEARRAY[2];
        id [4] = RESERVED;
        id [5] = (byte)id.length;
        id [6] = 0;
        id [7] = 0;
        id [8] = type.id;

        long crc = toCRC16(id);
        id[6] = (byte) ((crc >> 8) & 0xff);
        id[7] = (byte) (crc & 0xff);

        return BaseEncoding.base16().upperCase().encode(id);
    }

    private static byte[] toNetworkByteOrder3(int data)
    {
        byte[] result = new byte[3];
        result[0] = (byte) ((data >> 16) & 0xff);
        result[1] = (byte) ((data >> 8) & 0xff);
        result[2] = (byte) (data & 0xff);
        return result;
    }

    private static long toCRC16(byte[] data)
    {
        return doCRC(data, 0x8005, 16, 0, true, true, false);
    }
}
