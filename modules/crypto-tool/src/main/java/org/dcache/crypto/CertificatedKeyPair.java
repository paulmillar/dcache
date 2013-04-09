package org.dcache.crypto;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.X509Certificate;

/**
  * A simple data class to hold both the public/private key-pair and a
  * certificate.
  */
public class CertificatedKeyPair
{
    private final KeyPair _keypair;
    private final X509Certificate _certificate;

    public CertificatedKeyPair(KeyPair kp, X509Certificate cert)
    {
        _keypair = kp;
        _certificate = cert;
    }

    public PrivateKey getPrivateKey()
    {
        return _keypair.getPrivate();
    }

    public PublicKey getPublicKey()
    {
        return _keypair.getPublic();
    }

    public X509Certificate getCertificate()
    {
        return _certificate;
    }
}
