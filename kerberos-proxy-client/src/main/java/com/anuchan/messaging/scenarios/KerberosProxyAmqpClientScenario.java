package com.anuchan.messaging.scenarios;

import com.azure.core.amqp.AmqpTransportType;
import com.azure.core.amqp.ProxyAuthenticator;
import com.azure.core.amqp.ProxyOptions;
import com.azure.core.credential.AzureNamedKeyCredential;
import com.azure.messaging.eventhubs.EventDataBatch;
import com.azure.messaging.eventhubs.EventHubClientBuilder;
import com.azure.messaging.eventhubs.EventHubProducerClient;
import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.Oid;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.UnknownHostException;
import java.security.Security;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;

@Service
public class KerberosProxyAmqpClientScenario extends ProxyScenario {
    private static final String PROXY_HOST = "kerberos-http-proxy-server";
    private static final int PROXY_PORT = 80;

    private static final String EVENT_HUB_NAMESPACE = "<eh-namespace-name>.servicebus.windows.net";
    private static final String EVENT_HUBS_NAME = "<eh-name>";

    @Override
    public void run() {
        final AzureNamedKeyCredential credential = new AzureNamedKeyCredential("RootManageSharedAccessKey",
                "Primary or Secondary Key from the portal");

        final ProxyAuthenticator authenticator = new KerberosProxyAuthenticator(PROXY_HOST);
        final ProxyOptions proxyOptions = new ProxyOptions(authenticator, new Proxy(Proxy.Type.HTTP, new InetSocketAddress(PROXY_HOST, PROXY_PORT)));

        final EventHubProducerClient producer = new EventHubClientBuilder()
                .proxyOptions(proxyOptions)
                .credential(EVENT_HUB_NAMESPACE, EVENT_HUBS_NAME, credential)
                .transportType(AmqpTransportType.AMQP_WEB_SOCKETS)
                .buildProducerClient();

        final EventDataBatch batch = producer.createBatch();
        producer.close();
    }

    /**
     * User plugged-in ProxyAuthenticator implementation for Kerberos authentication.
     *
     * Please be aware that this authenticator implementation is just for demonstration purposes, and it won't guarantee
     * that the way GSS API for Kerberos used are sufficiently secure. Consult expert for the correct and secure use in
     * real world scenarios
     */
    private static final class KerberosProxyAuthenticator implements ProxyAuthenticator {
        private static final String KERBEROS_CALL_BACK_HANDLER = "com.anuchan.messaging.scenarios.KerberosCallBackHandler";
        private static final String NEGOTIATE_SCHEME = "Negotiate";
        private static final String SPNEGO_OID = "1.3.6.1.5.5.2";
        private final String hostname;

        KerberosProxyAuthenticator(String hostname) {
            System.setProperty("java.security.auth.login.config", "login.conf");
            System.setProperty("java.security.krb5.conf", "/etc/krb5.conf");
            System.setProperty("sun.security.krb5.debug", "true");
            System.setProperty("javax.security.auth.useSubjectCredsOnly","false");
            Security.setProperty("auth.login.defaultCallbackHandler", KERBEROS_CALL_BACK_HANDLER);
            this.hostname = Objects.requireNonNull(hostname, "'hostname' cannot be null.");
        }

        @Override
        public String authenticate(ChallengeResponse response) {
            if (!hasNegotiateScheme(response)) {
                throw new UnsupportedOperationException("Expected '" + NEGOTIATE_SCHEME + "' scheme is not in the challenge response.");
            }
            String hostname = this.hostname;
            try {
                final InetAddress in = InetAddress.getByName(hostname);
                final String canonicalServer = in.getCanonicalHostName();
                if (!in.getHostAddress().contentEquals(canonicalServer)) {
                    hostname = canonicalServer;
                }
            } catch (final UnknownHostException ignore){
            }
            String authServer = hostname;
            final GSSManager manager = GSSManager.getInstance();
            final GSSName serverName;
            try {
                serverName = manager.createName("HTTP@" + authServer, GSSName.NT_HOSTBASED_SERVICE);
            } catch (GSSException e) {
                throw new RuntimeException(e);
            }
            final GSSCredential gssCredential = null;
            final Oid oid;
            try {
                oid = new Oid(SPNEGO_OID);
            } catch (GSSException e) {
                throw new RuntimeException(e);
            }
            final GSSContext gssContext;
            try {
                gssContext = manager.createContext(serverName.canonicalize(oid), oid, gssCredential, GSSContext.DEFAULT_LIFETIME);
            } catch (GSSException e) {
                throw new RuntimeException(e);
            }
            try {
                gssContext.requestMutualAuth(true);
            } catch (GSSException e) {
                throw new RuntimeException(e);
            }
            final byte[] token;
            try {
                token = gssContext.initSecContext(new byte[] {}, 0, 0);
            } catch (GSSException e) {
                throw new RuntimeException(e);
            }
            return String.join(" ", NEGOTIATE_SCHEME, Base64.getEncoder().encodeToString(token));
        }

        private static boolean hasNegotiateScheme(ChallengeResponse response) {
            final String supportedScheme = NEGOTIATE_SCHEME.toLowerCase(Locale.ROOT);
            for (String scheme : response.getAuthenticationSchemes()) {
                if (scheme.toLowerCase(Locale.ROOT).startsWith(supportedScheme)) {
                    return true;
                }
            }
            return false;
        }
    }
}
