package org.dcache.webadmin.view.pages.login;

import org.apache.wicket.PageReference;
import org.apache.wicket.Session;
import org.apache.wicket.authentication.IAuthenticationStrategy;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Button;
import org.apache.wicket.markup.html.form.CheckBox;
import org.apache.wicket.markup.html.form.PasswordTextField;
import org.apache.wicket.markup.html.form.StatelessForm;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.CompoundPropertyModel;
import org.apache.wicket.protocol.https.RequireHttps;
import org.apache.wicket.request.Request;
import org.apache.wicket.request.cycle.RequestCycle;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.io.ssl.SslConnection.DecryptedEndPoint;
import org.eclipse.jetty.server.HttpConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import org.dcache.webadmin.controller.LogInService;
import org.dcache.webadmin.controller.exceptions.LogInServiceException;
import org.dcache.webadmin.view.beans.LogInBean;
import org.dcache.webadmin.view.beans.UserBean;
import org.dcache.webadmin.view.beans.WebAdminInterfaceSession;
import org.dcache.webadmin.view.pages.basepage.BasePage;
import org.dcache.webadmin.view.util.DefaultFocusBehaviour;

import static javax.net.ssl.SSLEngineResult.HandshakeStatus.NEED_UNWRAP;

/**
 * Contains all the page construction and logic for servicing a login
 * request, but may be extended to return to a referring page
 * by overriding the #getReferrer() method.
 *
 * @author arossi
 */
@RequireHttps
public class LogIn extends BasePage {

    private static final X509Certificate[] EMPTY_X509_ARRAY = new X509Certificate[0];
    private static final Logger _log = LoggerFactory.getLogger(LogIn.class);
    private static final long serialVersionUID = 8902191632839916396L;

    private class LogInForm extends StatelessForm {

        private class CertSignInButton extends Button {

            private static final long serialVersionUID = 7349334961548160283L;

            public CertSignInButton(String id) {
                super(id);
                /*
                 * deactivate checking of formdata for certsignin
                 */
                this.setDefaultFormProcessing(false);
            }

            @Override
            public void onSubmit() {
                try {
                    if (!isSignedIn()) {
                        signInWithCert(getLogInService());
                    }
                    goToPage();
                } catch (IllegalArgumentException ex) {
                    error(getStringResource("noCertError"));
                    _log.debug("no certificate provided");
                } catch (LogInServiceException ex) {
                    String cause = "unknown";
                    if (ex.getMessage() != null) {
                        cause = ex.getMessage();
                    }
                    error(getStringResource("loginError"));
                    _log.debug("cert sign in error - cause {}", cause);
                }
            }
        }

        private class LogInButton extends Button {

            private static final long serialVersionUID = -8852712258475979167L;

            public LogInButton(String id) {
                super(id);
            }

            @Override
            public void onSubmit() {
                IAuthenticationStrategy strategy
                    = getApplication().getSecuritySettings()
                                      .getAuthenticationStrategy();
                try {
                    if (!isSignedIn()) {
                        signIn(_logInModel, strategy);
                    }
                    goToPage();
                } catch (LogInServiceException ex) {
                    strategy.remove();
                    String cause = "unknown";
                    if (ex.getMessage() != null) {
                        cause = ex.getMessage();
                    }
                    error(getStringResource("loginError") + " - cause: "
                                    + cause);
                    _log.debug("user/pwd sign in error - cause {}", cause);
                }
            }
        }

        private static final long serialVersionUID = -1800491058587279179L;
        private final TextField _username;
        private final PasswordTextField _password;
        private final CheckBox _rememberMe;
        private final WebMarkupContainer _rememberMeRow;
        private final CheckBox _activateRoles;
        private final WebMarkupContainer _activateRolesRow;
        private final LogInBean _logInModel;

        LogInForm(final String id) {
            super(id, new CompoundPropertyModel<>(new LogInBean()));
            _logInModel = (LogInBean) getDefaultModelObject();

            _username = new TextField("username");
            _username.setRequired(true);
            add(_username);
            _password = new PasswordTextField("password");

            add(_password);
            add(new LogInButton("submit"));
            _rememberMeRow = new WebMarkupContainer("rememberMeRow");
            add(_rememberMeRow);
            _rememberMe = new CheckBox("remembering");
            _rememberMeRow.add(_rememberMe);
            _activateRolesRow = new WebMarkupContainer("activateRolesRow");
            add(_activateRolesRow);
            _activateRoles = new CheckBox("activateRoles");
            _activateRolesRow.add(_activateRoles);
            Button certButton = new CertSignInButton("certsignin");
            certButton.add(new DefaultFocusBehaviour());
            add(certButton);
        }

        private LogInService getLogInService() {
            return getWebadminApplication().getLogInService();
        }

        private boolean isSignedIn() {
            return getWebadminSession().isSignedIn();
        }

        private void signIn(LogInBean model, IAuthenticationStrategy strategy)
                        throws LogInServiceException {
            String username;
            String password;
            if (model != null) {
                username = model.getUsername();
                password = model.getPassword();
            } else {
                /*
                 * get username and password from persistence store
                 */
                String[] data = strategy.load();
                if ((data == null) || (data.length <= 1)) {
                    throw new LogInServiceException("no username data saved");
                }
                username = data[0];
                password = data[1];
            }
            _log.debug("username sign in, username: {}", username);
            UserBean user = getLogInService().authenticate(username,
                            password.toCharArray());
            getWebadminSession().setUser(user);
            if (model != null && model.isActivateRoles()) {
                user.activateAllRoles();
            }
            if (model != null && model.isRemembering()) {
                strategy.save(username, password);
            } else {
                strategy.remove();
            }
        }
    }

    /**
     *   Can be overridden to return a reference.
     */
    protected PageReference getReferrer() {
        return null;
    }

    protected void goToPage() {
        /*
         * Covers the redirect from admin-authz page, noop in other cases
         */
        continueToOriginalDestination();

        PageReference ref = getReferrer();
        if (ref != null) {
            /*
             * Covers the user-clicking a the "Login" button.
             * This must be done first.
             */
            setResponsePage(ref.getPage());
        } else {
            /*
             * Covers case when user somehow jumps to login page directly
             */
            setResponsePage(getWebadminApplication().getHomePage());
        }
    }

    @Override
    protected void initialize() {
        super.initialize();
        final FeedbackPanel feedback = new FeedbackPanel("feedback");
        add(new Label("dCacheInstanceName",
                        getWebadminApplication().getDcacheName()));
        add(new Label("dCacheInstanceDescription",
                        getWebadminApplication().getDcacheDescription()));
        add(feedback);
        add(new LogInForm("LogInForm"));
    }

    public static void signInWithCert(LogInService service)
            throws IllegalArgumentException, LogInServiceException
    {
        X509Certificate[] certChain = getCertChain();
        UserBean user = service.authenticate(certChain);
        WebAdminInterfaceSession session = (WebAdminInterfaceSession) Session.get();
        session.setUser(user);
    }

    private static X509Certificate[] getCertChain() throws LogInServiceException
    {
        DecryptedEndPoint endpoint = decryptedEndPoint();

        try {
            renegotiateWantClientAuth(endpoint, true);
            return peerCertificatesFrom(endpoint);
        } catch (IOException e) {
            throw new LogInServiceException("Failed to fetch client certificates: " + e);
        } finally {
            try {
                renegotiateWantClientAuth(endpoint, false);
            } catch (IOException e) {
                _log.warn("Failed to renegotiate without requesting client auth: {}",
                        e.getMessage());
            }
        }
    }

    private static void renegotiateWantClientAuth(DecryptedEndPoint endpoint,
            boolean wantClientAuth) throws IOException
    {
        SSLEngine engine = sslEngineOf(endpoint);
        engine.setWantClientAuth(wantClientAuth);
        engine.beginHandshake();
        do {
            endpoint.flush();
        } while (engine.getHandshakeStatus() == NEED_UNWRAP);
    }

    private static X509Certificate [] peerCertificatesFrom(DecryptedEndPoint endpoint)
            throws SSLPeerUnverifiedException
    {
        Certificate [] chain = sslEngineOf(endpoint).getSession().getPeerCertificates();

        return chain == null ? EMPTY_X509_ARRAY : (X509Certificate []) chain;
    }

    private static DecryptedEndPoint decryptedEndPoint() throws LogInServiceException
    {
        Request request = RequestCycle.get().getRequest();
        HttpServletRequest servletRequest = (HttpServletRequest) request.getContainerRequest();

        // Jetty makes the Connection available as an attribute with that class' name.
        HttpConnection connection = (HttpConnection) servletRequest.getAttribute(HttpConnection.class.getName());

        EndPoint endpoint = connection.getHttpChannel().getEndPoint();

        if (!(endpoint instanceof DecryptedEndPoint)) {
            throw new LogInServiceException("Connection is not TLS encrypted");
        }

        return (DecryptedEndPoint) endpoint;
    }

    private static SSLEngine sslEngineOf(DecryptedEndPoint endpoint)
    {
        return endpoint.getSslConnection().getSSLEngine();
    }
}
