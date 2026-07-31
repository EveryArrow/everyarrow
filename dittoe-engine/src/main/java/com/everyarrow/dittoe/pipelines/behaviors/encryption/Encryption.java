package com.everyarrow.dittoe.pipelines.behaviors.encryption;

import com.everyarrow.dittoe.io.data.DataStoreInterface;
import com.everyarrow.dittoe.io.file.FileType;
import com.everyarrow.dittoe.pipelines.behaviors.StagedBehaviorBase;
import com.everyarrow.dittoe.pipelines.behaviors.StagedBehaviorInput;
import com.everyarrow.dittoe.pipelines.execution.abstraction.BehaviorException;
import org.everyarrow.dittoe.schema.BehaviorType;
import org.everyarrow.dittoe.schema.PipelineMetadata;
import org.everyarrow.dittoe.schema.StageType;
import com.everyarrow.dittoe.utility.Utils;
import com.everyarrow.dittoe.utility.security.SafePathResolver;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;

public class Encryption extends StagedBehaviorBase {
    static final String CONTENT_ALGORITHM = "AES/GCM/NoPadding";
    static final String KEY_WRAP_ALGORITHM = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final String ENVELOPE_VERSION = "DITTOE-ENCRYPTED-V1";
    private static final int AES_KEY_BYTES = 32;
    private static final int GCM_NONCE_BYTES = 12;
    private static final int GCM_TAG_BITS = 128;

    public Encryption(String name, String description, Map<String, String> properties,
                      PipelineMetadata pipelineMetadata, DataStoreInterface dataStore) {
        super(new StagedBehaviorInput(name,
                BehaviorType.Formatting, properties, description, Integer.MAX_VALUE,
                StageType.Package, pipelineMetadata, dataStore));
    }

    @Override
    public void exec() throws BehaviorException {
        try {
            String tempDirectory = this.getPipelineMetadata().getGlobalProperties().get("temp_directory");
            FileType fileExtension =
                    FileType.getFileType(this.getPipelineMetadata().getTargetResourceToProcess().getResourceProperties());
            String resourceName = this.getPipelineMetadata().getTargetResourceToProcess().getResourceName();
            String fileName = resourceName.concat(Utils.hasBehavior(this.getPipelineMetadata(),
                    BehaviorType.Compression) ? "-compressed" : "-formatted").concat(".").concat(fileExtension.name().toLowerCase());
            String publicKeyStr = this.getProperties().get("public_key");
            PublicKey publicKey = getPublicKeyFromString(publicKeyStr);
            Path inputFile = SafePathResolver.resolveUnderBase(tempDirectory, fileName);
            Path outputFile = SafePathResolver.resolveUnderBase(tempDirectory,
                    this.getPipelineMetadata().getTargetResourceToProcess().getResourceName() + "-encrypted.dat");

            SecureRandom random = new SecureRandom();
            byte[] symmetricKeyBytes = new byte[AES_KEY_BYTES];
            byte[] nonce = new byte[GCM_NONCE_BYTES];
            random.nextBytes(symmetricKeyBytes);
            random.nextBytes(nonce);

            Cipher rsaCipher = Cipher.getInstance(KEY_WRAP_ALGORITHM);
            rsaCipher.init(Cipher.ENCRYPT_MODE, publicKey, new OAEPParameterSpec(
                    "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT));
            byte[] encryptedSymmetricKey = rsaCipher.doFinal(symmetricKeyBytes);

            SecretKey key = new SecretKeySpec(symmetricKeyBytes, "AES");
            Cipher aesCipher = Cipher.getInstance(CONTENT_ALGORITHM);
            aesCipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_BITS, nonce));

            Files.createDirectories(outputFile.getParent());
            try (BufferedInputStream input = new BufferedInputStream(Files.newInputStream(inputFile));
                 OutputStream output = new BufferedOutputStream(Files.newOutputStream(outputFile));
                 CipherOutputStream cipherOutput = new CipherOutputStream(output, aesCipher)) {
                output.write(envelopeHeader(encryptedSymmetricKey, nonce).getBytes(StandardCharsets.UTF_8));
                input.transferTo(cipherOutput);
            }
        } catch (Exception e) {
            throw new BehaviorException(e);
        }
    }

    public static String envelopeHeader(byte[] encryptedSymmetricKey, byte[] nonce) {
        return ENVELOPE_VERSION + "\n"
                + "keyAlgorithm=" + KEY_WRAP_ALGORITHM + "\n"
                + "contentAlgorithm=" + CONTENT_ALGORITHM + "\n"
                + "nonce=" + Base64.getEncoder().encodeToString(nonce) + "\n"
                + "wrappedKey=" + Base64.getEncoder().encodeToString(encryptedSymmetricKey) + "\n\n";
    }

    private PublicKey getPublicKeyFromString(String key) throws NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] data = Base64.getDecoder().decode(key.replace("\n", "").trim().getBytes(StandardCharsets.UTF_8));
        X509EncodedKeySpec spec = new X509EncodedKeySpec(data);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return keyFactory.generatePublic(spec);
    }
}
