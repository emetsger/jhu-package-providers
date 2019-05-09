/*
 *
 *  * Copyright 2019 Johns Hopkins University
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package edu.jhu.library.pass.deposit.provider.bagit;

import org.dataconservancy.pass.client.PassClient;
import org.dataconservancy.pass.deposit.assembler.PackageOptions;
import org.dataconservancy.pass.deposit.assembler.PackageStream;
import org.dataconservancy.pass.deposit.assembler.shared.DepositFileResource;
import org.dataconservancy.pass.deposit.assembler.shared.PackageProvider;
import org.dataconservancy.pass.deposit.model.DepositSubmission;
import org.dataconservancy.pass.model.Submission;
import org.dataconservancy.pass.model.User;
import org.joda.time.format.ISODateTimeFormat;
import org.springframework.core.io.Resource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static java.net.URI.create;

public class BagItPackageProvider implements PackageProvider {

    protected static final UnsupportedOperationException UOE =
            new UnsupportedOperationException("Representation only exists in-memory.");

    /**
     * Package options key that contains the classpath resource path of the {@code bag-info.txt} Handlebars template
     */
    protected static final String BAGINFO_TEMPLATE = "baginfo-template-resource";

    /**
     * Payload directory
     * https://www.rfc-editor.org/rfc/rfc8493.html#section-2.1.2
     */
    protected static final String PAYLOAD_DIR = "data";

    /**
     * Payload manifest (at least one)
     * https://www.rfc-editor.org/rfc/rfc8493.html#section-2.1.3
     */
    protected static final String PAYLOAD_MANIFEST_TMPL = "manifest-%s.txt";

    /**
     * Tagfile manifest (optional)
     * https://www.rfc-editor.org/rfc/rfc8493.html#section-2.2.1
     */
    protected static final String TAG_MANIFEST_TMPL = "tagmanifest-%s.txt";

    /**
     * bagit.txt file (required)
     * https://www.rfc-editor.org/rfc/rfc8493.html#section-2.1.1
     */
    protected static final String BAGIT_TXT = "bagit.txt";

    /**
     * bag-info.txt file (optional)
     * https://www.rfc-editor.org/rfc/rfc8493.html#section-2.2.2
     */
    protected static final String BAGINFO_TXT = "bag-info.txt";

    /**
     * fetch.txt (optional)
     * https://www.rfc-editor.org/rfc/rfc8493.html#section-2.2.3
     */
    protected static final String FETCH_TXT = "fetch.txt";

    /**
     * Tag file encoding
     * https://www.rfc-editor.org/rfc/rfc8493.html#section-2.1.1
     */
    protected Charset tagFileEncoding = StandardCharsets.UTF_8;

    /**
     * Supported BagIT version
     * https://www.rfc-editor.org/rfc/rfc8493.html#section-2.1.1
     */
    protected BagItVersion bagItVersion = BagItVersion.BAGIT_1_0;

    /**
     * Default checksum calculation algorithm when generating new Bags.
     * https://www.rfc-editor.org/rfc/rfc8493.html#section-2.4
     */
    protected BagAlgo defaultAlgo = BagAlgo.SHA512;

    /**
     * Whether or not this implementation will produce incomplete Bags.
     */
    protected FetchStrategy fetchStrategy = FetchStrategy.DISABLED;

    /**
     * Writer for Bag-related files
     */
    protected BagItWriter writer;

    /**
     * Runtime options provided to the Packager/Assembler
     */
    protected Map<String, Object> packageOpts;

    /**
     * PASS Repository client, used for resolving URI references in the Submission
     */
    protected PassClient passClient;

    /**
     * Handlebars parameterization of Bag metadata
     */
    protected Parameterizer parameterizer;

    public BagItPackageProvider(BagItWriter writer, Parameterizer parameterizer, PassClient passClient) {
        this.writer = writer;
        this.parameterizer = parameterizer;
        this.passClient = passClient;
    }

    @Override
    public void start(DepositSubmission submission, List<DepositFileResource> custodialResources,
                      Map<String, Object> packageOptions) {
        this.packageOpts = packageOptions;
    }

    /**
     * Answers a path for the custodial resource subordinate to the BagIt payload directory.
     * <p>
     * The existing path of the custodial resource is preserved, and prefixed with "{@code data/}".
     * </p>
     *
     * @param custodialResource the custodial resource (i.e. a resource that is part of the Bag payload)
     * @return the path of the resource prefixed with "{@code data/}"
     * @throws RuntimeException if there is an error obtaining the path of the custodial resource
     */
    @Override
    public String packagePath(DepositFileResource custodialResource) {
        // payload directory: https://www.rfc-editor.org/rfc/rfc8493.html#section-2.1.2
        try {
            return String.format("%s/%s", PAYLOAD_DIR, BagItWriter.encodePath(custodialResource.getFilename()));
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    public List<SupplementalResource> finish(DepositSubmission submission,
                                             List<PackageStream.Resource> packageResources) {

        List<SupplementalResource> supplementalResources =
                new ArrayList<>(writePayloadManifests(submission, packageResources, packageOpts));
        supplementalResources.add(writeBagDeclaration());
        supplementalResources.add(writeBagInfo(submission, packageResources,
                this.getClass().getResourceAsStream((String)packageOpts.get(BAGINFO_TEMPLATE))));

        return supplementalResources;
    }

    /**
     * Writes a payload manifest for each checksum supplied in the packager options.
     *
     * @param submission the submission in the Deposit Services model
     * @param packageResources the custodial files being streamed in the package
     * @param packageOptions the options supplied to the Assembler when creating the package
     * @return the BagIt payload manifests
     */
    @SuppressWarnings("unchecked")
    protected Collection<SupplementalResource> writePayloadManifests(DepositSubmission submission,
                                                    List<PackageStream.Resource> packageResources,
                                                    Map<String, Object> packageOptions) {

        // Generate a payload manifest for each checksum in the package options
        Collection<PackageOptions.Checksum.OPTS> checksums = (Collection<PackageOptions.Checksum.OPTS>)
                packageOptions.get(PackageOptions.Checksum.KEY);

        List<SupplementalResource> manifests = new ArrayList<>(checksums.size());

        checksums.forEach(checksum -> {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            BagAlgo algo = BagAlgo.valueOf(checksum.name());

            packageResources.forEach(resource -> {

                PackageStream.Checksum resourceChecksum = resource.checksums().stream()
                        .filter(candidate -> candidate.algorithm() == checksum)
                        .findAny()
                        .orElseThrow(() ->
                                new RuntimeException("Missing " + checksum.name() + " checksum for " + resource.name()));

                try {
                    writer.writeManifestLine(out, resourceChecksum.asHex(), resource.name());
                } catch (IOException e) {
                    throw new RuntimeException("Error writing manifest: " + e.getMessage(), e);
                }

            });

            manifests.add(new SupplementalResource() {
                @Override
                public String getPackagePath() {
                    return String.format(PAYLOAD_MANIFEST_TMPL, algo.getAlgo());
                }

                @Override
                public boolean exists() {
                    return true;
                }

                @Override
                public URL getURL() throws IOException {
                    throw UOE;
                }

                @Override
                public URI getURI() throws IOException {
                    throw UOE;
                }

                @Override
                public File getFile() throws IOException {
                    throw UOE;
                }

                @Override
                public long contentLength() throws IOException {
                    return out.size();
                }

                @Override
                public long lastModified() throws IOException {
                    return System.currentTimeMillis();
                }

                @Override
                public Resource createRelative(String s) throws IOException {
                    throw UOE;
                }

                @Override
                public String getFilename() {
                    return getPackagePath();
                }

                @Override
                public String getDescription() {
                    return "Bag payload manifest for checksum algorithm " + algo.getAlgo();
                }

                @Override
                public InputStream getInputStream() throws IOException {
                    return new ByteArrayInputStream(out.toByteArray());
                }
            });
        });

        return manifests;

    }

    protected SupplementalResource writeBagInfo(DepositSubmission submission,
                                                List<PackageStream.Resource> packageResources,
                                                InputStream bagInfoMustacheTemplate) {

        BagModel model = new BagModel();

        long streamCount = packageResources.size();
        long octetCount = packageResources.stream().mapToLong(PackageStream.Resource::sizeBytes).sum();

        Submission passSubmission = passClient.readResource(create(submission.getId()), Submission.class);
        model.setSubmission(passSubmission);
        model.setDepositSubmission(submission);
        model.setSubmissionUri(submission.getId());
        model.setBagItVersion(bagItVersion.getVersionString());
        model.setBagSizeBytes(octetCount);
        model.setCustodialFileCount(streamCount);
        model.setSubmissionMetadata(passSubmission.getMetadata());
        model.setSubmissionDate(ISODateTimeFormat.basicDateTimeNoMillis().print(passSubmission.getSubmittedDate()));

        User passUser = passClient.readResource(passSubmission.getSubmitter(), User.class);
        model.setSubmissionUser(passUser);
        model.setSubmissionUri(passUser.getId().toString());
        model.setSubmissionUserEmail(passUser.getEmail());
        model.setSubmissionUserFullName(passUser.getDisplayName());

        if (submission.getMetadata().getArticleMetadata().getDoi() != null) {
            model.setPublisherId(submission.getMetadata().getArticleMetadata().getDoi().toString());
        }

        String sizeTmpl = "%s %s";
        String size;
        String unit;
        if (octetCount < BagMetadata.ONE_KIBIBYTE) {
            size = String.valueOf(octetCount);
            unit = "bytes";
        } else if (octetCount < BagMetadata.ONE_MEBIBYTE) {
            size = String.valueOf(Math.floorDiv(octetCount, BagMetadata.ONE_KIBIBYTE));
            unit = "KiB";
        } else if (octetCount < BagMetadata.ONE_GIBIBYTE) {
            size = String.valueOf(Math.floorDiv(octetCount, BagMetadata.ONE_MEBIBYTE));
            unit = "MiB";
        } else if (octetCount < BagMetadata.ONE_TEBIBYTE) {
            size = String.valueOf(Math.floorDiv(octetCount, BagMetadata.ONE_GIBIBYTE));
            unit = "GiB";
        } else {
            size = String.valueOf(Math.floorDiv(octetCount, BagMetadata.ONE_TEBIBYTE));
            unit = "TiB";
        }
        model.setBagSizeHumanReadable(String.format(sizeTmpl, size, unit));

        String bagInfo = parameterizer.parameterize(bagInfoMustacheTemplate, model);

        return new SupplementalResource() {
            @Override
            public String getPackagePath() {
                return BAGINFO_TXT;
            }

            @Override
            public boolean exists() {
                return true;
            }

            @Override
            public URL getURL() throws IOException {
                throw UOE;
            }

            @Override
            public URI getURI() throws IOException {
                throw UOE;
            }

            @Override
            public File getFile() throws IOException {
                throw UOE;
            }

            @Override
            public long contentLength() throws IOException {
                return bagInfo.getBytes(tagFileEncoding).length;
            }

            @Override
            public long lastModified() throws IOException {
                return System.currentTimeMillis();
            }

            @Override
            public Resource createRelative(String relativePath) throws IOException {
                throw UOE;
            }

            @Override
            public String getFilename() {
                return BAGINFO_TXT;
            }

            @Override
            public String getDescription() {
                return "Bag Metadata";
            }

            @Override
            public InputStream getInputStream() throws IOException {
                return new ByteArrayInputStream(bagInfo.getBytes(tagFileEncoding));
            }
        };
    }

    /**
     * Answers a Bag Declaration according to version 1.0 of the BagIt specification.
     *
     * @return the Bag Declaration
     * @throws RuntimeException if there is an error writing the declaration
     */
    protected SupplementalResource writeBagDeclaration() {

        ByteArrayOutputStream bagDecl = new ByteArrayOutputStream();

        try {
            writer.writeTagLine(bagDecl, BagMetadata.BAGIT_VERSION, bagItVersion.getVersionString());
            writer.writeTagLine(bagDecl, BagMetadata.TAG_FILE_ENCODING, tagFileEncoding.name());
        } catch (IOException e) {
            throw new RuntimeException("Error writing Bag Declaration: " + e.getMessage(), e);
        }

        return new SupplementalResource() {
            @Override
            public String getPackagePath() {
                return BAGIT_TXT;
            }

            @Override
            public boolean exists() {
                return true;
            }

            @Override
            public URL getURL() throws IOException {
                throw UOE;
            }

            @Override
            public URI getURI() throws IOException {
                throw UOE;
            }

            @Override
            public File getFile() throws IOException {
                throw UOE;
            }

            @Override
            public long contentLength() throws IOException {
                return bagDecl.size();
            }

            @Override
            public long lastModified() throws IOException {
                return System.currentTimeMillis();
            }

            @Override
            public Resource createRelative(String s) throws IOException {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getFilename() {
                return BAGIT_TXT;
            }

            @Override
            public String getDescription() {
                return "Bag Declaration";
            }

            @Override
            public InputStream getInputStream() throws IOException {
                return new ByteArrayInputStream(bagDecl.toByteArray());
            }
        };

    }


}
