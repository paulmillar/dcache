package org.dcache.gplazma.plugins;

import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.dcache.auth.FQAN;
import org.dcache.gplazma.util.NameRolePair;
import org.dcache.util.Glob;

import static com.google.common.base.Objects.equal;

/**
 * Parse a vorolemap file.
 *
 * Leading and trailing whitespace in lines is ignored.  Valid lines are either
 * empty, comments or are a mapping line.  Comment lines start with a hash
 * symbol.
 *
 * Mapping lines have three parts: a predicate, a group name and an optional
 * comment.  Each part is separated by white space.  The optional comment
 * part must start with a hash symbol.
 *
 * In a mapping line, the predicate part is either a DN glob or a (DN glob,
 * FQAN literal) pair with the glob and literal separated by whitespace.  Both
 * the DN glob and FQAN literal, if present, may be placed in double-quote
 * characters.
 *
 * The group name is a sequence of alphanumeric characters.
 *
 * In summary:
 *
 * {@literal
 *     <dn-glob or quoted-dn-glob> [<fqan or quoted-fqan>] <group> [# comment]
 * }
 *
 * The parser provides a predicate for each valid mapping line.  The predicate
 * accepts a NameRolePair object and, if it matches, returns the corresponding
 * group.
 *
 * A supplied NameRolePair matches a mapping line's predicate if the DN from the
 * NameRolePair matches DN glob and either the normalised FQAN from the
 * NameRolePair is the normalised FQAN literal or the FQAN from the
 * NameRolePair is null and the mapping line has no FQAN.
 *
 * The empty FQAN (written {@literal ""}) is treated as if no FQAN was supplied,
 * so the following two mapping lines are equivalent:
 *
 * {@literal
 *     "/O=ACME/CN=Wile E Coyote"  ""  genius
 *     "/O=ACME/CN=Wile E Coyote"      genius
 * }
 *
 * A normalised FQAN is one that has any /Role=NULL or /Capability=NULL removed.
 */
class VOMapLineParser
    implements LineParser<VOMapLineParser.DNFQANPredicate, String>
{
    private static final Logger _log =
            LoggerFactory.getLogger(VOMapLineParser.class);

    private static final String SOME_WS = "\\s+";
    private static final String QUOTED_TERM = "\"[^\"]*\"";
    private static final String UNQUOTED_DN = "(?:/[\\w\\d\\s,;:@\\-\\*\\.=]+)+";
    private static final String DN = "\\*|(?:"+ UNQUOTED_DN +")|(?:"+ QUOTED_TERM + ")";
    private static final String UNQUOTED_FQAN = "(?:/[\\w\\d,;:@\\-\\*\\.]+)+(?:/[\\w\\d\\s,;:@\\-\\*=]+)*";
    private static final String FQAN = "(?:" + UNQUOTED_FQAN +")|(?:"+ QUOTED_TERM + ")";
    private static final String GROUPNAME = "[\\w.][\\w.\\-]*";
    private static final String COMMENT = "#.*";

    private static final Pattern ROLE_MAP_FILE_LINE_PATTERN =
        Pattern.compile("("+ DN +")" +
                        "(?:" + SOME_WS + "("+ FQAN +"))?" +
                        SOME_WS + "("+ GROUPNAME +")" +
                         "(?:"+SOME_WS+")?" + "(?:" + COMMENT + ")?");

    private static final int MATCHER_GROUP_DN = 1;
    private static final int MATCHER_GROUP_FQAN = 2;
    private static final int MATCHER_GROUP_GROUPNAME = 3;

    @Override
    public Map.Entry<DNFQANPredicate, String> accept(String rawLine)
    {
        String line = rawLine.trim();

        if (Strings.isNullOrEmpty(line) || line.startsWith("#")) {
            return null;
        }

        Matcher matcher = ROLE_MAP_FILE_LINE_PATTERN.matcher(line);

        if (!matcher.matches()) {
            _log.warn("Ignored malformed line in VORoleMap-File: '{}'", line);
            return null;
        }

        String dn = matcher.group(MATCHER_GROUP_DN); // never null
        String fqan = matcher.group(MATCHER_GROUP_FQAN); // can be null
        String group = matcher.group(MATCHER_GROUP_GROUPNAME); // never null

        DNFQANPredicate predicate =
                buildDnFqanPredicate(withoutQuotes(dn), withoutQuotes(fqan));

        return new DNFQANStringEntry(predicate, group);
    }

    private DNFQANPredicate buildDnFqanPredicate(String dn, String fqan)
    {
        if(fqan != null && fqan.isEmpty()) {
            fqan = null;
        }

        return new DNFQANPredicate(dn, normaliseFqan(fqan));
    }


    private static String withoutQuotes(String arg)
    {
        if(arg == null) {
            return null;
        }

        String withoutQuotes;

        if(arg.charAt(0) == '\"' && arg.charAt(arg.length()-1) == '\"') {
            withoutQuotes = arg.substring(1, arg.length()-1);
        } else {
            withoutQuotes = arg;
        }

        return withoutQuotes;
    }


    /**
     * A predicate against a NameRolePair.
     */
    static class DNFQANPredicate implements MapPredicate<NameRolePair>
    {
        private final String _fqan;
        private final Pattern _dn;

        public DNFQANPredicate(String dn, String fqan) {
            _dn = Glob.parseGlobToPattern(dn);
            _fqan = fqan;
        }

        @Override
        public boolean matches(NameRolePair dnfqan) {
            String dn = dnfqan.getName();
            String fqan = normaliseFqan(dnfqan.getRole());

            return _dn.matcher(dn).matches() && equal(_fqan, fqan);
        }
    }

    private static String normaliseFqan(String role)
    {
        return role == null ? null : new FQAN(role).toString();
    }

    /**
     * This class represents a parsed line from the VoRoleMap file.
     */
    private final static class DNFQANStringEntry
        implements Map.Entry<DNFQANPredicate, String>
        {

        private final DNFQANPredicate _key;
        private String _value;

        public DNFQANStringEntry(DNFQANPredicate key, String value) {
            _key = key;
            _value = value;
        }

        @Override
        public DNFQANPredicate getKey() {
            return _key;
        }

        @Override
        public String getValue() {
            return _value;
        }

        @Override
        public String setValue(String value) {
            return _value = value;
        }
    }
}
