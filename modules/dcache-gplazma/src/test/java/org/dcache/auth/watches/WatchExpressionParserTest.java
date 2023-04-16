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

import java.util.function.Predicate;
import java.util.stream.Collectors;
import static org.dcache.auth.watches.LoginResultBuilderFramework.aLoginResult;
import static org.dcache.auth.watches.LoginResultBuilderFramework.aMapPlugin;
import static org.dcache.auth.watches.LoginResultBuilderFramework.anAuthPlugin;
import static org.dcache.auth.watches.LoginResultObservationBuilder.aLoginResultObservation;
import static org.dcache.gplazma.configuration.ConfigurationItemControl.OPTIONAL;
import static org.dcache.gplazma.monitor.LoginMonitor.Result.SUCCESS;
import org.dcache.util.ColumnWriter;
import static org.dcache.util.PrincipalSetMaker.aSetOfPrincipals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.junit.Before;
import org.junit.BeforeClass;
import org.parboiled.Parboiled;
import org.parboiled.errors.InvalidInputError;
import org.parboiled.errors.ParseError;
import org.parboiled.errors.ParserRuntimeException;
import org.parboiled.parserunners.ReportingParseRunner;
import org.parboiled.support.MatcherPath;

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
    public void shouldRecogniseGlobPredicates() {
        assertTrue(runner.run("dn~/C=DE*").isSuccess());
        assertTrue(runner.run("sub~*@OP").isSuccess());
        assertTrue(runner.run("email~*@desy.de").isSuccess());
        assertTrue(runner.run("groupname~it-*").isSuccess());
        assertTrue(runner.run("username~p??l").isSuccess());
        assertTrue(runner.run("uid~100?").isSuccess());
        assertTrue(runner.run("gid~?000").isSuccess());
    }

    @Test
    public void shouldRecogniseRegularExpressionPredicates() {
        assertTrue(runner.run("dn/.*O=Organisation.*/").isSuccess());
        assertTrue(runner.run("sub/.*@OP/").isSuccess());
        assertTrue(runner.run("email/[a-z.]*@desy\\.de/").isSuccess());
        assertTrue(runner.run("groupname/(it|other)-foo/").isSuccess());
        assertTrue(runner.run("username/p.*/").isSuccess());
        assertTrue(runner.run("uid/[0-9]{4}/").isSuccess());
        assertTrue(runner.run("gid/[3-9][2-4]/").isSuccess());
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

    @Test(expected=NullPointerException.class)
    public void shouldThrowNPEWhenUnescapeNull() {
        WatchExpressionParser.unescape(null);
    }

    @Test
    public void shouldUnescapeEmptyString() {
        var result = WatchExpressionParser.unescape("");
        assertThat(result, equalTo(""));
    }

    @Test
    public void shouldUnescapeSimpleText() {
        var result = WatchExpressionParser.unescape("This is a test");
        assertThat(result, equalTo("This is a test"));
    }

    @Test
    public void shouldUnescapeTextWithBlackslashQuote() {
        var result = WatchExpressionParser.unescape("He said \\\"this is a test\\\".");
        assertThat(result, equalTo("He said \"this is a test\"."));
    }

    @Test
    public void shouldUnescapeTextWithBlackslashN() {
        var result = WatchExpressionParser.unescape("Line 1,\\nLine2.");
        assertThat(result, equalTo("Line 1,\nLine2."));
    }

    @Test(expected=ParserRuntimeException.class)
    public void shouldThrowParserErrorOnBadEscape() {
        WatchExpressionParser.unescape("The following escape sequence is unknown\\a");
    }

    @Test
    public void shouldMatchSimpleUsername() {
        var predicate = whenParsing("username:paul");

        given(aLoginResultObservation().withResult(aLoginResult()
            .withValidationResult(SUCCESS)
            .withAuthPhase()
                .with(anAuthPlugin("oidc", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUsername("paul"))
                .withResult(SUCCESS)
            .withMapPhase()
                .with(aMapPlugin("multimap", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUid(1000).withPrimaryGid(1000))
                .withResult(SUCCESS)
            .withAccountPhase()
                .withResult(SUCCESS)
            .withSessionPhase()
                .withResult(SUCCESS)));

        assertTrue(predicate.test(observation));
    }

    @Test
    public void shouldMatchSimpleGroupname() {
        var predicate = whenParsing("groupname:it");

        given(aLoginResultObservation().withResult(aLoginResult()
            .withValidationResult(SUCCESS)
            .withAuthPhase()
                .with(anAuthPlugin("oidc", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUsername("paul").withGroupname("it"))
                .withResult(SUCCESS)
            .withMapPhase()
                .with(aMapPlugin("multimap", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUid(1000).withPrimaryGid(1000))
                .withResult(SUCCESS)
            .withAccountPhase()
                .withResult(SUCCESS)
            .withSessionPhase()
                .withResult(SUCCESS)));

        assertTrue(predicate.test(observation));
    }

    @Test
    public void shouldNotMatchWrongSimpleGroupname() {
        var predicate = whenParsing("groupname:atlas");

        given(aLoginResultObservation().withResult(aLoginResult()
            .withValidationResult(SUCCESS)
            .withAuthPhase()
                .with(anAuthPlugin("oidc", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUsername("paul").withGroupname("it"))
                .withResult(SUCCESS)
            .withMapPhase()
                .with(aMapPlugin("multimap", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUid(1000).withPrimaryGid(1000))
                .withResult(SUCCESS)
            .withAccountPhase()
                .withResult(SUCCESS)
            .withSessionPhase()
                .withResult(SUCCESS)));

        assertFalse(predicate.test(observation));
    }

    @Test
    public void shouldMatchSimpleUid() {
        var predicate = whenParsing("uid:1000");

        given(aLoginResultObservation().withResult(aLoginResult()
            .withValidationResult(SUCCESS)
            .withAuthPhase()
                .with(anAuthPlugin("oidc", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUsername("paul"))
                .withResult(SUCCESS)
            .withMapPhase()
                .with(aMapPlugin("multimap", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUid(1000).withPrimaryGid(1000))
                .withResult(SUCCESS)
            .withAccountPhase()
                .withResult(SUCCESS)
            .withSessionPhase()
                .withResult(SUCCESS)));

        assertTrue(predicate.test(observation));
    }

    @Test
    public void shouldNotMatchSimpleWrongUid() {
        var predicate = whenParsing("uid:2000");

        given(aLoginResultObservation().withResult(aLoginResult()
            .withValidationResult(SUCCESS)
            .withAuthPhase()
                .with(anAuthPlugin("oidc", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUsername("paul"))
                .withResult(SUCCESS)
            .withMapPhase()
                .with(aMapPlugin("multimap", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUid(1000).withPrimaryGid(1000))
                .withResult(SUCCESS)
            .withAccountPhase()
                .withResult(SUCCESS)
            .withSessionPhase()
                .withResult(SUCCESS)));

        assertFalse(predicate.test(observation));
    }

    @Test
    public void shouldMatchSimpleGid() {
        var predicate = whenParsing("gid:1000");

        given(aLoginResultObservation().withResult(aLoginResult()
            .withValidationResult(SUCCESS)
            .withAuthPhase()
                .with(anAuthPlugin("oidc", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUsername("paul").withGroupname("it"))
                .withResult(SUCCESS)
            .withMapPhase()
                .with(aMapPlugin("multimap", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUid(1000).withPrimaryGid(1000))
                .withResult(SUCCESS)
            .withAccountPhase()
                .withResult(SUCCESS)
            .withSessionPhase()
                .withResult(SUCCESS)));

        assertTrue(predicate.test(observation));
    }

    @Test
    public void shouldNotMatchSimpleWrongGid() {
        var predicate = whenParsing("gid:2000");

        given(aLoginResultObservation().withResult(aLoginResult()
            .withValidationResult(SUCCESS)
            .withAuthPhase()
                .with(anAuthPlugin("oidc", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUsername("paul").withGroupname("it"))
                .withResult(SUCCESS)
            .withMapPhase()
                .with(aMapPlugin("multimap", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUid(1000).withPrimaryGid(1000))
                .withResult(SUCCESS)
            .withAccountPhase()
                .withResult(SUCCESS)
            .withSessionPhase()
                .withResult(SUCCESS)));

        assertFalse(predicate.test(observation));
    }

    @Test
    public void shouldMatchSimpleEmail() {
        var predicate = whenParsing("email:paul.millar@desy.de");

        given(aLoginResultObservation().withResult(aLoginResult()
            .withValidationResult(SUCCESS)
            .withAuthPhase()
                .with(anAuthPlugin("oidc", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withEmail("paul.millar@desy.de").withUsername("paul"))
                .withResult(SUCCESS)
            .withMapPhase()
                .with(aMapPlugin("multimap", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUid(1000).withPrimaryGid(1000))
                .withResult(SUCCESS)
            .withAccountPhase()
                .withResult(SUCCESS)
            .withSessionPhase()
                .withResult(SUCCESS)));

        assertTrue(predicate.test(observation));
    }

    @Test
    public void shouldNotMatchSimpleWrongEmail() {
        var predicate = whenParsing("email:fred.bloggs@example.org");

        given(aLoginResultObservation().withResult(aLoginResult()
            .withValidationResult(SUCCESS)
            .withAuthPhase()
                .with(anAuthPlugin("oidc", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withEmail("paul.millar@desy.de").withUsername("paul"))
                .withResult(SUCCESS)
            .withMapPhase()
                .with(aMapPlugin("multimap", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUid(1000).withPrimaryGid(1000))
                .withResult(SUCCESS)
            .withAccountPhase()
                .withResult(SUCCESS)
            .withSessionPhase()
                .withResult(SUCCESS)));

        assertFalse(predicate.test(observation));
    }

    @Test
    public void shouldMatchSimpleOidcSub() {
        var predicate = whenParsing("sub:paul@OP");

        given(aLoginResultObservation().withResult(aLoginResult()
            .withValidationResult(SUCCESS)
            .withAuthPhase()
                .with(anAuthPlugin("oidc", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withOidc("paul", "OP").withUsername("paul"))
                .withResult(SUCCESS)
            .withMapPhase()
                .with(aMapPlugin("multimap", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUid(1000).withPrimaryGid(1000))
                .withResult(SUCCESS)
            .withAccountPhase()
                .withResult(SUCCESS)
            .withSessionPhase()
                .withResult(SUCCESS)));

        assertTrue(predicate.test(observation));
    }

    @Test
    public void shouldNotMatchSimpleWrongByClaimOidcSub() {
        var predicate = whenParsing("sub:an.other@OP");

        given(aLoginResultObservation().withResult(aLoginResult()
            .withValidationResult(SUCCESS)
            .withAuthPhase()
                .with(anAuthPlugin("oidc", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withOidc("paul", "OP").withUsername("paul"))
                .withResult(SUCCESS)
            .withMapPhase()
                .with(aMapPlugin("multimap", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUid(1000).withPrimaryGid(1000))
                .withResult(SUCCESS)
            .withAccountPhase()
                .withResult(SUCCESS)
            .withSessionPhase()
                .withResult(SUCCESS)));

        assertFalse(predicate.test(observation));
    }

    @Test
    public void shouldNotMatchSimpleWrongByOpOidcSub() {
        var predicate = whenParsing("sub:paul@OTHER-OP");

        given(aLoginResultObservation().withResult(aLoginResult()
            .withValidationResult(SUCCESS)
            .withAuthPhase()
                .with(anAuthPlugin("oidc", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withOidc("paul", "OP").withUsername("paul"))
                .withResult(SUCCESS)
            .withMapPhase()
                .with(aMapPlugin("multimap", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUid(1000).withPrimaryGid(1000))
                .withResult(SUCCESS)
            .withAccountPhase()
                .withResult(SUCCESS)
            .withSessionPhase()
                .withResult(SUCCESS)));

        assertFalse(predicate.test(observation));
    }

    @Test
    public void shouldMatchSingleQuotedUsername() {
        var predicate = whenParsing("username:'paul'");

        given(aLoginResultObservation().withResult(aLoginResult()
            .withValidationResult(SUCCESS)
            .withAuthPhase()
                .with(anAuthPlugin("oidc", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUsername("paul"))
                .withResult(SUCCESS)
            .withMapPhase()
                .with(aMapPlugin("multimap", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUid(1000).withPrimaryGid(1000))
                .withResult(SUCCESS)
            .withAccountPhase()
                .withResult(SUCCESS)
            .withSessionPhase()
                .withResult(SUCCESS)));

        assertTrue(predicate.test(observation));
    }

    @Test
    public void shouldMatchDoubleQuotedUsername() {
        var predicate = whenParsing("username:\"paul\"");

        given(aLoginResultObservation().withResult(aLoginResult()
            .withValidationResult(SUCCESS)
            .withAuthPhase()
                .with(anAuthPlugin("oidc", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUsername("paul"))
                .withResult(SUCCESS)
            .withMapPhase()
                .with(aMapPlugin("multimap", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUid(1000).withPrimaryGid(1000))
                .withResult(SUCCESS)
            .withAccountPhase()
                .withResult(SUCCESS)
            .withSessionPhase()
                .withResult(SUCCESS)));

        assertTrue(predicate.test(observation));
    }

    @Test
    public void shouldMatchDoubleQuotedUsernameWithDoubleQuotes() {
        var predicate = whenParsing("username:\"pa\\\"ul\"");

        given(aLoginResultObservation().withResult(aLoginResult()
            .withValidationResult(SUCCESS)
            .withAuthPhase()
                .with(anAuthPlugin("oidc", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUsername("pa\"ul"))
                .withResult(SUCCESS)
            .withMapPhase()
                .with(aMapPlugin("multimap", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUid(1000).withPrimaryGid(1000))
                .withResult(SUCCESS)
            .withAccountPhase()
                .withResult(SUCCESS)
            .withSessionPhase()
                .withResult(SUCCESS)));

        assertTrue(predicate.test(observation));
    }

    @Test
    public void shouldNotMatchDifferentSimpleUsername() {
        var predicate = whenParsing("username:paul");

        given(aLoginResultObservation().withResult(aLoginResult()
            .withValidationResult(SUCCESS)
            .withAuthPhase()
                .with(anAuthPlugin("oidc", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUsername("tigran"))
                .withResult(SUCCESS)
            .withMapPhase()
                .with(aMapPlugin("multimap", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUid(1001).withPrimaryGid(1000))
                .withResult(SUCCESS)
            .withAccountPhase()
                .withResult(SUCCESS)
            .withSessionPhase()
                .withResult(SUCCESS)));

        assertFalse(predicate.test(observation));
    }

    @Test
    public void shouldMatchSimpleDn() {
        var predicate = whenParsing("dn:\"/DC=org/DC=terena/DC=tcs/C=DE/O=Deutsches Elektronen-Synchrotron DESY/CN=Alexander Paul Millar paul@desy.de\"");

        given(aLoginResultObservation().withResult(aLoginResult()
            .withValidationResult(SUCCESS)
            .withAuthPhase()
                .with(anAuthPlugin("x509", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withDn("/DC=org/DC=terena/DC=tcs/C=DE/O=Deutsches Elektronen-Synchrotron DESY/CN=Alexander Paul Millar paul@desy.de"))
                .withResult(SUCCESS)
            .withMapPhase()
                .with(aMapPlugin("multimap", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUsername("paul").withUid(1000).withPrimaryGid(1000))
                .withResult(SUCCESS)
            .withAccountPhase()
                .withResult(SUCCESS)
            .withSessionPhase()
                .withResult(SUCCESS)));

        assertTrue(predicate.test(observation));
    }

    @Test
    public void shouldNotMatchSimpleDnMissingPrincipal() {
        var predicate = whenParsing("dn:\"/DC=org/DC=terena/DC=tcs/C=DE/O=Deutsches Elektronen-Synchrotron DESY/CN=Alexander Paul Millar paul@desy.de\"");

        given(aLoginResultObservation().withResult(aLoginResult()
            .withValidationResult(SUCCESS)
            .withAuthPhase()
                .with(anAuthPlugin("oidc", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUsername("paul"))
                .withResult(SUCCESS)
            .withMapPhase()
                .with(aMapPlugin("multimap", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUid(1000).withPrimaryGid(1000))
                .withResult(SUCCESS)
            .withAccountPhase()
                .withResult(SUCCESS)
            .withSessionPhase()
                .withResult(SUCCESS)));

        assertFalse(predicate.test(observation));
    }

    @Test
    public void shouldNotMatchSimpleUsernameWithExclaimationNegationIfPresent() {
        var predicate = whenParsing("!username:paul");

        given(aLoginResultObservation().withResult(aLoginResult()
            .withValidationResult(SUCCESS)
            .withAuthPhase()
                .with(anAuthPlugin("oidc", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUsername("paul"))
                .withResult(SUCCESS)
            .withMapPhase()
                .with(aMapPlugin("multimap", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUid(1001).withPrimaryGid(1000))
                .withResult(SUCCESS)
            .withAccountPhase()
                .withResult(SUCCESS)
            .withSessionPhase()
                .withResult(SUCCESS)));

        assertFalse(predicate.test(observation));
    }

    @Test
    public void shouldMatchSimpleUsernameWithExclaimationNegationIfAbsent() {
        var predicate = whenParsing("!username:paul");

        given(aLoginResultObservation().withResult(aLoginResult()
            .withValidationResult(SUCCESS)
            .withAuthPhase()
                .with(anAuthPlugin("oidc", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUsername("tigran"))
                .withResult(SUCCESS)
            .withMapPhase()
                .with(aMapPlugin("multimap", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUid(1001).withPrimaryGid(1000))
                .withResult(SUCCESS)
            .withAccountPhase()
                .withResult(SUCCESS)
            .withSessionPhase()
                .withResult(SUCCESS)));

        assertTrue(predicate.test(observation));
    }

    @Test
    public void shouldSupportAndBinaryOperation() {
        var predicate = whenParsing("username:paul && uid:1000");

        given(aLoginResultObservation().withResult(aLoginResult()
            .withValidationResult(SUCCESS)
            .withAuthPhase()
                .with(anAuthPlugin("oidc", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUsername("paul"))
                .withResult(SUCCESS)
            .withMapPhase()
                .with(aMapPlugin("multimap", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUid(1000).withPrimaryGid(1000))
                .withResult(SUCCESS)
            .withAccountPhase()
                .withResult(SUCCESS)
            .withSessionPhase()
                .withResult(SUCCESS)));

        assertTrue(predicate.test(observation));

        given(aLoginResultObservation().withResult(aLoginResult()
            .withValidationResult(SUCCESS)
            .withAuthPhase()
                .with(anAuthPlugin("oidc", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUsername("tigran"))
                .withResult(SUCCESS)
            .withMapPhase()
                .with(aMapPlugin("multimap", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUid(1000).withPrimaryGid(1000))
                .withResult(SUCCESS)
            .withAccountPhase()
                .withResult(SUCCESS)
            .withSessionPhase()
                .withResult(SUCCESS)));

        assertFalse(predicate.test(observation));

        given(aLoginResultObservation().withResult(aLoginResult()
            .withValidationResult(SUCCESS)
            .withAuthPhase()
                .with(anAuthPlugin("oidc", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUsername("paul"))
                .withResult(SUCCESS)
            .withMapPhase()
                .with(aMapPlugin("multimap", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUid(2000).withPrimaryGid(1000))
                .withResult(SUCCESS)
            .withAccountPhase()
                .withResult(SUCCESS)
            .withSessionPhase()
                .withResult(SUCCESS)));

        assertFalse(predicate.test(observation));

        given(aLoginResultObservation().withResult(aLoginResult()
            .withValidationResult(SUCCESS)
            .withAuthPhase()
                .with(anAuthPlugin("oidc", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUsername("tigran"))
                .withResult(SUCCESS)
            .withMapPhase()
                .with(aMapPlugin("multimap", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUid(2000).withPrimaryGid(1000))
                .withResult(SUCCESS)
            .withAccountPhase()
                .withResult(SUCCESS)
            .withSessionPhase()
                .withResult(SUCCESS)));

        assertFalse(predicate.test(observation));
    }


    @Test
    public void shouldSupportOrBinaryOperation() {
        var predicate = whenParsing("username:paul || uid:1000");

        given(aLoginResultObservation().withResult(aLoginResult()
            .withValidationResult(SUCCESS)
            .withAuthPhase()
                .with(anAuthPlugin("oidc", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUsername("paul"))
                .withResult(SUCCESS)
            .withMapPhase()
                .with(aMapPlugin("multimap", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUid(1000).withPrimaryGid(1000))
                .withResult(SUCCESS)
            .withAccountPhase()
                .withResult(SUCCESS)
            .withSessionPhase()
                .withResult(SUCCESS)));

        assertTrue(predicate.test(observation));

        given(aLoginResultObservation().withResult(aLoginResult()
            .withValidationResult(SUCCESS)
            .withAuthPhase()
                .with(anAuthPlugin("oidc", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUsername("tigran"))
                .withResult(SUCCESS)
            .withMapPhase()
                .with(aMapPlugin("multimap", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUid(1000).withPrimaryGid(1000))
                .withResult(SUCCESS)
            .withAccountPhase()
                .withResult(SUCCESS)
            .withSessionPhase()
                .withResult(SUCCESS)));

        assertTrue(predicate.test(observation));

        given(aLoginResultObservation().withResult(aLoginResult()
            .withValidationResult(SUCCESS)
            .withAuthPhase()
                .with(anAuthPlugin("oidc", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUsername("paul"))
                .withResult(SUCCESS)
            .withMapPhase()
                .with(aMapPlugin("multimap", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUid(2000).withPrimaryGid(1000))
                .withResult(SUCCESS)
            .withAccountPhase()
                .withResult(SUCCESS)
            .withSessionPhase()
                .withResult(SUCCESS)));

        assertTrue(predicate.test(observation));

        given(aLoginResultObservation().withResult(aLoginResult()
            .withValidationResult(SUCCESS)
            .withAuthPhase()
                .with(anAuthPlugin("oidc", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUsername("tigran"))
                .withResult(SUCCESS)
            .withMapPhase()
                .with(aMapPlugin("multimap", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUid(2000).withPrimaryGid(1000))
                .withResult(SUCCESS)
            .withAccountPhase()
                .withResult(SUCCESS)
            .withSessionPhase()
                .withResult(SUCCESS)));

        assertFalse(predicate.test(observation));
    }

    @Test
    public void shouldSupportOrAnd() {
        var predicate = whenParsing("groupname:it || groupname:atlas && uid:1000");

        given(aLoginResultObservation().withResult(aLoginResult()
            .withValidationResult(SUCCESS)
            .withAuthPhase()
                .with(anAuthPlugin("oidc", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUsername("paul").withGroupname("it"))
                .withResult(SUCCESS)
            .withMapPhase()
                .with(aMapPlugin("multimap", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUid(2000).withPrimaryGid(1000))
                .withResult(SUCCESS)
            .withAccountPhase()
                .withResult(SUCCESS)
            .withSessionPhase()
                .withResult(SUCCESS)));

        assertTrue(predicate.test(observation));

        given(aLoginResultObservation().withResult(aLoginResult()
            .withValidationResult(SUCCESS)
            .withAuthPhase()
                .with(anAuthPlugin("oidc", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUsername("paul").withGroupname("atlas"))
                .withResult(SUCCESS)
            .withMapPhase()
                .with(aMapPlugin("multimap", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUid(1000).withPrimaryGid(1000))
                .withResult(SUCCESS)
            .withAccountPhase()
                .withResult(SUCCESS)
            .withSessionPhase()
                .withResult(SUCCESS)));

        assertTrue(predicate.test(observation));

        given(aLoginResultObservation().withResult(aLoginResult()
            .withValidationResult(SUCCESS)
            .withAuthPhase()
                .with(anAuthPlugin("oidc", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUsername("paul").withGroupname("atlas"))
                .withResult(SUCCESS)
            .withMapPhase()
                .with(aMapPlugin("multimap", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUid(2000).withPrimaryGid(1000))
                .withResult(SUCCESS)
            .withAccountPhase()
                .withResult(SUCCESS)
            .withSessionPhase()
                .withResult(SUCCESS)));

        assertFalse(predicate.test(observation));
    }

    @Test
    public void shouldSupportAndOr() {
        var predicate = whenParsing("groupname:atlas && uid:1000 || groupname:it");

        given(aLoginResultObservation().withResult(aLoginResult()
            .withValidationResult(SUCCESS)
            .withAuthPhase()
                .with(anAuthPlugin("oidc", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUsername("paul").withGroupname("it"))
                .withResult(SUCCESS)
            .withMapPhase()
                .with(aMapPlugin("multimap", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUid(2000).withPrimaryGid(1000))
                .withResult(SUCCESS)
            .withAccountPhase()
                .withResult(SUCCESS)
            .withSessionPhase()
                .withResult(SUCCESS)));

        assertTrue(predicate.test(observation));

        given(aLoginResultObservation().withResult(aLoginResult()
            .withValidationResult(SUCCESS)
            .withAuthPhase()
                .with(anAuthPlugin("oidc", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUsername("paul").withGroupname("atlas"))
                .withResult(SUCCESS)
            .withMapPhase()
                .with(aMapPlugin("multimap", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUid(1000).withPrimaryGid(1000))
                .withResult(SUCCESS)
            .withAccountPhase()
                .withResult(SUCCESS)
            .withSessionPhase()
                .withResult(SUCCESS)));

        assertTrue(predicate.test(observation));

        given(aLoginResultObservation().withResult(aLoginResult()
            .withValidationResult(SUCCESS)
            .withAuthPhase()
                .with(anAuthPlugin("oidc", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUsername("paul").withGroupname("atlas"))
                .withResult(SUCCESS)
            .withMapPhase()
                .with(aMapPlugin("multimap", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUid(2000).withPrimaryGid(1000))
                .withResult(SUCCESS)
            .withAccountPhase()
                .withResult(SUCCESS)
            .withSessionPhase()
                .withResult(SUCCESS)));

        assertFalse(predicate.test(observation));
    }

    @Test
    public void shouldSupportBracketsOrThenAnd() {
        var predicate = whenParsing("(groupname:it || groupname:atlas) && uid:1000");

        given(aLoginResultObservation().withResult(aLoginResult()
            .withValidationResult(SUCCESS)
            .withAuthPhase()
                .with(anAuthPlugin("oidc", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUsername("paul").withGroupname("it"))
                .withResult(SUCCESS)
            .withMapPhase()
                .with(aMapPlugin("multimap", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUid(1000).withPrimaryGid(1000))
                .withResult(SUCCESS)
            .withAccountPhase()
                .withResult(SUCCESS)
            .withSessionPhase()
                .withResult(SUCCESS)));

        assertTrue(predicate.test(observation));

        given(aLoginResultObservation().withResult(aLoginResult()
            .withValidationResult(SUCCESS)
            .withAuthPhase()
                .with(anAuthPlugin("oidc", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUsername("paul").withGroupname("atlas"))
                .withResult(SUCCESS)
            .withMapPhase()
                .with(aMapPlugin("multimap", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUid(1000).withPrimaryGid(1000))
                .withResult(SUCCESS)
            .withAccountPhase()
                .withResult(SUCCESS)
            .withSessionPhase()
                .withResult(SUCCESS)));

        assertTrue(predicate.test(observation));

        given(aLoginResultObservation().withResult(aLoginResult()
            .withValidationResult(SUCCESS)
            .withAuthPhase()
                .with(anAuthPlugin("oidc", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUsername("paul").withGroupname("other"))
                .withResult(SUCCESS)
            .withMapPhase()
                .with(aMapPlugin("multimap", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUid(1000).withPrimaryGid(1000))
                .withResult(SUCCESS)
            .withAccountPhase()
                .withResult(SUCCESS)
            .withSessionPhase()
                .withResult(SUCCESS)));

        assertFalse(predicate.test(observation));

        given(aLoginResultObservation().withResult(aLoginResult()
            .withValidationResult(SUCCESS)
            .withAuthPhase()
                .with(anAuthPlugin("oidc", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUsername("paul").withGroupname("it"))
                .withResult(SUCCESS)
            .withMapPhase()
                .with(aMapPlugin("multimap", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUid(2000).withPrimaryGid(1000))
                .withResult(SUCCESS)
            .withAccountPhase()
                .withResult(SUCCESS)
            .withSessionPhase()
                .withResult(SUCCESS)));

        assertFalse(predicate.test(observation));

        given(aLoginResultObservation().withResult(aLoginResult()
            .withValidationResult(SUCCESS)
            .withAuthPhase()
                .with(anAuthPlugin("oidc", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUsername("paul").withGroupname("atlas"))
                .withResult(SUCCESS)
            .withMapPhase()
                .with(aMapPlugin("multimap", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUid(2000).withPrimaryGid(1000))
                .withResult(SUCCESS)
            .withAccountPhase()
                .withResult(SUCCESS)
            .withSessionPhase()
                .withResult(SUCCESS)));

        assertFalse(predicate.test(observation));
    }

    @Test
    public void shouldSupportNotBracketsOrThenAnd() {
        var predicate = whenParsing("!(groupname:it || groupname:atlas) && uid:1000");

        given(aLoginResultObservation().withResult(aLoginResult()
            .withValidationResult(SUCCESS)
            .withAuthPhase()
                .with(anAuthPlugin("oidc", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUsername("paul").withGroupname("other"))
                .withResult(SUCCESS)
            .withMapPhase()
                .with(aMapPlugin("multimap", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUid(1000).withPrimaryGid(1000))
                .withResult(SUCCESS)
            .withAccountPhase()
                .withResult(SUCCESS)
            .withSessionPhase()
                .withResult(SUCCESS)));

        assertTrue(predicate.test(observation));

        given(aLoginResultObservation().withResult(aLoginResult()
            .withValidationResult(SUCCESS)
            .withAuthPhase()
                .with(anAuthPlugin("oidc", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUsername("paul").withGroupname("atlas"))
                .withResult(SUCCESS)
            .withMapPhase()
                .with(aMapPlugin("multimap", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUid(1000).withPrimaryGid(1000))
                .withResult(SUCCESS)
            .withAccountPhase()
                .withResult(SUCCESS)
            .withSessionPhase()
                .withResult(SUCCESS)));

        assertFalse(predicate.test(observation));

        given(aLoginResultObservation().withResult(aLoginResult()
            .withValidationResult(SUCCESS)
            .withAuthPhase()
                .with(anAuthPlugin("oidc", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUsername("paul").withGroupname("it"))
                .withResult(SUCCESS)
            .withMapPhase()
                .with(aMapPlugin("multimap", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUid(1000).withPrimaryGid(1000))
                .withResult(SUCCESS)
            .withAccountPhase()
                .withResult(SUCCESS)
            .withSessionPhase()
                .withResult(SUCCESS)));

        assertFalse(predicate.test(observation));

        given(aLoginResultObservation().withResult(aLoginResult()
            .withValidationResult(SUCCESS)
            .withAuthPhase()
                .with(anAuthPlugin("oidc", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUsername("paul").withGroupname("other"))
                .withResult(SUCCESS)
            .withMapPhase()
                .with(aMapPlugin("multimap", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUid(2000).withPrimaryGid(1000))
                .withResult(SUCCESS)
            .withAccountPhase()
                .withResult(SUCCESS)
            .withSessionPhase()
                .withResult(SUCCESS)));

        assertFalse(predicate.test(observation));

        given(aLoginResultObservation().withResult(aLoginResult()
            .withValidationResult(SUCCESS)
            .withAuthPhase()
                .with(anAuthPlugin("oidc", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUsername("paul").withGroupname("atlas"))
                .withResult(SUCCESS)
            .withMapPhase()
                .with(aMapPlugin("multimap", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUid(2000).withPrimaryGid(1000))
                .withResult(SUCCESS)
            .withAccountPhase()
                .withResult(SUCCESS)
            .withSessionPhase()
                .withResult(SUCCESS)));

        assertFalse(predicate.test(observation));

        given(aLoginResultObservation().withResult(aLoginResult()
            .withValidationResult(SUCCESS)
            .withAuthPhase()
                .with(anAuthPlugin("oidc", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUsername("paul").withGroupname("it"))
                .withResult(SUCCESS)
            .withMapPhase()
                .with(aMapPlugin("multimap", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUid(2000).withPrimaryGid(1000))
                .withResult(SUCCESS)
            .withAccountPhase()
                .withResult(SUCCESS)
            .withSessionPhase()
                .withResult(SUCCESS)));

        assertFalse(predicate.test(observation));
    }

    @Test
    public void shouldMatchGlobUsername() {
        var predicate = whenParsing("username~p*");

        given(aLoginResultObservation().withResult(aLoginResult()
            .withValidationResult(SUCCESS)
            .withAuthPhase()
                .with(anAuthPlugin("oidc", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUsername("paul"))
                .withResult(SUCCESS)
            .withMapPhase()
                .with(aMapPlugin("multimap", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUid(1000).withPrimaryGid(1000))
                .withResult(SUCCESS)
            .withAccountPhase()
                .withResult(SUCCESS)
            .withSessionPhase()
                .withResult(SUCCESS)));

        assertTrue(predicate.test(observation));

        given(aLoginResultObservation().withResult(aLoginResult()
            .withValidationResult(SUCCESS)
            .withAuthPhase()
                .with(anAuthPlugin("oidc", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUsername("patrick"))
                .withResult(SUCCESS)
            .withMapPhase()
                .with(aMapPlugin("multimap", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUid(1000).withPrimaryGid(1000))
                .withResult(SUCCESS)
            .withAccountPhase()
                .withResult(SUCCESS)
            .withSessionPhase()
                .withResult(SUCCESS)));

        assertTrue(predicate.test(observation));

        given(aLoginResultObservation().withResult(aLoginResult()
            .withValidationResult(SUCCESS)
            .withAuthPhase()
                .with(anAuthPlugin("oidc", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUsername("tigran"))
                .withResult(SUCCESS)
            .withMapPhase()
                .with(aMapPlugin("multimap", OPTIONAL).withSuccess())
                .thatAdds(aSetOfPrincipals().withUid(1000).withPrimaryGid(1000))
                .withResult(SUCCESS)
            .withAccountPhase()
                .withResult(SUCCESS)
            .withSessionPhase()
                .withResult(SUCCESS)));

        assertFalse(predicate.test(observation));
    }

    private void given(LoginResultObservationBuilder builder) {
        observation = builder.build();
    }

    private Predicate<LoginResultObservation> whenParsing(String argument) {
        var result = runner.run(argument);

        if (!result.isSuccess()) {
            var errors = result.getParseErrors().stream()
                .map(e -> describe(e, argument))
                .collect(Collectors.joining("\n\n"));
            throw new AssertionError("Parsing of \"" + argument + "\" failed with errors:\n"
                + errors);
        }

        var predicate = result.getTopStackValue();

        assertThat(predicate, not(nullValue()));

        return predicate;
    }

    private static String describe(ParseError e, String argument) {
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(e.getStartIndex()).append(":").append(e.getEndIndex()).append(']');
        sb.append(" --> ").append(e.getErrorMessage());
        if (e instanceof InvalidInputError) {
            InvalidInputError iie = (InvalidInputError) e;
            sb.append(iie.getFailedMatchers().stream()
                .map(mp -> describe(mp, argument))
                .collect(Collectors.joining("\n", "\nFailed-matchers:", "\n")));
        }
        return sb.toString();
    }

    private static String describe(MatcherPath mp, String argument) {
        ColumnWriter writer = new ColumnWriter();
        writer
            .header("Count").right("LEVEL")
            .space()
            .header("Label").left("LABEL")
            .space()
            .header("Input").left("INPUT");
        return "\n" + describeRecursive(writer, mp, argument).toString();
    }

    private static ColumnWriter describeRecursive(ColumnWriter writer, MatcherPath mp, String argument) {
        if (mp.hasParent()) {
            describeRecursive(writer, mp.getParent(), argument);
        }
        MatcherPath.Element e = mp.getElement();
        writer.row()
            .value("LEVEL", e.getLevel()+1)
            .value("LABEL", e.getMatcher())
            .value("INPUT", appendStringIndex(e.getStartIndex(), argument));

        return writer;
    }

    private static String appendStringIndex(int index, String argument) {
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, index-8);

        if (start < index) {
            sb.append("[");
            if (start > 0) {
                sb.append("...");
            }
            int preStart = Math.min(start, argument.length()-1);
            int preEnd = Math.min(index, argument.length()-1);
            sb.append(argument.substring(preStart, preEnd));
            sb.append("]");
        }

        if (index < argument.length()-1) {
            if (start < index) {
                sb.append(' ');
            }
            int end = Math.min(index+8, argument.length()-1);
            sb.append(argument.substring(index, end));
            if (end < argument.length()-1) {
                sb.append("...");
            }
        }

        return sb.toString();
    }
}