/* dCache - http://www.dcache.org/
 *
 * Copyright (C) 2023 Deutsches Elektronen-Synchrotron
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
package org.dcache.auth.watches;

import java.time.Instant;
import java.util.Objects;
import org.dcache.gplazma.monitor.LoginResult;

/**
 * Information about a specific login result.  This class includes both the login result itself
 * and any ancillary information that may be useful.
 */
public class LoginResultObservation {

  private final LoginResult result;
  private final Instant whenObserved = Instant.now();

  public LoginResultObservation(LoginResult result) {
    this.result = Objects.requireNonNull(result);
  }

  public LoginResult getResult() {
    return result;
  }

  public Instant getWhenObserved() {
    return whenObserved;
  }
}
