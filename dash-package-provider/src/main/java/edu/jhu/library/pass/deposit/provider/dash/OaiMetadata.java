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

import java.util.stream.Stream;

public enum OaiMetadata {

    // Populated from https://dash.harvard.edu/oai/request?verb=ListMetadataFormats

    MODS("mods", "http://www.loc.gov/mods/v3"),
    DIDL("didl", "urn:mpeg:mpeg21:2002:02-DIDL-NS"),
    OAI_DC("oai_dc", "http://www.openarchives.org/OAI/2.0/oai_dc/"),
    DIM("dim", "http://www.dspace.org/xmlns/dspace/dim"),
    QDC("qdc", "http://purl.org/dc/terms/"),
    ORE("ore", "http://www.w3.org/2005/Atom"),
    METS("mets", "http://www.loc.gov/METS/"),
    XOAI("xoai", "http://www.lyncode.com/xoai"),
    UKETD_DC("uketd_dc", "http://naca.central.cranfield.ac.uk/ethos-oai/2.0/"),
    RDF("rdf", "http://www.openarchives.org/OAI/2.0/rdf/"),
    MARC("marc", "http://www.loc.gov/MARC21/slim"),
    DASHRDF("dashrdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns"),
    ETDMS("etdms", "http://www.ndltd.org/standards/metadata/etdms/1.0/");

    private String prefix;

    private String namespace;

    private OaiMetadata(String prefix, String namespace) {
        this.prefix = prefix;
        this.namespace = namespace;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getNamespace() {
        return namespace;
    }

    static OaiMetadata forPrefix(String prefix) {
        return Stream.of(OaiMetadata.values())
                .filter(md -> md.prefix.equals(prefix))
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException("No such prefix '" + prefix + "'"));
    }

    static OaiMetadata forNamespace(String namespace) {
        return Stream.of(OaiMetadata.values())
                .filter(md -> md.namespace.equals(namespace))
                .findAny()
                .orElseThrow(() -> new IllegalArgumentException("No such namespace '" + namespace + "'"));
    }


    @Override
    public String toString() {
        return "OaiMetadata{" +
                "prefix='" + prefix + '\'' +
                ", namespace='" + namespace + '\'' +
                "} " + super.toString();
    }
}
