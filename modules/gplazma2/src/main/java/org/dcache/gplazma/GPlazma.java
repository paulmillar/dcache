package org.dcache.gplazma;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.Subject;

import java.security.Principal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import org.dcache.commons.util.NDC;
import org.dcache.gplazma.configuration.Configuration;
import org.dcache.gplazma.configuration.ConfigurationItem;
import org.dcache.gplazma.configuration.ConfigurationItemControl;
import org.dcache.gplazma.configuration.ConfigurationItemType;
import org.dcache.gplazma.configuration.ConfigurationLoadingStrategy;
import org.dcache.gplazma.configuration.parser.FactoryConfigurationException;
import org.dcache.gplazma.loader.CachingPluginLoaderDecorator;
import org.dcache.gplazma.loader.PluginFactory;
import org.dcache.gplazma.loader.PluginLoader;
import org.dcache.gplazma.loader.PluginLoadingException;
import org.dcache.gplazma.loader.XmlResourcePluginLoader;
import org.dcache.gplazma.monitor.LoggingLoginMonitor;
import org.dcache.gplazma.monitor.LoginMonitor;
import org.dcache.gplazma.monitor.LoginMonitor.Result;
import org.dcache.gplazma.plugins.GPlazmaAccountPlugin;
import org.dcache.gplazma.plugins.GPlazmaAuthenticationPlugin;
import org.dcache.gplazma.plugins.GPlazmaIdentityPlugin;
import org.dcache.gplazma.plugins.GPlazmaMappingPlugin;
import org.dcache.gplazma.plugins.GPlazmaPlugin;
import org.dcache.gplazma.plugins.GPlazmaSessionPlugin;
import org.dcache.gplazma.strategies.AccountStrategy;
import org.dcache.gplazma.strategies.AuthenticationStrategy;
import org.dcache.gplazma.strategies.GPlazmaPluginElement;
import org.dcache.gplazma.strategies.IdentityStrategy;
import org.dcache.gplazma.strategies.MappingStrategy;
import org.dcache.gplazma.strategies.SessionStrategy;
import org.dcache.gplazma.strategies.StrategyFactory;
import org.dcache.gplazma.validation.ValidationStrategy;
import org.dcache.gplazma.validation.ValidationStrategyFactory;

import static com.google.common.base.Preconditions.checkNotNull;
import org.dcache.gplazma.configuration.PluginsLifecycleAware;

public class GPlazma
{
    private static final Logger LOGGER =
        LoggerFactory.getLogger( GPlazma.class);

    private static final LoginMonitor LOGGING_LOGIN_MONITOR =
            new LoggingLoginMonitor();

    private Properties _globalProperties;
    private boolean _globalPropertiesHaveUpdated;

    private PluginLoader pluginLoader;

    private final PluginFactory _customPluginFactory;

    private GPlazmaInternalException _lastLoadPluginsProblem;

    private List<GPlazmaPluginElement<GPlazmaAuthenticationPlugin>>
            authenticationPluginElements;
    private List<GPlazmaPluginElement<GPlazmaMappingPlugin>>
            mappingPluginElements;
    private List<GPlazmaPluginElement<GPlazmaAccountPlugin>>
            accountPluginElements;
    private List<GPlazmaPluginElement<GPlazmaSessionPlugin>>
            sessionPluginElements;
    private List<GPlazmaPluginElement<GPlazmaIdentityPlugin>>
            identityPluginElements;

    private final Set<PluginsLifecycleAware> _pluginsLifecycleAware
            = new HashSet<>();

    private final ConfigurationLoadingStrategy configurationLoadingStrategy;
    private AuthenticationStrategy _authStrategy;
    private MappingStrategy _mapStrategy;
    private AccountStrategy _accountStrategy;
    private SessionStrategy _sessionStrategy;
    private ValidationStrategy validationStrategy;
    private IdentityStrategy identityStrategy;




    /**
     * @param configurationLoadingStrategy The strategy for loading the plugin configuration.
     * @param properties General configuration for plugins
     */
    public GPlazma(ConfigurationLoadingStrategy configurationLoadingStrategy,
                   Properties properties)
    {
        this(configurationLoadingStrategy, properties, null);
    }

    /**
     * @param configurationLoadingStrategy The strategy for loading the plugin configuration.
     * @param properties General configuration for plugins
     * @param factory Custom PluginFactory to allow customisation of plugins
     */
    public GPlazma(ConfigurationLoadingStrategy configurationLoadingStrategy,
                   Properties properties, PluginFactory factory)
    {
        this.configurationLoadingStrategy = configurationLoadingStrategy;
        _globalProperties = properties;
        _customPluginFactory = factory;
        try {
            loadPlugins();
        } catch (GPlazmaInternalException e) {
            /* Ignore this error.  Subsequent attempts to use gPlazma will
             * fail with the same error.  gPlazma will try to rectify the
             * problem if configuration file is edited.
             */
        }
    }

    public void addConfigurationLifecycleAware(PluginsLifecycleAware aware)
    {
        _pluginsLifecycleAware.add(aware);
    }

    public LoginReply login(Subject subject) throws AuthenticationException
    {
        return login(subject, LOGGING_LOGIN_MONITOR);
    }

    public LoginReply login(Subject subject, LoginMonitor monitor)
            throws AuthenticationException
    {
        checkNotNull(subject, "subject is null");

        AuthenticationStrategy authStrategy;
        MappingStrategy mapStrategy;
        AccountStrategy accountStrategy;
        SessionStrategy sessionStrategy;

        synchronized (configurationLoadingStrategy) {
            try {
                checkPluginConfig();
            } catch(GPlazmaInternalException e) {
                throw new AuthenticationException("internal gPlazma error: " +
                        e.getMessage());
            }

            authStrategy = _authStrategy;
            mapStrategy = _mapStrategy;
            accountStrategy = _accountStrategy;
            sessionStrategy = _sessionStrategy;
        }

        Set<Principal> identifiedPrincipals = doAuthPhase(authStrategy, monitor,
                subject);

        Set<Principal> authorizedPrincipals = doMapPhase(mapStrategy, monitor,
                identifiedPrincipals);

        doAccountPhase(accountStrategy, monitor, authorizedPrincipals);

        Set<Object> attributes = doSessionPhase(sessionStrategy, monitor,
                authorizedPrincipals);

        return buildReply(monitor, subject, authorizedPrincipals, attributes);
    }


    private Set<Principal> doAuthPhase(AuthenticationStrategy strategy,
            LoginMonitor monitor, Subject subject)
            throws AuthenticationException
    {
        Set<Object> publicCredentials = subject.getPublicCredentials();
        Set<Object> privateCredentials = subject.getPrivateCredentials();

        Set<Principal> principals = new HashSet<>();
        principals.addAll(subject.getPrincipals());

        NDC.push("AUTH");
        Result result = Result.FAIL;
        try {
            monitor.authBegins(publicCredentials, privateCredentials,
                    principals);
            strategy.authenticate(monitor,
                    publicCredentials, privateCredentials,
                    principals);
            result = Result.SUCCESS;
        } finally {
            NDC.pop();
            monitor.authEnds(principals, result);
        }

        return principals;
    }


    private Set<Principal> doMapPhase(MappingStrategy strategy,
            LoginMonitor monitor, Set<Principal> identifiedPrincipals)
            throws AuthenticationException
    {
        Set<Principal> authorizedPrincipals = new HashSet<>();

        NDC.push("MAP");
        Result result = Result.FAIL;
        try {
            monitor.mapBegins(identifiedPrincipals);
            strategy.map(monitor, identifiedPrincipals, authorizedPrincipals);
            result = Result.SUCCESS;
        } finally {
            NDC.pop();
            monitor.mapEnds(authorizedPrincipals, result);
        }

        return authorizedPrincipals;
    }


    private void doAccountPhase(AccountStrategy strategy, LoginMonitor monitor,
            Set<Principal> principals) throws AuthenticationException
    {
        NDC.push("ACCOUNT");
        Result result = Result.FAIL;
        try {
            monitor.accountBegins(principals);
            strategy.account(monitor, principals);
            result = Result.SUCCESS;
        } finally {
            NDC.pop();
            monitor.accountEnds(principals, result);
        }
    }

    private Set<Object> doSessionPhase(SessionStrategy strategy,
            LoginMonitor monitor, Set<Principal> principals)
            throws AuthenticationException
    {
        Set<Object> attributes = new HashSet<>();

        NDC.push("SESSION");
        Result result = Result.FAIL;
        try {
            monitor.sessionBegins(principals);
            strategy.session(monitor, principals, attributes);
            result = Result.SUCCESS;
        } finally {
            NDC.pop();
            monitor.sessionEnds(principals, attributes, result);
        }

        return attributes;
    }


    public LoginReply buildReply(LoginMonitor monitor, Subject originalSubject,
            Set<Principal> principals, Set<Object> attributes)
            throws AuthenticationException
    {
        Set<Object> publicCredentials = originalSubject.getPublicCredentials();
        Set<Object> privateCredentials = originalSubject.getPrivateCredentials();

        LoginReply reply = new LoginReply();

        Subject subject = new Subject(false, principals, publicCredentials,
                privateCredentials);
        reply.setSubject(subject);
        reply.setSessionAttributes(attributes);

        Result result = Result.FAIL;
        String error = null;
        NDC.push("VALIDATION");
        try {
            validationStrategy.validate(reply);
            result = Result.SUCCESS;
        } catch(AuthenticationException e) {
            error = e.getMessage();
            throw e;
        } finally {
            NDC.pop();
            monitor.validationResult(result, error);
        }

        return reply;
    }


    public Principal map(Principal principal) throws NoSuchPrincipalException
    {
        try {
            return getIdentityStrategy().map(principal);
        } catch (GPlazmaInternalException e) {
            throw new NoSuchPrincipalException("internal gPlazma error: " +
                    e.getMessage());
        }
    }

    public Set<Principal> reverseMap(Principal principal)
            throws NoSuchPrincipalException
    {
        try {
            return getIdentityStrategy().reverseMap(principal);
        } catch (GPlazmaInternalException e) {
            throw new NoSuchPrincipalException("internal gPlazma error: " +
                    e.getMessage());
        }
    }

    private IdentityStrategy getIdentityStrategy() throws GPlazmaInternalException
    {
        synchronized (configurationLoadingStrategy) {
            checkPluginConfig();
            return this.identityStrategy;
        }
    }

    private void loadPlugins() throws GPlazmaInternalException
    {
        LOGGER.debug("reloading plugins");

        pluginLoader = new CachingPluginLoaderDecorator(
                XmlResourcePluginLoader.newPluginLoader());
        if(_customPluginFactory != null) {
            pluginLoader.setPluginFactory(_customPluginFactory);
        }
        pluginLoader.init();

        resetPlugins();

        try {
            Configuration configuration = configurationLoadingStrategy.load();
            List<ConfigurationItem> items = configuration.getConfigurationItemList();

            for(ConfigurationItem item : items) {
                String pluginName = item.getPluginName();

                Properties pluginProperties = item.getPluginConfiguration();
                Properties combinedProperties = new Properties(_globalProperties);
                combinedProperties.putAll(pluginProperties);

                GPlazmaPlugin plugin;

                try {
                    plugin = pluginLoader.newPluginByName(pluginName,
                        combinedProperties);
                } catch(PluginLoadingException e) {
                    throw new PluginLoadingException("failed to create "
                            + pluginName + ": " + e.getMessage(), e);
                }

                ConfigurationItemControl control = item.getControl();
                ConfigurationItemType type = item.getType();

                classifyPlugin(type, plugin, pluginName, control);
            }

            initStrategies();
        } catch(GPlazmaInternalException e) {
            LOGGER.error(e.getMessage());
            _lastLoadPluginsProblem = e;
            throw e;
        }


        if(isPreviousLoadPluginsProblematic()) {
            /* FIXME: this should be logged at info level but we want it to
             *        appear in the log file. */
            LOGGER.warn("gPlazma configuration successfully loaded");

            _lastLoadPluginsProblem = null;
        }
    }


    private void resetPlugins()
    {
        authenticationPluginElements = new ArrayList<>();
        mappingPluginElements = new ArrayList<>();
        accountPluginElements = new ArrayList<>();
        sessionPluginElements = new ArrayList<>();
        identityPluginElements = new ArrayList<>();
    }


    private void initStrategies() throws FactoryConfigurationException
    {
        StrategyFactory factory = StrategyFactory.getInstance();
        _authStrategy = factory.newAuthenticationStrategy();
        _authStrategy.setPlugins(authenticationPluginElements);
        _mapStrategy = factory.newMappingStrategy();
        _mapStrategy.setPlugins(mappingPluginElements);
        _accountStrategy = factory.newAccountStrategy();
        _accountStrategy.setPlugins(accountPluginElements);
        _sessionStrategy = factory.newSessionStrategy();
        _sessionStrategy.setPlugins(sessionPluginElements);
        identityStrategy = factory.newIdentityStrategy();
        identityStrategy.setPlugins(identityPluginElements);

        ValidationStrategyFactory validationFactory =
                ValidationStrategyFactory.getInstance();
        validationStrategy = validationFactory.newValidationStrategy();
    }

    private void checkPluginConfig() throws GPlazmaInternalException
    {
        if (_globalPropertiesHaveUpdated || configurationLoadingStrategy.hasUpdated()) {
            _globalPropertiesHaveUpdated = false;
            loadPlugins();
            for(PluginsLifecycleAware aware : _pluginsLifecycleAware) {
                aware.pluginsReloaded();
            }
        }

        if(isPreviousLoadPluginsProblematic()) {
            throw _lastLoadPluginsProblem;
        }
    }

    private boolean isPreviousLoadPluginsProblematic()
    {
        return _lastLoadPluginsProblem != null;
    }

    private void classifyPlugin( ConfigurationItemType type,
            GPlazmaPlugin plugin, String pluginName,
            ConfigurationItemControl control) throws PluginLoadingException
    {
        if(!type.getType().isAssignableFrom(plugin.getClass())) {
                    throw new PluginLoadingException("plugin " + pluginName +
                            " (java class  " +
                            plugin.getClass().getCanonicalName() +
                            ") does not support being loaded as type " + type );
        }
        switch (type) {
            case AUTHENTICATION:
            {
                storePluginElement(plugin, pluginName, control,
                        authenticationPluginElements);
                break;
            }
            case MAPPING:
            {
                storePluginElement(plugin, pluginName, control,
                        mappingPluginElements);
                break;
            }
            case ACCOUNT:
            {
                storePluginElement(plugin, pluginName, control,
                        accountPluginElements);
                break;
            }
            case SESSION:
            {
                storePluginElement(plugin, pluginName, control,
                        sessionPluginElements);
                break;
            }
            case IDENTITY: {
                storePluginElement(plugin, pluginName, control,
                        identityPluginElements);
                break;
            }
            default:
            {
                throw new PluginLoadingException("unknown plugin type " + type);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends GPlazmaPlugin> void storePluginElement(
            GPlazmaPlugin plugin, String pluginName,
            ConfigurationItemControl control,
            List<GPlazmaPluginElement<T>> pluginElements)
    {
        // we are forced to use unchecked cast here, as the generics do not support
        // instanceof, but we have checked the type before calling storePluginElement
        T authPlugin = (T) plugin;
        GPlazmaPluginElement<T> pluginElement = new GPlazmaPluginElement<>(authPlugin, pluginName, control);
        pluginElements.add(pluginElement);
    }
}
