package com.anuchan.messaging.scenarios;

import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;

public class KerberosCallBackHandler implements CallbackHandler {
    private static final String PROXY_USER = "anuchan";
    private static final String PROXY_PASSWORD = "1PassWo0rd^*";

    public void handle(Callback[] callbacks) throws UnsupportedCallbackException {
        for (Callback callback : callbacks) {
            if (callback instanceof NameCallback) {
                NameCallback nc = (NameCallback) callback;
                nc.setName(PROXY_USER);
            } else if (callback instanceof PasswordCallback) {
                PasswordCallback pc = (PasswordCallback) callback;
                pc.setPassword(PROXY_PASSWORD.toCharArray());
            } else {
                throw new UnsupportedCallbackException(callback, "Unknown Callback");
            }

        }
    }
}

