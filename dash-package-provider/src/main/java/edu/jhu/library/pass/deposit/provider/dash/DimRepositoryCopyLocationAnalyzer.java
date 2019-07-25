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

package edu.jhu.library.pass.deposit.provider.dash;

import edu.jhu.library.pass.deposit.provider.dash.OaiResponseBodyProcessor.FieldMatcher;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

import static edu.jhu.library.pass.deposit.provider.dash.DashUtil.asStream;
import static edu.jhu.library.pass.deposit.provider.dash.OaiMetadata.DIM;

@Component
public class DimRepositoryCopyLocationAnalyzer implements OaiResponseBodyProcessor.RepositoryCopyLocationAnalyzer {

    @Override
    public URL analyze(Document metadata, URI submissionUri, FieldMatcher submissionMatcher, FieldMatcher repoCopyMatcher) {
        boolean containsSubmissionUri = asStream(metadata.getElementsByTagNameNS(DIM.getNamespace(), "field"))
                .anyMatch(node -> {
                    String schema = node.getAttributes().getNamedItem("mdschema").getTextContent();
                    String element = node.getAttributes().getNamedItem("element").getTextContent();
                    Node qualifierNode = node.getAttributes().getNamedItem("qualifier");
                    String qualifier = (qualifierNode != null) ? qualifierNode.getTextContent() : null;
                    return submissionMatcher.matches(schema, element, qualifier, node.getTextContent());
                });

        if (containsSubmissionUri) {
            return asStream(metadata.getElementsByTagNameNS(DIM.getNamespace(), "field"))
                    .filter(node -> {
                        String schema = node.getAttributes().getNamedItem("mdschema").getTextContent();
                        String element = node.getAttributes().getNamedItem("element").getTextContent();
                        Node qualifierNode = node.getAttributes().getNamedItem("qualifier");
                        String qualifier = (qualifierNode != null) ? qualifierNode.getTextContent() : null;

                        return repoCopyMatcher.matches(schema, element, qualifier, node.getTextContent());
                    })
                    .findAny()
                    .map(node -> {
                        try {
                            return new URL(node.getTextContent());
                        } catch (MalformedURLException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .orElse(null);
        }

        return null;
    }

}
