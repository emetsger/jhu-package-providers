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

import edu.jhu.library.pass.deposit.provider.j10p.DspaceMetsAssembler;
import org.dataconservancy.pass.deposit.assembler.PackageOptions;
import org.dataconservancy.pass.deposit.assembler.shared.PackageVerifier;
import org.dataconservancy.pass.deposit.integration.shared.SubmitAndValidatePackagesIT;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.singletonList;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = SpringContextConfiguration.class)
public class PlaceholderIT extends SubmitAndValidatePackagesIT {

    private static final Logger LOG = LoggerFactory.getLogger(PlaceholderIT.class);

    @Test
    public void placeholder() {
        LOG.info("Executing placeholder IT.");
    }

    // TODO: Submit to NIHMS Repository using Filesystem Transport and verify package

    // TODO: Submit to J10P Repository using Filesystem Transport and verify package

    // TODO: Submit to J10P Repository using SWORD Transport and verify logical success (separate IT)

    // TODO: Submit to NIHMS Repository using FTP Transport and verify logical success (separate IT)


    @Override
    protected Map<String, Object> getPackageOpts() {
        return new HashMap<String, Object>() {
            {
                put(PackageOptions.Spec.KEY, DspaceMetsAssembler.SPEC_DSPACE_METS);
                put(PackageOptions.Archive.KEY, PackageOptions.Archive.OPTS.ZIP);
                put(PackageOptions.Compression.KEY, PackageOptions.Compression.OPTS.ZIP);
                put(PackageOptions.Checksum.KEY, singletonList(PackageOptions.Checksum.OPTS.SHA256));
            }
        };
    }

    @Override
    protected PackageVerifier getVerifier() {
        return null;
    }

    @Override
    protected InputStream getSubmissionResources() {
        return null;
    }
}
