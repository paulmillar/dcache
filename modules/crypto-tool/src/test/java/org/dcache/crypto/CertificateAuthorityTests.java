/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.dcache.crypto;

import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 *
 * @author paul
 */
public class CertificateAuthorityTests
{
    CertificateAuthority _ca;

    @Before
    public void setup()
    {
        try {
            _ca = new CertificateAuthority();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e.getMessage(), e);
        } catch (NoSuchProviderException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Test
    public void testCreateServerCredential() throws CertificateExpiredException,
            CertificateNotYetValidException
    {
        final String hostname = "www.dcache.org";

        CertificatedKeyPair credentials =
                _ca.createServerCredentials(hostname);
        assertCertificatedKeyPairValid(credentials);
        assertNotCertificateAuthority(credentials.getCertificate());

        assertTrue(credentials.getCertificate().getSubjectDN().toString().contains(hostname));

        assertPEMSerialiseDeserialiseEquals(credentials.getCertificate());
    }

    
    @Test
    public void testGetCaCertificate()
    {
        X509Certificate certificate = _ca.getCertificate();

        assertNotNull(certificate);

        assertPEMSerialiseDeserialiseEquals(certificate);
    }


    @Test
    public void testCreateEECCredential()
    {
        final String name = "Arthur Philip Dent";

        CertificatedKeyPair credentials = _ca.generateEEC(name);
        assertCertificatedKeyPairValid(credentials);
        assertNotCertificateAuthority(credentials.getCertificate());

        String dn = credentials.getCertificate().getSubjectDN().toString();
        assertTrue(dn.contains(name));
        
        assertPEMSerialiseDeserialiseEquals(credentials.getCertificate());
    }


    public void assertCertificatedKeyPairValid(CertificatedKeyPair credentials)
    {
        assertNotNull(credentials);
        assertNotNull(credentials.getPublicKey());
        assertNotNull(credentials.getPrivateKey());
        assertNotNull(credentials.getCertificate());

        PublicKey publicKey = credentials.getPublicKey();
        PrivateKey privateKey = credentials.getPrivateKey();

        assertEquals(publicKey.getAlgorithm(), privateKey.getAlgorithm());

        // show that public and private key work together

        X509Certificate cert = credentials.getCertificate();
        try {
            cert.checkValidity();
        } catch (CertificateExpiredException e) {
            fail(e.getMessage());
        } catch (CertificateNotYetValidException e) {
            fail(e.getMessage());
        }

        assertEquals(_ca.getIssuingPrincipal(), cert.getIssuerX500Principal());
    }
    
    public static void assertNotCertificateAuthority(X509Certificate certificate)
    {
        assertEquals(-1, certificate.getBasicConstraints());
    }

    public static void assertPEMSerialiseDeserialiseEquals(X509Certificate original)
    {
        String data = PemUtil.toString(original);

        X509Certificate parsed = PemUtil.parseAsX509Certificate(data);

        assertEquals(original, parsed);
    }
}
