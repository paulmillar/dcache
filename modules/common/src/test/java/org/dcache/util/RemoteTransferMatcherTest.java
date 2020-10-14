/* dCache - http://www.dcache.org/
 *
 * Copyright (C) 2020 Deutsches Elektronen-Synchrotron
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
package org.dcache.util;

import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RemoteTransferMatcherTest
{
    private RemoteTransferMatcher matcher;

    @Test
    public void shouldReturnEmptyListWhenNoRules()
    {
        givenEmptyMatcher();

        List<String> rules = matcher.listRules();

        assertThat(rules, is(empty()));
    }

    @Test
    public void shouldNotMatchWhenNoRules()
    {
        givenEmptyMatcher();

        boolean isMatch = matcher.matches("/path", URI.create("https://remote.example.org/path/to/remote-file"));

        assertFalse(isMatch);
    }

    @Test
    public void shouldAllowAddingRuleToEmptyMatcher()
    {
        givenEmptyMatcher();

        add(aRule().withLocalPath("/path"));

        assertThat(matcher.listRules(), hasSize(1));
        assertTrue(matcher.matches("/path", URI.create("https://remote.example.org/path/to/remote-file")));
        assertFalse(matcher.matches("/another-file", URI.create("https://remote.example.org/path/to/remote-file")));
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void shouldFailToRemoveOutOfIndexRuleForEmptyMatcher()
    {
        givenEmptyMatcher();

        matcher.removeRule(0);
    }

    @Test(expected=IllegalArgumentException.class)
    public void shouldFailToAddBlanketRule()
    {
        givenEmptyMatcher();

        add(aRule());
    }

    @Test(expected=IllegalArgumentException.class)
    public void shouldFailToAddRuleWithBadCount()
    {
        givenEmptyMatcher();

        add(aRule().withCount(-1).withLocalPath("/local-path"));
    }

    @Test
    public void shouldAllowRemovingRule()
    {
        givenMatcherWith(aRule().withLocalPath("/path"));

        matcher.removeRule(0);

        assertThat(matcher.listRules(), is(empty()));
        assertFalse(matcher.matches("/path", URI.create("https://remote.example.org/path/to/remote-file")));
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void shouldFailToRemoveOutOfIndexRuleForSingleRuleMatcher()
    {
        givenMatcherWith(aRule().withLocalPath("/path"));

        matcher.removeRule(1);
    }

    @Test
    public void shouldMatchSingleLocalPathWildcardRule()
    {
        givenMatcherWith(aRule().withLocalPath("/*path"));

        URI uri = URI.create("https://remote.example.org/path/to/remote-file");

        assertTrue(matcher.matches("/path", uri));
        assertTrue(matcher.matches("/localpath", uri));
        assertFalse(matcher.matches("/localpath-other", uri));
    }

    @Test
    public void shouldMatchSingleHostWildcardRule()
    {
        givenMatcherWith(aRule().withHost("*interest.example.org"));

        assertTrue(matcher.matches("/path", URI.create("https://interest.example.org/path/to/remote-file")));
        assertTrue(matcher.matches("/path", URI.create("https://service-of-interest.example.org/path/to/remote-file")));
        assertFalse(matcher.matches("/path", URI.create("https://remote.example.org/path/to/remote-file")));
    }

    @Test
    public void shouldMatchSingleRemotePathWildcardRule()
    {
        givenMatcherWith(aRule().withRemotePath("/path/to/*file"));

        assertTrue(matcher.matches("/path", URI.create("https://example.org/path/to/file")));
        assertTrue(matcher.matches("/path", URI.create("https://example.org/path/to/remote-file")));
        assertFalse(matcher.matches("/path", URI.create("https://remote.example.org/another/path/to/file")));
        assertFalse(matcher.matches("/path", URI.create("https://remote.example.org/path/to/file.jpg")));
    }

    @Test
    public void shouldMatchSingleWildcardRule()
    {
        givenMatcherWith(aRule().withLocalPath("/*path").withHost("service*.example.org").withRemotePath("/path/to/*file"));

        assertTrue(matcher.matches("/path", URI.create("https://service.example.org/path/to/file")));
        assertTrue(matcher.matches("/local-path", URI.create("https://service-remote.example.org/path/to/remote-file")));

        assertFalse(matcher.matches("/path-local", URI.create("https://service-remote.example.org/path/to/file")));
        assertFalse(matcher.matches("/path", URI.create("https://my-service.example.org/path/to/file")));
        assertFalse(matcher.matches("/path", URI.create("https://service.example.org/some/other/file")));
    }

    @Test
    public void shouldMatchTwoWildcardRules()
    {
        givenMatcherWith(
                aRule().withLocalPath("/*path"),
                aRule().withHost("service*.example.org"));

        assertThat(matcher.listRules(), hasSize(2));

        assertTrue(matcher.matches("/path", URI.create("https://example.org/path/to/remote-file")));
        assertTrue(matcher.matches("/local-path", URI.create("https://example.org/path/to/remote-file")));

        assertTrue(matcher.matches("/some/other/file", URI.create("https://service.example.org/path/to/remote-file")));
        assertTrue(matcher.matches("/some/other/file", URI.create("https://service-of-interest.example.org/path/to/remote-file")));

        assertFalse(matcher.matches("/some/other/file", URI.create("https://example.org/path/to/remote-file")));
    }

    @Test
    public void shouldMatchSingleShotRule()
    {
        givenMatcherWith(aRule().withCount(1).withLocalPath("/*path"));

        boolean isMatch = matcher.matches("/path", URI.create("https://example.org/path/to/remote-file"));

        assertTrue(isMatch);
        assertThat(matcher.listRules(), is(empty()));
        assertFalse(matcher.matches("/path", URI.create("https://example.org/path/to/remote-file")));
    }

    @Test
    public void shouldMatchTwoMatchRule()
    {
        givenMatcherWith(aRule().withCount(2).withLocalPath("/*path"));

        boolean isMatch = matcher.matches("/path", URI.create("https://example.org/path/to/remote-file"));

        assertTrue(isMatch);
        assertThat(matcher.listRules(), hasSize(1));
    }

    @Test
    public void shouldMatchTwoMatchRuleWithAHit()
    {
        givenMatcherWith(aRule().withCount(2).withLocalPath("/*path").withExistingHits(1));

        boolean isMatch = matcher.matches("/path", URI.create("https://example.org/path/to/remote-file"));

        assertTrue(isMatch);
        assertThat(matcher.listRules(), is(empty()));
    }

    @Test
    public void shouldMatchTwoMatchRuleWithTwoHits()
    {
        givenMatcherWith(aRule().withCount(2).withLocalPath("/*path").withExistingHits(2));

        boolean isMatch = matcher.matches("/path", URI.create("https://example.org/path/to/remote-file"));

        assertFalse(isMatch);
    }

    private void givenEmptyMatcher()
    {
        matcher = new RemoteTransferMatcher();
    }

    private void givenMatcherWith(RuleBuilder...rules)
    {
        matcher = new RemoteTransferMatcher();
        Arrays.stream(rules).forEach(this::add);
    }

    private void add(RuleBuilder rule)
    {
        matcher.addRule(rule.count, rule.localPath, rule.host, rule.remotePath);

        for (int i = 0; i < rule.hits; i++) {
            if (!matcher.matches(rule.matchingLocalPath(), rule.matchingURI())) {
                List<String> rules = matcher.listRules();
                String description = rules.get(rules.size()-1);
                throw new RuntimeException("Failed to match rule [" + description
                        + "] with localPath=" + rule.matchingLocalPath()
                        + ", URI=" + rule.matchingURI());
            }
        }
    }

    private static RuleBuilder aRule()
    {
        return new RuleBuilder();
    }

    private static class RuleBuilder
    {
        private int count;
        private int hits;
        private Optional<Glob> localPath = Optional.empty();
        private Optional<Glob> host = Optional.empty();
        private Optional<Glob> remotePath = Optional.empty();

        RuleBuilder withCount(int count)
        {
            this.count = count;
            return this;
        }

        RuleBuilder withHost(String p)
        {
            host = Optional.of(new Glob(p));
            return this;
        }

        RuleBuilder withRemotePath(String p)
        {
            remotePath = Optional.of(new Glob(p));
            return this;
        }

        RuleBuilder withLocalPath(String p)
        {
            localPath = Optional.of(new Glob(p));
            return this;
        }

        RuleBuilder withExistingHits(int hits)
        {
            this.hits = hits;
            return this;
        }

        // Note that matchingHost, matchingRemotePath and matchingLocalPath
        // methods use the fact that a '*' in a glob will match any string,
        // including the '*' character.

        /**
         * Return a String that matches the host criterion.
         */
        String matchingHost()
        {
            return host.map(Object::toString).orElse("example.org");
        }

        /**
         * Return a String that matches the remotePath criterion.
         */
        String matchingRemotePath()
        {
            return absolutePathOf(remotePath.orElse(Glob.ANY).toString());
        }

        /**
         * Return a String that matches the localPath criterion.
         */
        String matchingLocalPath()
        {
            return absolutePathOf(localPath.orElse(Glob.ANY).toString());
        }

        /**
         * Ensure path is absolute by prepending a '/' if necessary.
         */
        private String absolutePathOf(String value)
        {
            return value.charAt(0) == '/' ?  value : ("/" + value);
        }

        /**
         * A URI that matches the host and remotePath criteria.
         */
        URI matchingURI()
        {
            try {
                return new URI("https", null, matchingHost(), -1,
                        matchingRemotePath(), null, null);
            } catch (URISyntaxException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
    }
}
