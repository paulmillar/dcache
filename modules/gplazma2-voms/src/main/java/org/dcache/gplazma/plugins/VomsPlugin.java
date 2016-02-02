package org.dcache.gplazma.plugins;

import eu.emi.security.authn.x509.X509CertChainValidatorExt;
import org.bouncycastle.asn1.x509.AttributeCertificate;
import org.bouncycastle.cert.X509AttributeCertificateHolder;
import org.bouncycastle.openssl.PEMWriter;
import org.bouncycastle.x509.X509AttributeCertificate;
import org.bouncycastle.x509.X509V2AttributeCertificate;
import org.italiangrid.voms.VOMSAttribute;
import org.italiangrid.voms.VOMSValidators;
import org.italiangrid.voms.ac.ACParsingContext;
import org.italiangrid.voms.ac.VOMSACValidator;
import org.italiangrid.voms.ac.VOMSValidationResult;
import org.italiangrid.voms.ac.impl.LeafACLookupStrategy;
import org.italiangrid.voms.store.VOMSTrustStore;
import org.italiangrid.voms.store.VOMSTrustStores;
import org.italiangrid.voms.util.CertificateValidatorBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.security.Principal;
import java.security.cert.CRLException;
import java.security.cert.CertPath;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.Set;

import org.dcache.auth.FQANPrincipal;
import org.dcache.gplazma.AuthenticationException;
import org.dcache.gplazma.util.CertPaths;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Arrays.asList;
import static org.dcache.gplazma.util.Preconditions.checkAuthentication;

/**
 * Validates and extracts FQANs from any X509Certificate certificate chain in
 * the public credentials.
 */
public class VomsPlugin implements GPlazmaAuthenticationPlugin
{
    private static final Logger LOG = LoggerFactory.getLogger(VomsPlugin.class);

    private static final String CADIR = "gplazma.vomsdir.ca";
    private static final String VOMSDIR = "gplazma.vomsdir.dir";
    private static final String SAVE_BAD_AC_ENABLE = "gplazma.voms.save-bad-ac.enable";
    private static final String SAVE_BAD_AC_PATH = "gplazma.voms.save-bad-ac.path-prefix";

    private final String caDir;
    private final String vomsDir;
    private VOMSACValidator validator;
    private final Random random = new Random();
    private final String saveBadAcPath;

    public VomsPlugin(Properties properties)
            throws CertificateException, CRLException, IOException
    {
        caDir = properties.getProperty(CADIR);
        vomsDir = properties.getProperty(VOMSDIR);
        checkArgument(caDir != null, "Undefined property: " + CADIR);
        checkArgument(vomsDir != null, "Undefined property: " + VOMSDIR);
        if (Boolean.valueOf(properties.getProperty(SAVE_BAD_AC_ENABLE))) {
            saveBadAcPath = properties.getProperty(SAVE_BAD_AC_PATH);
        } else {
            saveBadAcPath = null;
        }
    }

    @Override
    public void start()
    {
        VOMSTrustStore vomsTrustStore = VOMSTrustStores.newTrustStore(asList(vomsDir));
        X509CertChainValidatorExt certChainValidator = new CertificateValidatorBuilder().trustAnchorsDir(caDir).build();
        validator = VOMSValidators.newValidator(vomsTrustStore, certChainValidator);
    }

    @Override
    public void stop()
    {
        validator.shutdown();
    }

    public String randomId()
    {
        byte[] rawId = new byte[3]; // a Base64 char represents 6 bits; 4 chars represent 3 bytes.
        random.nextBytes(rawId);
        return Base64.getEncoder().withoutPadding().encodeToString(rawId);
    }

    @Override
    public void authenticate(Set<Object> publicCredentials,
                    Set<Object> privateCredentials,
                    Set<Principal> identifiedPrincipals)
                    throws AuthenticationException
    {
        boolean primary = true;
        boolean hasX509 = false;
        boolean hasFQANs = false;
        String ids = null;
        boolean multipleIds = false;

        for (Object credential : publicCredentials) {
            if (CertPaths.isX509CertPath(credential)) {
                hasX509 = true;
                X509Certificate[] certificates = CertPaths.getX509Certificates((CertPath) credential);
                List<VOMSValidationResult> results = validator.validateWithResult(certificates);
                boolean hasValidationErrors = false;
                for (VOMSValidationResult result : results) {
                    if (result.isValid()) {
                        VOMSAttribute attr = result.getAttributes();

                        for (String fqan : attr.getFQANs()) {
                            hasFQANs = true;
                            identifiedPrincipals.add(new FQANPrincipal(fqan, primary));
                            primary = false;
                        }
                    } else {
                        hasValidationErrors = true;
                        String id = randomId();
                        LOG.warn("Validation failure {}: {}", id, result.getValidationErrors());
                        if (ids == null) {
                            ids = id;
                        } else {
                            ids = ids + ", " + id;
                            multipleIds = true;
                        }
                    }
                }

                if (hasValidationErrors && saveBadAcPath != null) {
                    String filenamePrefix = saveBadAcPath + "-" + randomId();

                    List<ACParsingContext> contexts = new LeafACLookupStrategy().
                            lookupVOMSAttributeCertificates(certificates);
                    int fileCount = 0;
                    for (ACParsingContext context : contexts) {
                        String filename = filenamePrefix + "-" + context.getCertChainPostion() + ".pem";
                        try (Writer writer = new FileWriter(filename)) {
                            context.getACs().stream().
                                    forEach(cert -> {
                                        try {
                                            Base64.Encoder e = Base64.getEncoder();
                                            writer.write("-----BEGIN ATTRIBUTE CERTIFICATE-----\n");

                                            byte[] derEncoded = cert.getDEREncoded();

                                            // Lines are 64-character => 384 bits => 48 bytes
                                            byte[] line = new byte[48];

                                            int c = 0;
                                            while ((c+1)*line.length < derEncoded.length) {
                                                System.arraycopy(derEncoded, c*line.length, line, 0, line.length);
                                                writer.write(e.encodeToString(line));
                                                writer.write("\n");
                                                c++;
                                            }

                                            if (c*line.length < derEncoded.length) {
                                                line = Arrays.copyOfRange(derEncoded, c*line.length, derEncoded.length);
                                                writer.write(e.encodeToString(line));
                                                writer.write("\n");
                                            }

                                            writer.write("-----END ATTRIBUTE CERTIFICATE-----\n");
                                        } catch (IOException e) {
                                            LOG.error("Failed to write Attribute Certificate to {}: {}",
                                                    filename, e.toString());
                                        }});
                            fileCount++;
                        } catch (IOException e) {
                            LOG.error("Failed to write to {}: {}", filename, e.toString());
                        }
                    }

                    if (fileCount > 0) {
                        LOG.warn("Saved VOMS Attribute Certificates in {} file{} that start{} {}",
                                fileCount, (fileCount == 1 ? "" :  "s"), (fileCount == 1 ? "s" :  ""),
                                filenamePrefix);
                    }
                }
            }
        }

        if (ids != null && !hasFQANs) {
            String failure = multipleIds ? "failures" : "failure";
            throw new AuthenticationException("validation " + failure + ": " + ids);
        }
        checkAuthentication(hasX509, "no X509 certificate chain");
        checkAuthentication(hasFQANs, "no FQANs");
    }
}
