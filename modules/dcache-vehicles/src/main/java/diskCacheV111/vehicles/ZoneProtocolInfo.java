/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package diskCacheV111.vehicles;

import java.util.Optional;

public interface ZoneProtocolInfo
{
    /**
     * Return the zone of the client.
     */
    Optional<String> getZone();
}
