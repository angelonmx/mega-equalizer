package com.angelonmx.megaequalizer.coverage;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Date;

/**
 * Persistent ADB identity. ADB authenticates clients with an RSA key pair and
 * a self-signed X.509 certificate whose public key is the ADB public key. The
 * certificate is generated on the device the first time it is needed and kept
 * in app-private storage; it never leaves the phone.
 */
public final class AdbIdentity {

    private static final String TAG = "AdbIdentity";
    private static final String KEY_FILE = "adb_identity_key.der";
    private static final String CERT_FILE = "adb_identity_cert.der";
    private static final String SUBJECT = "CN=Mega Equalizer ADB";
    private static final long VALIDITY_MILLIS = 10L * 365L * 24L * 60L * 60L * 1000L;

    private static volatile AdbIdentity cached;

    private final byte[] privateKeyEncoded;
    private final X509Certificate certificate;

    private AdbIdentity(byte[] privateKeyEncoded, X509Certificate certificate) {
        this.privateKeyEncoded = privateKeyEncoded;
        this.certificate = certificate;
    }

    @NonNull
    public static AdbIdentity get(@NonNull Context context) throws Exception {
        AdbIdentity identity = cached;
        if (identity != null) {
            return identity;
        }
        synchronized (AdbIdentity.class) {
            identity = cached;
            if (identity != null) {
                return identity;
            }
            identity = load(context.getApplicationContext());
            cached = identity;
            return identity;
        }
    }

    @NonNull
    private static AdbIdentity load(Context context) throws Exception {
        File keyFile = new File(context.getFilesDir(), KEY_FILE);
        File certFile = new File(context.getFilesDir(), CERT_FILE);
        if (keyFile.isFile() && certFile.isFile()) {
            try {
                byte[] key = read(keyFile);
                // Validate the stored key before using it.
                KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(key));
                X509Certificate cert = readCertificate(certFile);
                return new AdbIdentity(key, cert);
            } catch (Exception error) {
                Log.w(TAG, "La identidad ADB guardada no se pudo leer; se regenera", error);
                // Fall through and create a fresh identity.
            }
        }
        return generate(context, keyFile, certFile);
    }

    @NonNull
    private static AdbIdentity generate(Context context, File keyFile, File certFile) throws Exception {
        Log.i(TAG, "Generando identidad ADB nueva");
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048, new SecureRandom());
        KeyPair keyPair = generator.generateKeyPair();
        Log.i(TAG, "Par de claves RSA generado");

        long now = System.currentTimeMillis();
        X500Name subject = new X500Name(SUBJECT);
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                subject,
                BigInteger.valueOf(now),
                new Date(now - 24L * 60L * 60L * 1000L),
                new Date(now + VALIDITY_MILLIS),
                subject,
                keyPair.getPublic()
        );
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        builder.addExtension(
                Extension.keyUsage,
                true,
                new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment)
        );
        Log.i(TAG, "Construyendo certificado autofirmado");
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .build(keyPair.getPrivate());
        X509Certificate certificate = new JcaX509CertificateConverter()
                .getCertificate(builder.build(signer));
        Log.i(TAG, "Certificado generado; guardando");

        write(keyFile, keyPair.getPrivate().getEncoded());
        write(certFile, certificate.getEncoded());
        Log.i(TAG, "Identidad ADB guardada");
        return new AdbIdentity(keyPair.getPrivate().getEncoded(), certificate);
    }

    @NonNull
    private static X509Certificate readCertificate(File file) throws Exception {
        byte[] bytes = read(file);
        CertificateFactory factory = CertificateFactory.getInstance("X.509");
        return (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(bytes));
    }

    private static byte[] read(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file);
             java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static void write(File file, byte[] bytes) throws IOException {
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(bytes);
            output.getFD().sync();
        }
    }

    /** Removes the stored identity. A new one is generated on the next call. */
    public static synchronized void reset(@NonNull Context context) {
        cached = null;
        File keyFile = new File(context.getFilesDir(), KEY_FILE);
        File certFile = new File(context.getFilesDir(), CERT_FILE);
        //noinspection ResultOfMethodCallIgnored
        keyFile.delete();
        //noinspection ResultOfMethodCallIgnored
        certFile.delete();
    }

    /**
     * Returns a fresh private key instance. LibADB destroys the key when a
     * connection closes, so each connection must get its own copy.
     */
    @NonNull
    public PrivateKey newPrivateKey() {
        try {
            return KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(privateKeyEncoded));
        } catch (Exception error) {
            throw new IllegalStateException("No se pudo cargar la identidad ADB", error);
        }
    }

    @NonNull
    public X509Certificate getCertificate() {
        return certificate;
    }
}
