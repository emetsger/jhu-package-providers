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

import org.apache.commons.io.filefilter.AndFileFilter;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.NameFileFilter;
import org.dataconservancy.pass.deposit.assembler.shared.ExplodedPackage;
import org.dataconservancy.pass.deposit.assembler.shared.PackageVerifier;
import org.dataconservancy.pass.deposit.model.DepositFile;
import org.dataconservancy.pass.deposit.model.DepositSubmission;

import javax.crypto.BadPaddingException;
import java.io.File;
import java.io.FileFilter;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class BagItPackageVerifier implements PackageVerifier {


    @Override
    public void verify(DepositSubmission depositSubmission, ExplodedPackage explodedPackage, Map<String, Object> map) throws Exception {
        final BiFunction<File, File, DepositFile> MAPPER = (packageDir, file) -> {
            return depositSubmission.getFiles()
                    .stream()
                    .filter(df -> df.getLocation().endsWith(file.getName()))
                    .findAny()
                    .orElseThrow(() -> new RuntimeException("Missing custodial file '" + file + "'"));
        };

        final File payloadDir = new File(explodedPackage.getExplodedDir(), BagItPackageProvider.PAYLOAD_DIR);
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

        verifyCustodialFiles(depositSubmission, explodedPackage.getExplodedDir(), payloadFilter, MAPPER);
    }


}
