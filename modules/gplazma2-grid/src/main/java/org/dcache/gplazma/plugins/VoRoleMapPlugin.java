package org.dcache.gplazma.plugins;

import com.google.common.collect.Lists;
import org.globus.gsi.jaas.GlobusPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.Principal;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.dcache.auth.FQAN;
import org.dcache.auth.FQANPrincipal;
import org.dcache.auth.GroupNamePrincipal;
import org.dcache.gplazma.AuthenticationException;
import org.dcache.gplazma.util.NameRolePair;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.collect.Iterables.filter;
import static org.dcache.gplazma.util.Preconditions.checkAuthentication;

/**
 * If this plugin succeeds then it adds one or more GroupNamePrincipal objects
 * to the Principals set and zero or more FQAN and one or more GlobusPrincipal
 * object to the AuthorizedPrincipals set.  Therefore, to succeed, the plugin
 * must be provided with at least one GlobusPrincipal.
 *
 * The behavior of the plugin is driven by the vorolemap file.  Aside from
 * comments and empty lines, this file contains zero or more mapping lines.
 * Each mapping line describes a predicate and the name of a group.  The
 * predicate has one required part and one optional part: the required part is
 * a DN glob, the optional part is an FQAN literal.
 *
 * The plugin makes use of a logical "map" operation.  In this operation, each
 * line of the file is compared against the supplied criteria: either a DN or a
 * DN,FQAN pair.  A map operation succeeds iff there is at least one mapping
 * line in the vorolemap file that has a predicate that matches the supplied DN
 * or DN,FQAN pair.  If the map operation succeeds then the group from the
 * first mapping line (in document order) with a predicate that matches the
 * supplied DN or DN,FQAN pair is the mapped group.
 *
 * Another concept the plugin uses is the parent FQAN.  The parent FQAN of
 * a FQAN is one with the final element removed.  Some FQANs have no parent.
 * Here is an example that shows a sequence of FQANs where each FQAN (except
 * the first) is the parent of the immediately preceeding one, terminating with
 * an FQAN that has no parent: /cms/higgs/Role=production, /cms/higgs, /cms.
 *
 * The plugin provides the logic:
 *
 *   The primary FQAN, if present, is paired with each supplied DN in turn and
 *   mapped to a group; the DN order is not specific.  If none of these
 *   mappings succeed then the same procedure is attempted with the parent of
 *   the primary FQAN.  This continues with each iteration taking the parent
 *   FQAN of the previous iteration until there is at least one successful
 *   mapping or the previous iteration attempted to map an FQAN that has no
 *   parent FQAN.
 *
 *   Then the Cartesian product of DNs and non-primary FQANs is taken.  Each
 *   DN,FQAN pair is mapped.  The order of processing these pairs is not
 *   specified.  If there is no non-primary FQANs then this step is skipped.
 *
 *   Then each of the DNs is mapped individually.
 *
 *   If the subject does not already have a primary group then the first
 *   successful mapping defines the primary group and all subsequently mapped
 *   groups are non-primary.  If the subject already has a primary group then
 *   all mapped groups are non-primary.
 *
 *   The GroupNamePrincipal objects for all successful mappings are added to
 *   the principals set.  All DNs involved with successful mappings are added
 *   to the AuthorizedPrincipals set.  All FQANs involved with successful
 *   mappings are added to the AuthorizedPrincipals set.  The FQAN that
 *   mapped the primary group is made primary and all others are non-primary.
 *   If the same FQAN mapped the primary group and a non-primary group then it
 *   is primary in AuthorizedPrincipals.
 */
public class VoRoleMapPlugin implements GPlazmaMappingPlugin
{
    private static final Logger _log =
        LoggerFactory.getLogger(VoRoleMapPlugin.class);

    private static final long REFRESH_PERIOD =
        TimeUnit.SECONDS.toMillis(10);

    private static final String VOROLEMAP =
        "gplazma.vorolemap.file";

    private final SourceBackedPredicateMap<NameRolePair,String> _map;

    public VoRoleMapPlugin(Properties properties) throws IOException
    {
        String path = properties.getProperty(VOROLEMAP);

        checkArgument(path != null, "Undefined property: " + VOROLEMAP);

        LineSource source = new FileLineSource(path, REFRESH_PERIOD);
        _map = new SourceBackedPredicateMap<>(source, new VOMapLineParser());
    }

    /**
     * package visible constructor for testing purposes
     * @param voMapCache map of dnfqans to usernames
     */
    VoRoleMapPlugin(SourceBackedPredicateMap<NameRolePair,String> map)
    {
        _map = map;
    }


    private static boolean hasPrimaryGroupName(Set<Principal> principals)
    {
        for (GroupNamePrincipal p:
                filter(principals, GroupNamePrincipal.class)) {
            if (p.isPrimaryGroup()) {
                return true;
            }
        }
        return false;
    }

    private String groupNameMatching(GlobusPrincipal globusPrincipal, FQAN fqan)
    {
        String dn = globusPrincipal == null ? null : globusPrincipal.getName();
        String fqanString = fqan == null ? null : fqan.toString();

        NameRolePair dnAndFqan = new NameRolePair(dn, fqanString);

        List<String> names = _map.getValuesForPredicatesMatching(dnAndFqan);

        return names.isEmpty() ? null : names.get(0);
    }

    @Override
    public void map(Set<Principal> principals,
            Set<Principal> authorizedPrincipals) throws AuthenticationException
    {
        VoRoleMapRequest request = new VoRoleMapRequest(principals,
                authorizedPrincipals);
        request.process();
    }

    /**
     * Encapsulates a request to this plugin.
     */
    private class VoRoleMapRequest
    {
        private final Set<Principal> _principals;
        private final Set<Principal> _authorizedPrincipals;
        private final List<FQANPrincipal> _fqans;
        private final List<GlobusPrincipal> _dns;
        private boolean _hasPrimaryGroup;
        private boolean _authorized;

        private VoRoleMapRequest(Set<Principal> principals,
                Set<Principal> authorizedPrincipals)
                throws AuthenticationException
        {
            _principals = principals;
            _authorizedPrincipals = authorizedPrincipals;

            _fqans = Lists.newArrayList(filter(_principals, FQANPrincipal.class));
            _dns = Lists.newArrayList(filter(_principals, GlobusPrincipal.class));

            _hasPrimaryGroup = hasPrimaryGroupName(_authorizedPrincipals);
        }


        public void process() throws AuthenticationException
        {
            for (FQANPrincipal fqan: _fqans) {
                addAllGroupNamesFor(fqan);
            }

            addDnOnlyGroupNames();

            checkAuthentication(_authorized, "no record");
        }


        private void addAllGroupNamesFor(FQANPrincipal fqan)
        {
            boolean addAsPrimaryGroup =
                    fqan.isPrimaryGroup() && !_hasPrimaryGroup;

            if (addAsPrimaryGroup) {
                /*
                 * For the primary group, try very hard to find a match.
                 * This is because the primary group (usually) determines the
                 * primary gid and, without a primary gid, the login will fail.
                 *
                 * Note that FQANs form a hierarchy and the VOMS server always
                 * adds parent FQANs.  Therefore the parent FQANs of this FQAN
                 * are also present in the request, but will be non-primary.
                 * Also, since the order of processing FQANs isn't guaranteed,
                 * zero or more parent FQAN may have already been processed.
                 *
                 * Here, the FQAN parent hierarchy is walked from the primary
                 * FQAN in the direction of increasing generalisation.  The
                 * primary group is the first FQAN successfully mapped.  In the
                 * absense of a direct match, this probably provides the
                 * expected behaviour.
                 */
                for (FQAN f = fqan.getFqan(); f != null; f = f.getParent()) {
                    if (addGroupNamesFor(f, addAsPrimaryGroup)) {
                        break;
                     }
                 }
            } else {
                addGroupNamesFor(fqan.getFqan(), addAsPrimaryGroup);
            }
         }


        private void addDnOnlyGroupNames()
        {
            addGroupNamesFor((FQAN)null, !_hasPrimaryGroup);
        }


        private boolean addGroupNamesFor(FQAN fqan, boolean addAsPrimaryGroup)
        {
            boolean hasAddedPrincipals = false;

            for (GlobusPrincipal dn : _dns) {
                String name = groupNameMatching(dn, fqan);

                if (name != null) {
                    GroupNamePrincipal group =
                            new GroupNamePrincipal(name, addAsPrimaryGroup);

                    if (addMappingFor(dn, fqan, group)) {
                        addAsPrimaryGroup = false;
                        hasAddedPrincipals = true;
                    }
                }
            }

            return hasAddedPrincipals;
        }
        /**
         * Add Principals for FQAN, DN and GroupName.  Care must be taken
         * since the order that FQANs are processed is not guaranteed.
         * Therefore, adding a primary group may remove an existing
         * non-primary group principals with the same name and adding a
         * non-primary group will fail to add principals if a primary group is
         * already present.
         */
        private boolean addMappingFor(GlobusPrincipal dn, FQAN fqan,
                GroupNamePrincipal group)
        {
            boolean isPrimary = group.isPrimaryGroup();
            boolean shouldAddFQAN = fqan != null;

            if (isPrimary) {
                Principal nonprimaryGroup =
                        new GroupNamePrincipal(group.getName(), false);
                Principal nonprimaryFQAN =
                        fqan == null ? null : new FQANPrincipal(fqan, false);

                _principals.remove(nonprimaryGroup);
                _authorizedPrincipals.remove(nonprimaryFQAN);
            } else {
                Principal primaryGroup =
                        new GroupNamePrincipal(group.getName(), true);
                Principal primaryFQAN =
                        fqan == null ? null : new FQANPrincipal(fqan, true);

                if (_principals.contains(primaryGroup)) {
                    return false;
                }

                shouldAddFQAN &= !_authorizedPrincipals.contains(primaryFQAN);
            }

            _principals.add(group);
            _authorizedPrincipals.add(dn);
            if (shouldAddFQAN) {
                _authorizedPrincipals.add(new FQANPrincipal(fqan, isPrimary));
            }

            _authorized = true;
            _hasPrimaryGroup |= isPrimary;

            _log.debug("added group {} for DN: {} and FQAN: {}",
                    new Object[] {group, dn, fqan});

            return true;
        }
    }
}
