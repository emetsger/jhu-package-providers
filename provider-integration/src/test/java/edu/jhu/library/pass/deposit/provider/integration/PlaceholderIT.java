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
package edu.jhu.library.pass.deposit.provider.integration;

import edu.jhu.library.pass.deposit.provider.j10p.DspaceMetsPackageVerifier;
import org.dataconservancy.pass.deposit.assembler.PackageOptions;
import org.dataconservancy.pass.deposit.assembler.shared.ExplodedPackage;
import org.dataconservancy.pass.deposit.assembler.shared.PackageVerifier;
import org.dataconservancy.pass.deposit.integration.shared.SubmitAndValidatePackagesIT;
import org.dataconservancy.pass.deposit.model.DepositSubmission;
import org.dataconservancy.pass.deposit.provider.nihms.NihmsPackageVerifier;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.File;

import static org.junit.Assert.fail;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = SpringContextConfiguration.class)
public class PlaceholderIT extends SubmitAndValidatePackagesIT {

    private static final Logger LOG = LoggerFactory.getLogger(PlaceholderIT.class);

    // TODO: Submit to NIHMS Repository using Filesystem Transport and verify package

    // TODO: Submit to J10P Repository using Filesystem Transport and verify package

    // TODO: Submit to J10P Repository using SWORD Transport and verify logical success (separate IT)

    // TODO: Submit to NIHMS Repository using FTP Transport and verify logical success (separate IT)

    private DspaceMetsPackageVerifier dspaceVerifier;

    private NihmsPackageVerifier nihmsVerifier;

    @Before
    public void setUp() throws Exception {
        dspaceVerifier = new DspaceMetsPackageVerifier(PackageOptions.Checksum.OPTS.SHA512);
        nihmsVerifier = new NihmsPackageVerifier();
    }

    @Override
    protected PackageVerifier getVerifier(DepositSubmission depositSubmission, ExplodedPackage explodedPackage) {
        if (explodedPackage.getPackageFile().toString().contains("pmc")) {
            return nihmsVerifier;
        }

        if (explodedPackage.getPackageFile().toString().contains("jscholarship")) {
            return dspaceVerifier;
        }

        fail("Unable to select PackageVerifier");
        return null;
    }

    @Override
    protected PackageOptions.Compression.OPTS sniffCompression(File packageFile) {
        return PackageOptions.Compression.OPTS.NONE;
    }

    @Override
    protected PackageOptions.Archive.OPTS sniffArchive(File packageFile) {
        return PackageOptions.Archive.OPTS.ZIP;
    }

}
