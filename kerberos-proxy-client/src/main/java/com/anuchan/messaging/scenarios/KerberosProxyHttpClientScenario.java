package com.anuchan.messaging.scenarios;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthSchemeProvider;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.AuthSchemes;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.impl.auth.SPNegoSchemeFactory;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.springframework.stereotype.Service;

import java.io.Closeable;
import java.security.Security;
import java.util.concurrent.TimeUnit;

/**
 * Code uses Apache HttpClient to make a request to a target URL via a Kerberos authenticated HTTP proxy.
 */
@Service
public class KerberosProxyHttpClientScenario extends ProxyScenario {
    private static final String PROXY_HOST = "kerberos-http-proxy-server";
    private static final int PROXY_PORT = 80;
    static final String PROXY_USER = "anuchan";
    static final String PROXY_PASSWORD = "1PassWo0rd^*";

    @Override
    public void run() {
        System.setProperty("java.security.auth.login.config", "login.conf");
        System.setProperty("java.security.krb5.conf", "/etc/krb5.conf");
        System.setProperty("sun.security.krb5.debug", "true");
        System.setProperty("javax.security.auth.useSubjectCredsOnly","false");
        Security.setProperty("auth.login.defaultCallbackHandler", "com.anuchan.messaging.scenarios.KerberosCallBackHandler");

        final CredentialsProvider credentialProvider = new BasicCredentialsProvider();
        credentialProvider.setCredentials(new AuthScope(PROXY_HOST, PROXY_PORT), new UsernamePasswordCredentials(PROXY_USER, PROXY_PASSWORD));
        final Registry<AuthSchemeProvider> authSchemeRegistry = RegistryBuilder
                .<AuthSchemeProvider> create()
                .register(AuthSchemes.SPNEGO, new SPNegoSchemeFactory(true, true))
                .build();
        final HttpHost proxy = new HttpHost(PROXY_HOST, PROXY_PORT, "http");

        final int timeout = 5*60*1000; // 5-min
        final RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(timeout)
                .setConnectionRequestTimeout(timeout)
                .setSocketTimeout(timeout)
                .build();

        final CloseableHttpClient httpclient = HttpClients.custom()
                .setDefaultCredentialsProvider(credentialProvider)
                .setDefaultAuthSchemeRegistry(authSchemeRegistry)
                .setRoutePlanner(new DefaultProxyRoutePlanner(proxy))
                .setRetryHandler(new DefaultHttpRequestRetryHandler(0, true))
                .setDefaultRequestConfig(config)
                .build();

        // final HttpUriRequest request = createRequest("https://google.com", "application/json");
        final HttpUriRequest request = createRequest("https://google.com", null);
        CloseableHttpResponse response = null;
        try{
            response = httpclient.execute(request);
            String value = new BasicResponseHandler().handleResponse(response);
            System.out.println("Response from " + request.getURI() + " via proxy (" + PROXY_HOST + ":" + PROXY_PORT + ")");
            System.out.println(value);
        } catch (Exception ex){
            ex.printStackTrace();
        } finally {
            close(response);
            close(httpclient);
        }

        // sleep for some time to hold the container from exiting.
        sleep(5);
    }

    private void close(Closeable toClose) {
        if (toClose != null) {
            try {
                toClose.close();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    private void sleep(int seconds) {
        try {
            TimeUnit.SECONDS.sleep(seconds);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private HttpUriRequest createRequest(String target, String accept) {
        final HttpUriRequest request = new HttpGet(target);
        if (accept != null) {
            request.setHeader(HttpHeaders.ACCEPT, accept);
        }
        return request;
    }
}
