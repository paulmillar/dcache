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

import java.util.Collections;
import org.junit.Test;
import org.junit.Before;

import static org.dcache.auth.watches.LoginResultObservationBuilder.aLoginResultObservation;
import static org.dcache.auth.watches.LoginResultBuilderFramework.aLoginResult;
import static org.dcache.auth.watches.LoginResultBuilderFramework.aMapPlugin;
import static org.dcache.auth.watches.LoginResultBuilderFramework.anAuthPlugin;
import static org.dcache.gplazma.configuration.ConfigurationItemControl.OPTIONAL;
import static org.dcache.gplazma.configuration.ConfigurationItemControl.REQUISITE;
import static org.dcache.gplazma.monitor.LoginMonitor.Result.FAIL;
import static org.dcache.gplazma.monitor.LoginMonitor.Result.SUCCESS;

public class PrincipalPredicateTest {

    private LoginResultObservation observation;

    @Before
    public void setup() {
        observation = null;
    }

    @Test
    public void shouldWork() {
        given(aLoginResultObservation().withResult(aLoginResult()
            .withAuthPhase()
                .with(anAuthPlugin("oidc", OPTIONAL)
                      .withResult(SUCCESS))
                .with(anAuthPlugin("x509", OPTIONAL)
                      .withError("No X.509 credential")
                      .withResult(FAIL))
                .withPrincipals(Collections.EMPTY_SET, Collections.EMPTY_SET)
                .withResult(SUCCESS)
            .withMapPhase()
                .with(aMapPlugin("multimap", REQUISITE)
                      .withError("No mapping possible")
                      .withResult(FAIL))
                .withPrincipals(Collections.EMPTY_SET, Collections.EMPTY_SET)
                .withResult(FAIL)));

        // parse predicate phrase

        // test if it matches.
    }

    private void given(LoginResultObservationBuilder builder) {
        observation = builder.build();
    }
}