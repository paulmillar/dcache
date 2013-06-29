package org.dcache.auth;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.springframework.beans.factory.annotation.Required;

import javax.security.auth.Subject;

import java.io.File;
import java.security.Principal;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.Collection;
import java.util.EnumSet;
import java.util.concurrent.CopyOnWriteArraySet;

import diskCacheV111.namespace.NameSpaceProvider;
import diskCacheV111.util.CacheException;
import diskCacheV111.util.PermissionDeniedCacheException;

import dmg.cells.nucleus.EnvironmentAware;
import dmg.util.Args;
import dmg.util.Formats;
import dmg.util.Replaceable;

import org.dcache.auth.attributes.LoginAttribute;
import org.dcache.cells.CellCommandListener;
import org.dcache.gplazma.AuthenticationException;
import org.dcache.gplazma.GPlazma;
import org.dcache.gplazma.NoSuchPrincipalException;
import org.dcache.gplazma.configuration.ConfigurationLoadingStrategy;
import org.dcache.gplazma.configuration.FromFileConfigurationLoadingStrategy;
import org.dcache.gplazma.loader.DcacheAwarePluginFactory;
import org.dcache.gplazma.loader.PluginFactory;
import org.dcache.gplazma.monitor.LoginResult;
import org.dcache.gplazma.monitor.LoginResultPrinter;
import org.dcache.gplazma.monitor.RecordingLoginMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import com.google.common.collect.Sets;

import static com.google.common.collect.Iterables.getFirst;
import static com.google.common.base.Predicates.instanceOf;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.Collections2.filter;
import org.dcache.gplazma.configuration.PluginsLifecycleAware;
import org.dcache.util.DiagnoseTriggers;

/**
 * A LoginStrategy that wraps a org.dcache.gplazma.GPlazma
 *
 */
public class Gplazma2LoginStrategy
    implements LoginStrategy, EnvironmentAware, CellCommandListener,
    PluginsLifecycleAware
{
    private static final Logger _log =
            LoggerFactory.getLogger(Gplazma2LoginStrategy.class);

    private String _configurationFile;
    private GPlazma _gplazma;
    private Map<String,Object> _environment = Collections.emptyMap();
    private PluginFactory _factory;
    private KnownFailedLogins _failedLogins = new KnownFailedLogins();

    private DiagnoseTriggers<Principal> _diagnosePrincipals =
            new DiagnoseTriggers<>();

    @Required
    public void setConfigurationFile(String configurationFile)
    {
        if ((configurationFile == null) || (configurationFile.length() == 0)) {
            throw new IllegalArgumentException(
                    "configuration file argument wasn't specified correctly");
        } else if (!new File(configurationFile).exists()) {
            throw new IllegalArgumentException(
                    "configuration file does not exists at " + configurationFile);
        }
        _configurationFile = configurationFile;
    }

    @Required
    public void setNameSpace(NameSpaceProvider namespace)
    {
        _factory = new DcacheAwarePluginFactory(namespace);
    }

    public String getConfigurationFile()
    {
        return _configurationFile;
    }

    @Override
    public void setEnvironment(Map<String,Object> environment)
    {
        _environment = environment;
    }

    public Map<String,Object> getEnvironment()
    {
        return _environment;
    }

    public Properties getEnvironmentAsProperties()
    {
        Replaceable replaceable = new Replaceable() {
                @Override
                public String getReplacement(String name)
                {
                    Object value =  _environment.get(name);
                    return (value == null) ? null : value.toString();
                }
            };

        Properties properties = new Properties();
        for (Map.Entry<String,Object> e: _environment.entrySet()) {
            String key = e.getKey();
            String value = String.valueOf(e.getValue());
            properties.put(key, Formats.replaceKeywords(value, replaceable));
        }

        return properties;
    }

    public void init()
    {
        ConfigurationLoadingStrategy configuration =
            new FromFileConfigurationLoadingStrategy(_configurationFile);
        _gplazma =
            new GPlazma(configuration, getEnvironmentAsProperties(), _factory);
        _gplazma.addConfigurationLifecycleAware(this);
    }

    static LoginReply
        convertLoginReply(org.dcache.gplazma.LoginReply gPlazmaLoginReply)
    {
        Set<Object> sessionAttributes =
            gPlazmaLoginReply.getSessionAttributes();
        Set<LoginAttribute> loginAttributes =
            Sets.newHashSet(Iterables.filter(sessionAttributes, LoginAttribute.class));
        return new LoginReply(gPlazmaLoginReply.getSubject(), loginAttributes);
    }

    /**
     * List of attributes that affect how a login result is printed
     */
    private enum PrintFeatures
    {
        /** Use warn-level instead of debug-level */
        WARN
    }

    private void printLoginResult(LoginResult result,
            EnumSet<PrintFeatures> features)
    {
        LoginResultPrinter printer = new LoginResultPrinter(result);

        String output = printer.print();
        for(String line : Splitter.on('\n').split(output)) {
            if(features.contains(PrintFeatures.WARN)) {
                _log.warn("{}", line);
            } else {
                _log.debug("{}", line);

            }
        }
    }

    @Override
    public LoginReply login(Subject subject) throws CacheException
    {
        RecordingLoginMonitor monitor = new RecordingLoginMonitor();

        LoginResult result = monitor.getResult();
        _diagnosePrincipals.acceptAll(result.allObservedPrincipals());

        try {
            LoginReply reply = convertLoginReply(_gplazma.login(subject, monitor));
            if(_log.isDebugEnabled()) {
                printLoginResult(result, EnumSet.noneOf(PrintFeatures.class));
            }
            _failedLogins.remove(subject);

            return reply;
        } catch (AuthenticationException e) {
            if(!_failedLogins.has(subject)) {
                _failedLogins.add(subject);

                if (result.hasStarted()) {
                    _log.warn("Login attempt failed; " +
                            "detailed explanation follows:");
                    printLoginResult(result, EnumSet.of(PrintFeatures.WARN));
                } else {
                    _log.warn("Login attempt failed: {}", e.getMessage());
                }

            }

            // We deliberately hide the reason why the login failed from the
            // rest of dCache.  This is to prevent a brute-force attack
            // discovering whether certain user accounts exist.
            throw new PermissionDeniedCacheException("login failed");
        }
    }

    @Override
    public void pluginsReloaded()
    {
        _failedLogins.clear();
    }

    @Override
    public Principal map(Principal principal) throws CacheException
    {
        try {
            return _gplazma.map(principal);
        } catch (NoSuchPrincipalException e) {
            return null;
        }
    }

    @Override
    public Set<Principal> reverseMap(Principal principal) throws CacheException
    {
        try {
            return _gplazma.reverseMap(principal);
        } catch (NoSuchPrincipalException e) {
            return Collections.emptySet();
        }
    }

    public static final String fh_explain_login =
            "This command runs a test login with the supplied principals\n" +
            "The result is tracked and an explanation is provided of how \n" +
            "the result was obtained.\n";
    public static final String hh_explain_login = "<principal> [<principal> ...] # explain the result of login";
    public String ac_explain_login_$_1_99(Args args)
    {
        Subject subject = Subjects.subjectFromArgs(args.getArguments());
        RecordingLoginMonitor monitor = new RecordingLoginMonitor();
        try {
            _gplazma.login(subject, monitor);
        } catch (AuthenticationException e) {
            // ignore exception: we'll show this in the explanation.
        }

        LoginResult result = monitor.getResult();
        LoginResultPrinter printer = new LoginResultPrinter(result);
        return printer.print();
    }

    public static final String hh_diagnose_add = "Add new principals";
    public String ac_diagnose_add_$_1_99(Args args)
    {
        Set<Principal> principals = Subjects.principalsFromArgs(args.getArguments());
        _diagnosePrincipals.addAll(principals);
        return "";
    }

    public static final String hh_diagnose_ls = "List diagnose principals";
    public String ac_diagnose_ls(Args args)
    {
        StringBuilder sb = new StringBuilder();
        for (Principal p : _diagnosePrincipals.getAll()) {
            sb.append(p).append('\n');
        }
        return sb.toString();
    }

    public static final String hh_diagnose_rm = "Remove a principal for diagnosis";
    public String ac_diagnose_rm(Args args)
    {
        Set<Principal> principals = Subjects.principalsFromArgs(args.getArguments());
        return _diagnosePrincipals.removeAll(principals) ? "" : "No principal was removed";
    }

    /**
     * Storage class for failed login attempts.  This allows gPlazma to
     * refrain from filling up log files should a client attempt multiple
     * login attempts that all fail.  We must be careful about how we store
     * the incoming Subjects.
     *
     * This class is thread-safe.
     */
    private static class KnownFailedLogins
    {
        private final Set<Subject> _failedLogins =
                new CopyOnWriteArraySet<>();

        /**
         * In general, this class does not store any private credential since
         * doing this would be against the general security advise of
         * only storing sensitive material (e.g., passwords) for as long as
         * is necessary.
         *
         * However, the class may wish to distinguish between different login
         * attempts based information contained in private credentials.  To
         * support this, principals may be added that contain
         * non-sensitive information contained in a private credential.
         */
        private static void addPrincipalsForPrivateCredentials(
                Set<Principal> principals, Set<Object> privateCredentials)
        {
            PasswordCredential password =
                    getFirst(Iterables.filter(privateCredentials,
                    PasswordCredential.class), null);

            if(password != null) {
                Principal loginName =
                        new LoginNamePrincipal(password.getUsername());
                principals.add(loginName);
            }
        }

        /**
         * Some public credentials, when compared, will always be different.
         * An example of this is the X509Certificate chain.  This is provided
         * to gPlazma as an array of X509Certificate objects; however, an
         * array, as an Object, will always be different to any other array.
         *
         * To work around this issue, this method normalises the credential;
         * that is, it converts a credential to one in which two distinct
         * Objects that represent the same information are equal.
         * @param storageCredentials the Set into which normalised credentials
         * are stored.
         * @param credentials the Set of credentials that are to be normalised
         */
        private static void addNormalisedPublicCredentials(
                Set<Object> storageCredentials, Set<Object> credentials)
        {
            for(Object credential : credentials) {
                Object normalised;
                if(credential instanceof X509Certificate[]) {
                    normalised = normalise((X509Certificate[])credential);
                } else {
                    normalised = credential;
                }
                storageCredentials.add(normalised);
            }
        }

        private static Object normalise(X509Certificate[] credential)
        {
            return Lists.newArrayList(credential);
        }


        /**
         * Calculate the storage Subject, given an incoming subject.  The
         * storage subject is similar to the supplied Subject but has sensitive
         * material (like passwords) removed and is location agnostic
         * (e.g., any Origin principals are removed).
         */
        private static Subject storageSubjectFor(Subject subject)
        {
            Subject storage = new Subject();

            addNormalisedPublicCredentials(storage.getPublicCredentials(),
                    subject.getPublicCredentials());

            /*
             * Do not store any private credentials as doing so would be a
             * security risk.
             */

            Collection<Principal> allExceptOrigin =
                    filter(subject.getPrincipals(), not(instanceOf(Origin.class)));

            storage.getPrincipals().addAll(allExceptOrigin);

            addPrincipalsForPrivateCredentials(storage.getPrincipals(),
                    subject.getPrivateCredentials());

            return storage;
        }

        private boolean has(Subject subject)
        {
            Subject storage = storageSubjectFor(subject);
            return _failedLogins.contains(storage);
        }

        private void add(Subject subject)
        {
            Subject storage = storageSubjectFor(subject);
            _failedLogins.add(storage);
        }

        private void remove(Subject subject)
        {
            Subject storage = storageSubjectFor(subject);
            _failedLogins.remove(storage);
        }

        private void clear()
        {
            _failedLogins.clear();
        }
    }
}
