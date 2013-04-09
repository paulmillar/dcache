package org.dcache.crypto;

import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.X509Extensions;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import org.bouncycastle.x509.X509V3CertificateGenerator;

import static org.dcache.crypto.BouncyCastleConstants.BOUNCYCASTLE_NAME;

/**
 * Utility class for working with X509V3CertificateGenerator
 */
public class CertGeneratorUtil
{

    public static final int EEC_KEY_USAGE = KeyUsage.dataEncipherment |
            KeyUsage.digitalSignature | KeyUsage.nonRepudiation |
            KeyUsage.keyEncipherment;



    private CertGeneratorUtil()
    {
        // cannot instantate
    }

    /**
     * Invoke X509Certificate#generate with given private key and BouncyCastle
     * as the provider.  Wraps all exceptions as a RuntimeException
     */
    public static X509Certificate generateCertificate(PrivateKey privateKey,
            X509V3CertificateGenerator generator)
    {
        X509Certificate cert;

        try {
            cert = generator.generate(privateKey, BOUNCYCASTLE_NAME);
        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        } catch (NoSuchProviderException e) {
            throw new RuntimeException(e);
        } catch (SecurityException e) {
            throw new RuntimeException(e);
        } catch (SignatureException e) {
            throw new RuntimeException(e);
        } catch (CertificateEncodingException e) {
            throw new RuntimeException(e);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }

        return cert;
    }


    public static void configureForEEC(X509V3CertificateGenerator generator)
    {
        generator.addExtension(X509Extensions.BasicConstraints, true,
                new BasicConstraints(false));
        generator.addExtension(X509Extensions.ExtendedKeyUsage, true,
                new ExtendedKeyUsage(KeyPurposeId.id_kp_clientAuth));

        generator.addExtension(X509Extensions.KeyUsage, true,
                new KeyUsage(EEC_KEY_USAGE));
    }

}
