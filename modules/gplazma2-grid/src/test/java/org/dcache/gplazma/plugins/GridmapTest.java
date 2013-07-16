package org.dcache.gplazma.plugins;

import com.google.common.collect.Sets;
import org.globus.gsi.jaas.GlobusPrincipal;
import org.hamcrest.Matcher;
import org.junit.Test;

import java.io.StringReader;
import java.security.Principal;
import java.util.Set;

import org.dcache.auth.FQANPrincipal;
import org.dcache.auth.GroupNamePrincipal;
import org.dcache.auth.UidPrincipal;
import org.dcache.auth.UserNamePrincipal;
import org.dcache.gplazma.AuthenticationException;
import org.dcache.gplazma.util.GridMapFile;

import static org.dcache.gplazma.plugins.PrincipalSetMaker.aSetOfPrincipals;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.Assert.assertThat;

public class GridmapTest
{
    private String _contents;
    private Set<Principal> _principals;

    @Test(expected=NullPointerException.class)
    public void shouldThrowNPEWhenGivenNullArgs() throws AuthenticationException
    {
        given(gridmapFile().thatIsEmpty());

        whenMapPluginCalledWith((Set<Principal>) null);
    }


    @Test
    public void shouldAddUserWhenDnMatches()
        throws AuthenticationException
    {
        given(gridmapFile().withLine("\"/O=ACME/CN=Wile E Coyote\" wile"));

        whenMapPluginCalledWith(aSetOfPrincipals().withDn("/O=ACME/CN=Wile E Coyote"));

        assertThat(_principals, hasDn("/O=ACME/CN=Wile E Coyote"));
        assertThat(_principals, hasUserName("wile"));
    }


    @Test
    public void shouldIgnoreCommentAndAdditionalWhiteSpaceAndEmptyLines()
        throws AuthenticationException
    {
        given(gridmapFile().withLines(
                "",
                "#  This is a comment, which should be ignored",
                "",
                "\"/O=ACME/CN=Wile E Coyote\" \t wile \t ",
                "# this should be ignored, too"));

        whenMapPluginCalledWith(aSetOfPrincipals().withDn("/O=ACME/CN=Wile E Coyote"));

        assertThat(_principals, hasDn("/O=ACME/CN=Wile E Coyote"));
        assertThat(_principals, hasUserName("wile"));
    }


    @Test
    public void shouldSelectFirstLine()
        throws AuthenticationException
    {
        given(gridmapFile().withLines(
                "\"/O=ACME/CN=Wile E Coyote\" wile",
                "\"/O=ACME/CN=Wile E Coyote\" acme"));

        whenMapPluginCalledWith(aSetOfPrincipals().withDn("/O=ACME/CN=Wile E Coyote"));

        assertThat(_principals, hasDn("/O=ACME/CN=Wile E Coyote"));
        assertThat(_principals, hasUserName("wile"));
    }



    @Test(expected=AuthenticationException.class)
    public void shouldNotMatchWhenDnDoesNotMatch()
        throws AuthenticationException
    {
        given(gridmapFile().withLine("\"/O=ACME/CN=Wile E Coyote\" wile"));

        whenMapPluginCalledWith(aSetOfPrincipals().withDn("/O=ACME/CN=Road Runner"));
    }


    public static Matcher<Iterable<? super GlobusPrincipal>> hasDn(String dn)
    {
        return hasItem(new GlobusPrincipal(dn));
    }

    public static Matcher<Iterable<? super UidPrincipal>> hasUid(int uid)
    {
        return hasItem(new UidPrincipal(uid));
    }

    public static Matcher<Iterable<? super FQANPrincipal>>
            hasFqan(String fqan)
    {
        return hasItem(new FQANPrincipal(fqan));
    }

    public static Matcher<Iterable<? super FQANPrincipal>>
            hasPrimaryFqan(String fqan)
    {
        return hasItem(new FQANPrincipal(fqan, true));
    }

    public static Matcher<Iterable<? super GroupNamePrincipal>>
            hasGroupName(String group)
    {
        return hasItem(new GroupNamePrincipal(group));
    }

    public static Matcher<Iterable<? super UserNamePrincipal>>
            hasUserName(String group)
    {
        return hasItem(new UserNamePrincipal(group));
    }

    public static Matcher<Iterable<? super GroupNamePrincipal>>
            hasPrimaryGroupName(String group)
    {
        return hasItem(new GroupNamePrincipal(group, true));
    }


    private void whenMapPluginCalledWith(PrincipalSetMaker maker)
            throws AuthenticationException
    {
        Set<Principal> principals = Sets.newHashSet(maker.build());
        whenMapPluginCalledWith(principals);
    }

    private void whenMapPluginCalledWith(Set<Principal> principals)
            throws AuthenticationException
    {
        _principals = principals;
        newGridMapFilePlugin(_contents).map(_principals);
    }

    private static GridMapFilePlugin newGridMapFilePlugin(String source)
    {
        GridMapFile gridmap = new GridMapFile(new StringReader(source)) {
            @Override
            public void refresh()
            {
                // do nothing
            }
        };
        return new GridMapFilePlugin(gridmap);
    }

    private void given(GridmapFileMaker maker)
    {
        _contents = maker.build();
    }

    private GridmapFileMaker gridmapFile()
    {
        return new GridmapFileMaker();
    }

    /** A builder for the content of a GridmapFile with a fluent interface. */
    private class GridmapFileMaker
    {
        private final StringBuilder _lines = new StringBuilder();

        public GridmapFileMaker thatIsEmpty()
        {
            return this;
        }

        public GridmapFileMaker withLine(String line)
        {
            _lines.append(line).append('\n');
            return this;
        }

        public GridmapFileMaker withLines(String... lines)
        {
            for (String line : lines) {
                _lines.append(line).append('\n');
            }
            return this;
        }

        private String build()
        {
            return _lines.toString();
        }
   }

}
