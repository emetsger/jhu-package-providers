/*
 * Copyright 2019 Johns Hopkins University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.jhu.library.pass.deposit.provider.bagit;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.MessageDigestCalculatingInputStream;
import org.apache.commons.io.output.NullOutputStream;
import org.dataconservancy.pass.deposit.assembler.PackageOptions;
import org.dataconservancy.pass.deposit.assembler.shared.ExplodedPackage;
import org.dataconservancy.pass.deposit.assembler.shared.PackageVerifier;
import org.dataconservancy.pass.deposit.model.DepositFile;
import org.dataconservancy.pass.deposit.model.DepositSubmission;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.codec.binary.Hex.encodeHexString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class BagItPackageVerifier implements PackageVerifier {

    private BagItReader reader;

    private Charset expectedEncoding = UTF_8;

    public BagItPackageVerifier() {
        this.reader = new BagItReader(expectedEncoding);
    }

    public BagItPackageVerifier(BagItReader reader) {
        this.reader = reader;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void verify(DepositSubmission depositSubmission, ExplodedPackage explodedPackage, Map<String, Object> map)
            throws Exception {

        // Directory under which the payload (i.e. custodial content of the submission) will be found
        final File payloadDir = new File(explodedPackage.getExplodedDir(), BagItPackageProvider.PAYLOAD_DIR);
        assertTrue("Missing payload directory: " + payloadDir, payloadDir.exists());

        // Maps payload file to a DepositFile
        final BiFunction<File, File, DepositFile> MAPPER = (packageDir, payloadFile) -> {
            return depositSubmission.getFiles()
                    .stream()
                    .filter(df -> df.getLocation().endsWith(payloadFile.getName()))
                    .findAny()
                    .orElseThrow(() -> new RuntimeException("Missing custodial file '" + payloadFile + "'"));
        };

        // Filters for all files that have the payload directory as an ancestor (i.e. all files under "data/")
        final FileFilter payloadFilter = (file) -> {
            if (!file.isFile()) {
                return false;
            }
            File parent = (file.getParentFile() != null) ? file.getParentFile() : file;
            do {
                if (parent.equals(payloadDir)) {
                    return true;
                }
            } while ((parent = parent.getParentFile()) != null);

            return false;
        };

        // Insure that every file in the DepositSubmission is present in the payload, and that every payload file is
        // present in the DepositSubmission
        verifyCustodialFiles(depositSubmission, explodedPackage.getExplodedDir(), payloadFilter, MAPPER);

        // Verify the payload manifest for each checksum in the package options:
        //   Every file in the payload is found in the manifest
        //   Every entry in the manifest is present in the payload
        //   The checksum in the manifest matches the calculated checksum
        List<PackageOptions.Checksum.OPTS> checksums = (List<PackageOptions.Checksum.OPTS>)
                map.get(PackageOptions.Checksum.KEY);
        // must be at least one checksum specified in the package options
        assertTrue("Package options must specify at least one checksum.", checksums.size() > 0);
        checksums.forEach(algorithm -> {
            File manifest = new File(explodedPackage.getExplodedDir(),
                    String.format(BagItPackageProvider.PAYLOAD_MANIFEST_TMPL, algorithm.name().toLowerCase()));
            verifyManifest(depositSubmission.getFiles(), explodedPackage.getExplodedDir(), manifest, algorithm);
        });

        // Bag Decl
        File bagDecl = new File(explodedPackage.getExplodedDir(), BagItPackageProvider.BAGIT_TXT);
        verifyBagDecl(bagDecl, BagItVersion.BAGIT_1_0.getVersionString());

        // Bag Info
        File bagInfo = new File(explodedPackage.getExplodedDir(), BagItPackageProvider.BAGINFO_TXT);
        verifyBagInfo(bagInfo);

    }

    protected void verifyBagInfo(File bagInfo) {
        Map<String, List<String>> entries;

        try {
            entries = reader.readLabelsAndValues(new FileInputStream(bagInfo));
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Missing Bag info file: '" + bagInfo + "'", e);
        }

        // TODO verify expected contents of bag info
        assertTrue(entries.size() > 0);
    }

    protected void verifyBagDecl(File bagDecl, String expectedVersion) {
        Map<String, String> entries;
        try {
            entries = reader.readBagDecl(new FileInputStream(bagDecl));
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Missing Bag declaration file: '" + bagDecl + "'", e);
        }

        assertEquals(expectedVersion, entries.get(BagMetadata.BAGIT_VERSION));

        assertEquals(expectedEncoding.name(), entries.get(BagMetadata.TAG_FILE_ENCODING));


    }

    protected void verifyManifest(List<DepositFile> payload, File packageDir, File manifestFile, PackageOptions.Checksum.OPTS algo) {

        // Insure the manifest file exists
        assertTrue(manifestFile.exists());

        // verify name of the manifest file conforms to the spec
        assertEquals(String.format(BagItPackageProvider.PAYLOAD_MANIFEST_TMPL,
                algo.name().toLowerCase()), manifestFile.getName());

        // Read it in.
        Map<String, String> manifest;
        try {
            manifest = reader.readManifest(new FileInputStream(manifestFile));
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Error reading manifest " + manifestFile + ": " + e.getMessage(), e);
        }

        // make sure each payload file is represented in the manifest
        payload.forEach(df -> {
            String relative = BagItPackageProvider.PAYLOAD_DIR +
                    df.getLocation().substring(df.getLocation().lastIndexOf("/"));
            File expectedPayloadFile = new File(packageDir, relative);
            assertTrue(expectedPayloadFile.exists());
            assertTrue("Missing file '" + relative + "' from the manifest.", manifest.containsKey(relative));
        });

        // make sure each file in the manifest is present in the payload
        manifest.keySet().forEach(expectedPayloadFile -> {
            assertTrue(new File(packageDir, expectedPayloadFile).exists());
        });

        // verify checksum of each payload file in the manifest
        manifest.forEach((key, value) -> {
            File payloadFile = new File(packageDir, key);
            try (NullOutputStream nullOut = new NullOutputStream();
                 FileInputStream fileIn = new FileInputStream(payloadFile);
                 MessageDigestCalculatingInputStream xsumCalculator = checksumCalculatorFor(fileIn, algo)) {
                IOUtils.copy(xsumCalculator, nullOut);
                assertEquals(value, encodeHexString(xsumCalculator.getMessageDigest().digest()));
            } catch (FileNotFoundException e) {
                throw new RuntimeException("Missing expected payload file: " + e.getMessage(), e);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

    }

    private static MessageDigestCalculatingInputStream checksumCalculatorFor(InputStream payloadFile,
                                                                           PackageOptions.Checksum.OPTS checksumAlgo) {
        MessageDigest md;

        try {
            switch (checksumAlgo) {
                case MD5:
                    md = MessageDigest.getInstance("MD5");
                    break;
                case SHA256:
                    md = MessageDigest.getInstance("SHA-256");
                    break;
                case SHA512:
                    md = MessageDigest.getInstance("SHA-512");
                    break;
                default:
                    throw new RuntimeException("No MessageDigest implementation found for " + checksumAlgo.name());

            }
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
        return new MessageDigestCalculatingInputStream(payloadFile, md);
    }
}
