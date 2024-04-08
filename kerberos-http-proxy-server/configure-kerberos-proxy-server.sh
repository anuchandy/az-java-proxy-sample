#!/usr/bin/env bash

export KERBEROS_USERNAME="anuchan"
export KERBEROS_PASSWORD="1PassWo0rd^*"
export KERBEROS_REALM="azure.sdk.java"
export KERBEROS_PORT="80"
# the suffix of the hostname must be '.{project-name}_{network-name}' hence '.{az-java-proxy-sample}_{shared-network}'
export KERBEROS_HOSTNAME="kerberos-http-proxy-server.az-java-proxy-sample_shared-network"

set -o xtrace
set -x

echo "Setting up kerberos config file at /etc/krb5.conf"
cat > /etc/krb5.conf << EOL
[libdefaults]
    default_realm = ${KERBEROS_REALM^^}
    dns_lookup_realm = false
    dns_lookup_kdc = false
[realms]
    ${KERBEROS_REALM^^} = {
        kdc = $KERBEROS_HOSTNAME
        admin_server = $KERBEROS_HOSTNAME
    }
[domain_realm]
    .$KERBEROS_REALM = ${KERBEROS_REALM^^}
[logging]
    kdc = FILE:/var/log/krb5kdc.log
    admin_server = FILE:/var/log/kadmin.log
    default = FILE:/var/log/krb5lib.log
EOL

echo "Setting up kerberos ACL config at /etc/krb5kdc/kadm5.acl"
mkdir -p /etc/krb5kdc
echo -e "*/*@${KERBEROS_REALM^^}\t*" > /etc/krb5kdc/kadm5.acl

echo "Creating KDC database"
set +e
printf "$KERBEROS_PASSWORD\n$KERBEROS_PASSWORD" | krb5_newrealm
set -e

echo "Creating principals"
kadmin.local -q "addprinc -pw $KERBEROS_PASSWORD $KERBEROS_USERNAME"

echo "Adding principal for kerberos auth and creating keytabs"
kadmin.local -q "addprinc -randkey HTTP/$KERBEROS_HOSTNAME"
kadmin.local -q "ktadd -k /etc/krb5.keytab HTTP/$KERBEROS_HOSTNAME"

chmod 777 /etc/krb5.keytab

echo "Restarting kerberos KDS service"
service krb5-kdc restart

echo "Add ServerName to Apache config"
grep -q -F "ServerName $KERBEROS_HOSTNAME" /etc/apache2/apache2.conf || echo "ServerName $KERBEROS_HOSTNAME" >> /etc/apache2/apache2.conf

echo "Deleting default virtual host files"
rm /etc/apache2/sites-enabled/*.conf
rm /etc/apache2/sites-available/*.conf

echo "Create virtual host files"
cat > /etc/apache2/sites-available/kerberos-proxy.conf << EOL
<VirtualHost *:$KERBEROS_PORT>
    ServerName $KERBEROS_HOSTNAME
    ServerAlias $KERBEROS_HOSTNAME

    ProxyRequests On
    ProxyPreserveHost On

    <Proxy *>
        Order Deny,Allow
        Allow from all

        AuthType GSSAPI
        AuthName "GSSAPI Single Sign On Login"
        Require valid-user
        GssapiCredStore keytab:/etc/krb5.keytab
    </Proxy>

</VirtualHost>
EOL

echo "Enabling virtual host site"
a2ensite kerberos-proxy.conf

echo "Enabling apache modules"
a2enmod proxy
a2enmod proxy_http
a2enmod proxy_http2
a2enmod proxy_connect
a2enmod ssl
a2enmod headers
service apache2 restart

echo "KERBEROS PROXY RUNNING"
# show apache logs to keep container running
tail -f /var/log/apache2/error.log