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

import org.dcache.gplazma.monitor.LoginResult;

import static java.util.Objects.requireNonNull;
import org.dcache.auth.watches.LoginResultBuilderFramework.LoginResultBaseBuilder;
import org.dcache.auth.watches.LoginResultBuilderFramework.LoginResultBuilder;

/**
 * A fluent class to build LoginResultObservation objects.
 */
public class LoginResultObservationBuilder {
    private LoginResult result;

    public static LoginResultObservationBuilder aLoginResultObservation() {
        return new LoginResultObservationBuilder();
    }

    public LoginResultObservationBuilder withResult(LoginResultBaseBuilder result) {
        return withResult(result.build());
    }

    public LoginResultObservationBuilder withResult(LoginResult result) {
        this.result = requireNonNull(result);
        return this;
    }

    public LoginResultObservation build() {
        return new LoginResultObservation(result);
    }
}
