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

import java.security.Principal;
import java.util.Collections;
import java.util.HashSet;
import static java.util.Objects.requireNonNull;
import java.util.Set;
import org.dcache.gplazma.configuration.ConfigurationItemControl;
import org.dcache.gplazma.monitor.LoginMonitor;
import org.dcache.gplazma.monitor.LoginResult;
import org.dcache.gplazma.monitor.LoginResult.AccountPhaseResult;
import org.dcache.gplazma.monitor.LoginResult.AccountPluginResult;
import org.dcache.gplazma.monitor.LoginResult.AuthPhaseResult;
import org.dcache.gplazma.monitor.LoginResult.AuthPluginResult;
import org.dcache.gplazma.monitor.LoginResult.MapPhaseResult;
import org.dcache.gplazma.monitor.LoginResult.MapPluginResult;
import org.dcache.gplazma.monitor.LoginResult.PAMPluginResult;
import org.dcache.gplazma.monitor.LoginResult.PhaseResult;
import org.dcache.gplazma.monitor.LoginResult.SessionPhaseResult;
import org.dcache.gplazma.monitor.LoginResult.SessionPluginResult;
import org.dcache.util.PrincipalSetMaker;


/**
 * A fluent class for building LoginResult objects.
 */
public class LoginResultBuilderFramework {

    private final LoginResult result = new LoginResult();

    private static void setAddedPrincipals(PhaseResult result, Set<Principal> defaultBefore,
            Set<Principal> added) {
        var diff = result.getPrincipals();
        var before = diff == null ? defaultBefore : diff.getBefore();
        Set<Principal> after = new HashSet<>(before);
        after.addAll(added);
        result.setPrincipals(before, after);
    }

    public class LoginResultBaseBuilder {
        public LoginResultBaseBuilder withDoorSupplying(PrincipalSetMaker principals) {

            var authPhase = result.getAuthPhase();
            var diff = authPhase.getPrincipals();
            Set<Principal> added = diff == null ? Collections.emptySet() : diff.getAdded();

            var before = principals.build();
            var after = new HashSet<Principal>(before);
            after.addAll(added);
            authPhase.setPrincipals(before, after);

            return this;
        }

        public AuthPhaseBuilder withAuthPhase() {
            return new AuthPhaseBuilder();
        }

        public MapPhaseBuilder withMapPhase() {
            return new MapPhaseBuilder();
        }

        public AccountPhaseBuilder withAccountPhase() {
            return new AccountPhaseBuilder();
        }

        public SessionPhaseBuilder withSessionPhase() {
            return new SessionPhaseBuilder();
        }

        public LoginResult build() {
            return result;
        }
    }

    public class LoginResultBuilder extends LoginResultBaseBuilder {
        public LoginResultBuilder withValidationResult(LoginMonitor.Result result) {
            LoginResultBuilderFramework.this.result.setValidationResult(result);
            return this;
        }

        public LoginResultBuilder withValidationError(String error) {
            LoginResultBuilderFramework.this.result.setValidationError(error);
            return this;
        }
    }

    private abstract class PhaseBuilder<T extends PhaseBuilder> extends LoginResultBaseBuilder {
        protected abstract PhaseResult<? extends PAMPluginResult> phaseResult();

        public T withPrincipals(Set<Principal> before, Set<Principal> after) {
            phaseResult().setPrincipals(before, after);
            return (T)this;
        }

        public T withResult(LoginMonitor.Result result) {
            phaseResult().setResult(result);
            return (T)this;
        }
    }

    public class AuthPhaseBuilder extends PhaseBuilder<AuthPhaseBuilder> {
        @Override
        protected AuthPhaseResult phaseResult() {
            return LoginResultBuilderFramework.this.result.getAuthPhase();
        }

        public AuthPhaseBuilder withPublicCredentials(Set<Object> credentials) {
            phaseResult().setPublicCredentials(credentials);
            return this;
        }

        public AuthPhaseBuilder thatAdds(PrincipalSetMaker added) {
            var authPhase = result.getAuthPhase();
            setAddedPrincipals(authPhase, Collections.emptySet(), added.build());
            return this;
        }

        public AuthPhaseBuilder withPrivateCredentials(Set<Object> credentials) {
            phaseResult().setPrivateCredentials(credentials);
            return this;
        }

        public AuthPhaseBuilder with(AuthPluginResultBuilder builder) {
            phaseResult().addPluginResult(builder.build());
            return this;
        }
    }

    public class MapPhaseBuilder extends PhaseBuilder<MapPhaseBuilder> {
        @Override
        protected MapPhaseResult phaseResult() {
            return LoginResultBuilderFramework.this.result.getMapPhase();
        }

        public MapPhaseBuilder thatAdds(PrincipalSetMaker added) {
            var authPhase = result.getAuthPhase();
            var defaultBefore = authPhase.getPrincipals().getAfter(); // Just assume this is set.
            var mapPhase = result.getMapPhase();
            setAddedPrincipals(mapPhase, defaultBefore, added.build());
            return this;
        }

        public MapPhaseBuilder with(MapPluginResultBuilder builder) {
            phaseResult().addPluginResult(builder.build());
            return this;
        }
    }

    public class AccountPhaseBuilder extends PhaseBuilder<AccountPhaseBuilder> {
        @Override
        protected AccountPhaseResult phaseResult() {
            return LoginResultBuilderFramework.this.result.getAccountPhase();
        }

        public AccountPhaseBuilder thatAdds(PrincipalSetMaker added) {
            var mapPhase = result.getMapPhase();
            var defaultBefore = mapPhase.getPrincipals().getAfter(); // Just assume this is set.
            var accountPhase = result.getAccountPhase();
            setAddedPrincipals(accountPhase, defaultBefore, added.build());
            return this;
        }

        public AccountPhaseBuilder withPluginResult(AccountPluginResultBuilder builder) {
            phaseResult().addPluginResult(builder.build());
            return this;
        }
    }

    public class SessionPhaseBuilder extends PhaseBuilder<SessionPhaseBuilder> {
        @Override
        protected SessionPhaseResult phaseResult() {
            return LoginResultBuilderFramework.this.result.getSessionPhase();
        }

        public SessionPhaseBuilder withAttributes(Set<Object> attributes) {
            phaseResult().setAttributes(attributes);
            return this;
        }

        public SessionPhaseBuilder thatAdds(PrincipalSetMaker added) {
            var accountPhase = result.getAccountPhase();
            var defaultBefore = accountPhase.getPrincipals().getAfter(); // Just assume this is set.
            var sessionPhase = result.getSessionPhase();
            setAddedPrincipals(sessionPhase, defaultBefore, added.build());
            return this;
        }

        public SessionPhaseBuilder withPluginResult(SessionPluginResultBuilder builder) {
            phaseResult().addPluginResult(builder.build());
            return this;
        }
    }

    private static class PAMPluginResultBuilder<B extends PAMPluginResultBuilder> {
        protected String name;
        protected ConfigurationItemControl control;
        private LoginMonitor.Result result;
        private String error;

        protected PAMPluginResultBuilder(String name, ConfigurationItemControl control) {
            this.name = requireNonNull(name);
            this.control = requireNonNull(control);
        }

        public B withResult(LoginMonitor.Result result) {
            this.result = requireNonNull(result);
            return (B)this;
        }

        public B withSuccess() {
            return withResult(LoginMonitor.Result.SUCCESS);
        }

        public B withError(String error) {
            this.error = requireNonNull(error);
            return (B)this;
        }

        protected void updateResult(PAMPluginResult pluginResult) {
            if (result != null) {
                pluginResult.setResult(result);
            }

            if (error != null) {
                pluginResult.setError(error);
            }
        }
    }

    public static AuthPluginResultBuilder anAuthPlugin(String name, ConfigurationItemControl control) {
        return new AuthPluginResultBuilder(name, control);
    }

    public static class AuthPluginResultBuilder extends PAMPluginResultBuilder<AuthPluginResultBuilder> {
        /*
        TODO add support for

        private SetDiff<Principal> _identified;
        private SetDiff<Object> _publicCredentials;
        private SetDiff<Object> _privateCredentials;
        */

        public AuthPluginResultBuilder(String name, ConfigurationItemControl control) {
            super(name, control);
        }

        public AuthPluginResult build() {
            AuthPluginResult pluginResult = new AuthPluginResult(name, control);
            updateResult(pluginResult);
            return pluginResult;
        }
    }

    public static MapPluginResultBuilder aMapPlugin(String name, ConfigurationItemControl control) {
        return new MapPluginResultBuilder(name, control);
    }

    public static class MapPluginResultBuilder extends PAMPluginResultBuilder<MapPluginResultBuilder> {
        /*
        TODO add support for

              private SetDiff<Principal> _principals;
        */
        public MapPluginResultBuilder(String name, ConfigurationItemControl control) {
            super(name, control);
        }

        public MapPluginResult build() {
            MapPluginResult pluginResult = new MapPluginResult(name, control);
            updateResult(pluginResult);
            return pluginResult;
        }
    }

    public static AccountPluginResultBuilder anAccountPlugin(String name, ConfigurationItemControl control) {
        return new AccountPluginResultBuilder(name, control);
    }

    public static class AccountPluginResultBuilder extends PAMPluginResultBuilder {
        /*
        TODO add support for

            private SetDiff<Principal> _authorized;
        */
        public AccountPluginResultBuilder(String name, ConfigurationItemControl control) {
            super(name, control);
        }

        public AccountPluginResult build() {
            AccountPluginResult pluginResult = new AccountPluginResult(name, control);
            updateResult(pluginResult);
            return pluginResult;
        }
    }

    public static SessionPluginResultBuilder aSessionPlugin(String name, ConfigurationItemControl control) {
        return new SessionPluginResultBuilder(name, control);
    }

    public static class SessionPluginResultBuilder extends PAMPluginResultBuilder {
        /*
        TODO add support for

            private SetDiff<Principal> _principals;
            private Set<Object> _attributes;
        */
        public SessionPluginResultBuilder(String name, ConfigurationItemControl control) {
            super(name, control);
        }

        public SessionPluginResult build() {
            SessionPluginResult pluginResult = new SessionPluginResult(name, control);
            updateResult(pluginResult);
            return pluginResult;
        }
    }

    private LoginResultBuilder init() {
        return new LoginResultBuilder();
    }

    public static LoginResultBuilder aLoginResult() {
        return new LoginResultBuilderFramework().init();
    }
}
