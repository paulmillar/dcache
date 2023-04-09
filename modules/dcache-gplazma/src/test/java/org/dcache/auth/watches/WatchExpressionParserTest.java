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
import java.util.Set;
import java.util.function.Predicate;
import org.dcache.auth.UserNamePrincipal;
import static org.dcache.auth.watches.LoginResultBuilderFramework.aLoginResult;
import static org.dcache.auth.watches.LoginResultBuilderFramework.aMapPlugin;
import static org.dcache.auth.watches.LoginResultBuilderFramework.anAuthPlugin;
import static org.dcache.auth.watches.LoginResultObservationBuilder.aLoginResultObservation;
import static org.dcache.gplazma.configuration.ConfigurationItemControl.OPTIONAL;
import static org.dcache.gplazma.configuration.ConfigurationItemControl.REQUISITE;
import static org.dcache.gplazma.monitor.LoginMonitor.Result.FAIL;
import static org.dcache.gplazma.monitor.LoginMonitor.Result.SUCCESS;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.junit.Before;
import org.junit.BeforeClass;
import org.parboiled.Parboiled;
import org.parboiled.parserunners.ReportingParseRunner;
import org.parboiled.support.ParsingResult;

public class WatchExpressionParserTest {

    private static WatchExpressionParser parser;

    private ReportingParseRunner<Predicate<LoginResultObservation>> runner;
    private LoginResultObservation observation;

    @BeforeClass
    public static void setupOnce() {
        parser = Parboiled.createParser(WatchExpressionParser.class);
    }

    @Before
    public void setup() {
        runner = new ReportingParseRunner(parser.input());
        observation = null;
    }

    @Test
    public void shouldRecogniseSimplePredicates() {
        assertTrue(runner.run("dn:/C=DE/O=GermanGrid/OU=DESY/CN=Paul").isSuccess());
        assertTrue(runner.run("sub:0123456").isSuccess());
        assertTrue(runner.run("email:paul.millar@desy.de").isSuccess());
        assertTrue(runner.run("groupname:it").isSuccess());
        assertTrue(runner.run("username:paul").isSuccess());
        assertTrue(runner.run("uid:1000").isSuccess());
        assertTrue(runner.run("gid:1000").isSuccess());

        assertFalse(runner.run("user:paul").isSuccess()); // Unknown principal type
        assertFalse(runner.run("username:").isSuccess()); // Missing value
        assertFalse(runner.run("username").isSuccess()); // Missing operation
        assertFalse(runner.run("").isSuccess()); // No predicate
    }


    @Test
    public void shouldRecogniseCombined() {
        assertTrue(runner.run("!username:paul").isSuccess());
        assertTrue(runner.run("! username:paul").isSuccess());
        assertTrue(runner.run("NOT username:paul").isSuccess());
        assertTrue(runner.run("not username:paul").isSuccess());
        assertTrue(runner.run("username:paul OR groupname:it").isSuccess());
        assertTrue(runner.run("username:paul  OR  groupname:it").isSuccess());
        assertTrue(runner.run("username:paul or groupname:it").isSuccess());
        assertTrue(runner.run("username:paul || groupname:it").isSuccess());
        assertTrue(runner.run("username:paul||groupname:it").isSuccess());
        assertTrue(runner.run("username:paul OR !groupname:it").isSuccess());
        assertTrue(runner.run("username:paul or !groupname:it").isSuccess());
        assertTrue(runner.run("username:paul || !groupname:it").isSuccess());
        assertTrue(runner.run("username:paul||!groupname:it").isSuccess());
        assertTrue(runner.run("username:paul AND groupname:it").isSuccess());
        assertTrue(runner.run("username:paul and groupname:it").isSuccess());
        assertTrue(runner.run("username:paul && groupname:it").isSuccess());
        assertTrue(runner.run("username:paul&&groupname:it").isSuccess());
        assertTrue(runner.run("username:paul AND !groupname:it").isSuccess());
        assertTrue(runner.run("username:paul and !groupname:it").isSuccess());
        assertTrue(runner.run("username:paul && !groupname:it").isSuccess());
        assertTrue(runner.run("username:paul&&!groupname:it").isSuccess());

        assertFalse(runner.run("OR groupname:it").isSuccess());
    }

    @Test
    public void shouldMatchSimpleUsername() {
        var predicate = runner.run("username:paul").getTopStackValue();

        given(aLoginResultObservation().withResult(aLoginResult()
            .withAuthPhase()
                .with(anAuthPlugin("oidc", OPTIONAL).withResult(SUCCESS))
                .withPrincipals(Collections.EMPTY_SET, Set.of(new UserNamePrincipal("paul")))
                .withResult(SUCCESS)
            .withMapPhase()
                .with(aMapPlugin("multimap", REQUISITE).withError("No mapping possible").withResult(FAIL))
                .withPrincipals(Collections.EMPTY_SET, Collections.EMPTY_SET)
                .withResult(FAIL)));

        assertTrue(predicate.test(observation));
    }

    @Test
    public void shouldNotMatchDifferentSimpleUsername() {
        var predicate = runner.run("username:paul").getTopStackValue();

        given(aLoginResultObservation().withResult(aLoginResult()
            .withAuthPhase()
                .with(anAuthPlugin("oidc", OPTIONAL).withResult(SUCCESS))
                .withPrincipals(Collections.EMPTY_SET, Set.of(new UserNamePrincipal("tigran")))
                .withResult(SUCCESS)
            .withMapPhase()
                .with(aMapPlugin("multimap", REQUISITE).withError("No mapping possible").withResult(FAIL))
                .withPrincipals(Collections.EMPTY_SET, Collections.EMPTY_SET)
                .withResult(FAIL)));

        assertFalse(predicate.test(observation));
    }


    private void given(LoginResultObservationBuilder builder) {
        observation = builder.build();
    }
}