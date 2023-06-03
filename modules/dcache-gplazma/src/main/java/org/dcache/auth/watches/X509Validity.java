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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A representation of an X.509 certificate's notBefore and notAfter information.  This assumes
 * that the notAfter timestamp is strictly after the notBefore timestamp.
 */
public enum X509Validity {
      /**
       * The notBefore timestamp is the current time or it is in the past.  The notAfter timestamp
       * is the current time or is in the future.  The certificate is valid.
       */
      OK,

      /**
       * The notAfter timestamp is in the past.  The certificate may not be used.
       */
      EXPIRED,

      /**
       * The notBefore timestamp is in the future.  The certificate may not be used yet.
       */
      EMBARGOED;

    public static final List<String> NAMES = Arrays.stream(X509Validity.values())
        .map(X509Validity::name)
        .collect(Collectors.toList());

}
