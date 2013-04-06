package org.dcache.gplazma.plugins;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import static com.google.common.base.Predicates.instanceOf;
import static com.google.common.collect.Iterables.filter;
import static com.google.common.collect.Iterables.transform;
import com.google.common.collect.Lists;
import static com.google.common.base.Preconditions.checkArgument;
import diskCacheV111.namespace.NameSpaceProvider;
import diskCacheV111.util.CacheException;
import diskCacheV111.util.FileNotFoundCacheException;
import diskCacheV111.util.FsPath;
import diskCacheV111.util.PnfsId;
import java.security.Principal;
import java.util.EnumSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import org.dcache.auth.GidPrincipal;
import static org.dcache.auth.Subjects.ROOT;
import org.dcache.auth.UidPrincipal;
import org.dcache.auth.UserNamePrincipal;
import org.dcache.auth.attributes.HomeDirectory;
import org.dcache.auth.attributes.ReadOnly;
import org.dcache.gplazma.AuthenticationException;
import static org.dcache.gplazma.util.Preconditions.checkAuthentication;
import static org.dcache.namespace.FileAttribute.TYPE;
import static org.dcache.namespace.FileAttribute.OWNER;
import static org.dcache.namespace.FileType.DIR;
import org.dcache.vehicles.FileAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * This session plugin tries to ensure all users that can write into dCache
 * have a home directory.  If presented with a login attempt where this isn't
 * the case, the plugin may be configured to create the home directory.  The
 * plugin makes no checks and undertakes no action for read-only login attempts.
 *
 * The plugin will always fail if the login request does not specify precisely
 * one ReadOnly attribute.
 *
 * The plugin will always succeed for requests that are read-only.
 *
 * For read-write requests the plugin will succeed if and only if, after
 * processing the login request, the user has a home directory that exists in
 * the namespace as a directory owned by the user.
 *
 * If the login request has no HomeDirectory attribute and
 * gplazma.plugins.homedir.build-path-if-undefined.enabled is 'true' and
 * precisely one username principal is defined, then the plugin appends the
 * username to gplazma.plugins.homedir.build-path-if-undefined.path to form the
 * user's home directory path.  This path is stored as a HomeDirectory
 * attribute.  If no username is defined, multiple usernames are defined or
 * gplazma.plugins.homedir.build-path-if-undefine.enabled is 'false' then no
 * further processing takes place and the plugin will fail.
 *
 * The plugin checks whether the path described in the HomeDirectory
 * attribute exists, requesting the path's type and ownership.  If the path
 * exists but is not a directory or is not owned by the login user then the
 * plugin will fail the login request.
 *
 * If the path does not exist and
 * gplazma.plugins.homedir.create-if-absent.enabled is 'false' then the plugin
 * will fail the request.
 *
 * If the path does not exist and
 * gplazma.plugins.homedir.create-if-absent.enabled is 'true' then the plugin
 * will attempt to create the home directory using the login user's uid and
 * primary gid and the directory owner and group-owner and with
 * gplazma.plugins.homedir.create-if-absent.permissions for the UNIX access
 * mode.
 *
 * This will fail if the home directory parent path does not exist or if the
 * user does not have precisely one UidPrincipal and precisely one primary
 * GidPrincipal.  Such a failure will trigger the plugin to fail.
 */
public class HomedirPlugin implements GPlazmaSessionPlugin, NamespaceAware
{
    private static final Logger _log =
            LoggerFactory.getLogger(HomedirPlugin.class);

    private static final String PROPERTY_AUTOBUILD_ENABLED =
            "gplazma.homedir.choose-if-undefined.enabled";
    private static final String PROPERTY_AUTOBUILD_PATH =
            "gplazma.homedir.choose-if-undefined.path";
    private static final String PROPERTY_DIR_CREATE_ENABLED =
            "gplazma.homedir.create-if-absent.enabled";
    private static final String PROPERTY_DIR_CREATE_PERMISSIONS =
            "gplazma.homedir.create-if-absent.permissions";

    private static final IsPrimaryPredicate isPrimary =
            new IsPrimaryPredicate();

    private NameSpaceProvider _namespace;
    private final boolean _isAutobuildEnabled;
    private final FsPath _autobuildPath;
    private final int _dirCreatePermissions;
    private final boolean _isDirCreateEnabled;

    public HomedirPlugin(Properties properties)
    {
        checkPropertiesDefined(properties, PROPERTY_AUTOBUILD_ENABLED,
                PROPERTY_AUTOBUILD_PATH, PROPERTY_DIR_CREATE_ENABLED,
                PROPERTY_DIR_CREATE_PERMISSIONS);

        _isAutobuildEnabled =
                Boolean.valueOf(properties.getProperty(PROPERTY_AUTOBUILD_ENABLED));
        _autobuildPath =
                new FsPath(properties.getProperty(PROPERTY_AUTOBUILD_PATH));
        _isDirCreateEnabled =
                Boolean.parseBoolean(properties.getProperty(PROPERTY_DIR_CREATE_ENABLED));
        _dirCreatePermissions =
                Integer.parseInt(properties.getProperty(PROPERTY_DIR_CREATE_PERMISSIONS), 8);
    }

    private static void checkPropertiesDefined(Properties properties,
            String... names)
    {
        for(String name : names) {
            checkArgument(properties.getProperty(name) != null,
                    "missing " + name + " property");
        }
    }

    @Override
    public void setNamespace(NameSpaceProvider namespace)
    {
        _namespace = namespace;
    }


    @Override
    public void session(Set<Principal> principals, Set<Object> attributes)
            throws AuthenticationException
    {
        new HomedirRequest(principals, attributes).process();
    }

    /**
     * Encapsulate an individual login request.
     */
    private class HomedirRequest
    {
        private final Set<Principal> _principals;
        private final Set<Object> _attributes;

        HomedirRequest(Set<Principal> principals, Set<Object> attributes)
        {
            _principals = principals;
            _attributes = attributes;
        }

        private <T extends Principal> List<T> getPrincipals(Class<T> type)
        {
            Function<Principal,T> cast = new Function<Principal,T>() {
                    @Override
                    public T apply(Principal principal) {
                        return (T) principal;
                    }
            };

            return Lists.newArrayList(transform(filter(_principals,
                    instanceOf(type)), cast));
        }


        public <T> T getRequiredAttribute(Class<T> type)
                throws AuthenticationException
        {
            T attribute = getOptionalAttribute(type);

            checkAuthentication(attribute != null, "missing " +
                    type.getSimpleName() + " attribute");

            return attribute;
        }


        public <T> T getOptionalAttribute(Class<T> type)
                throws AuthenticationException
        {
            List<?> matchingAttributes =
                    Lists.newArrayList(filter(_attributes, instanceOf(type)));

            int count = matchingAttributes.size();

            checkAuthentication(count < 2, "multiple " + type.getSimpleName() +
                    " attributes");

            return count == 0 ? null : (T) matchingAttributes.get(0);
        }


        public void process() throws AuthenticationException
        {
            ReadOnly ro = getRequiredAttribute(ReadOnly.class);

            if(ro.isReadOnly()) {
                return;
            }

            String path = getHomeDirectory();
            PnfsId id = idOf(path);

            if(directoryExists(id)) {
                checkHomeDirectory(id);
            } else {
                createHomeDirectory(path);
            }
        }


        private String getHomeDirectory() throws AuthenticationException
        {
            HomeDirectory home = getOptionalAttribute(HomeDirectory.class);

            if(home == null) {
                checkAuthentication(_isAutobuildEnabled, "no home directory");
                home = createHomeDirectoryAttribute();
                _attributes.add(home);
            }

            return home.getHome();
        }


        private HomeDirectory createHomeDirectoryAttribute()
                throws AuthenticationException
        {
            String username = getUsername();
            String homedir = new FsPath(_autobuildPath, username).toString();
            return new HomeDirectory(homedir);
        }


        private void createHomeDirectory(String homedir) throws AuthenticationException
        {
            checkAuthentication(_isDirCreateEnabled, "home directory does " +
                    "not exist");

            int uid = getUid();
            int gid = getPrimaryGid();

            try {
                _namespace.createEntry(ROOT, homedir, uid, gid,
                        _dirCreatePermissions, true);
            } catch (CacheException e) {
                _log.info("failed to create home directory: {}", e.getMessage());
                throw new AuthenticationException("failed to create home " +
                        "directory: " + e.getMessage());
            }
        }


        private PnfsId idOf(String path) throws AuthenticationException
        {
            try {
                return _namespace.pathToPnfsid(ROOT, path, true);
            } catch (FileNotFoundCacheException e) {
                return null;
            } catch (CacheException e) {
                throw new AuthenticationException(e.getMessage());
            }
        }


        private boolean directoryExists(PnfsId id)
        {
            return id != null;
        }


        private void checkHomeDirectory(PnfsId id)
                throws AuthenticationException
        {
            FileAttributes homedir;

            try {
                homedir = _namespace.getFileAttributes(ROOT, id,
                        EnumSet.of(TYPE, OWNER));
            } catch (CacheException e) {
                throw new AuthenticationException("failed to fetch " +
                        "information about " + id);
            }

            checkAuthentication(homedir.getFileType() == DIR,
                    "home directory " + id + " not a directory");

            int uid = getUid();

            checkAuthentication(homedir.getOwner() == uid,
                    "home directory " + id + " not owned by user");
        }


        private int getUid() throws AuthenticationException
        {
            List<UidPrincipal> uids = getPrincipals(UidPrincipal.class);
            checkAuthentication(uids.size() > 0, "missing uid");
            checkAuthentication(uids.size() == 1, "multiple uids");
            return (int) uids.get(0).getUid();
        }


        private String getUsername() throws AuthenticationException
        {
            List<UserNamePrincipal> usernames =
                    getPrincipals(UserNamePrincipal.class);
            checkAuthentication(usernames.size() > 0, "missing username");
            checkAuthentication(usernames.size() == 1, "multiple usernames");
            return usernames.get(0).getName();
        }

        private int getPrimaryGid() throws AuthenticationException
        {
            List<GidPrincipal> gids = getPrincipals(GidPrincipal.class);

            List<GidPrincipal> primaryGids =
                    Lists.newArrayList(filter(gids, isPrimary));

            checkAuthentication(primaryGids.size() > 0, "missing primary gid");
            checkAuthentication(primaryGids.size() == 1, "multiple primary gid");

            return (int) primaryGids.get(0).getGid();
        }
    }

    /**
     * A simple predicate that selects GidPrincipal objects where isPrimaryGroup
     * returns true.
     */
    private static class IsPrimaryPredicate implements Predicate<GidPrincipal>
    {
        @Override
        public boolean apply(GidPrincipal p)
        {
            return p.isPrimaryGroup();
        }
    }
}