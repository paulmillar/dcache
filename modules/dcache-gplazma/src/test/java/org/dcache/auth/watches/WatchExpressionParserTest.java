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

import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.BeforeClass;
import org.parboiled.Parboiled;
import org.parboiled.parserunners.ReportingParseRunner;
import org.parboiled.support.ParsingResult;

public class WatchExpressionParserTest {

    private static WatchExpressionParser parser;

    private ReportingParseRunner runner;

    @BeforeClass
    public static void setupOnce() {
        parser = Parboiled.createParser(WatchExpressionParser.class);
    }

    @Before
    public void setup() {
        runner = new ReportingParseRunner(parser.input());
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
}