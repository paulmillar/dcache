package org.dcache.crypto;

import java.security.cert.X509Certificate;
import java.util.List;

/**
 * Emulate a VOMS server.
 */
public class VOMSServer
{
    private static final String DEFAULT_DN =
            "DC=org, DC=dCache, OU=Unit-testing, CN=Simple CA";

    private CertificatedKeyPair _credentials;

    public VOMSServer(CertificateAuthority ca)
    {
        this(ca, DEFAULT_DN);
    }
    
    public VOMSServer(CertificateAuthority ca, String dn)
    {
        _credentials = ca.createServerCredentials(dn);
    }

    public X509Certificate createSignedCertificate(CertificatedKeyPair ckp,
            List<String> fqan)
    {
        // FIXME needs implementing.
        return null;
    }
}
