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
package edu.jhu.library.pass.deposit.provider.dash;

import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilderFactory;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

import static org.junit.Assert.*;

/**
 * @author Elliot Metsger (emetsger@jhu.edu)
 */
public class DimRepositoryCopyLocationAnalyzerTest {

    private static final String REPOCOPY_URI = "http://nrs.harvard.edu/urn-3:HUL.InstRepos:39987919";

    private static final String SUBMISSION_URI = "https://pass.harvard.edu/fcrepo/rest/submissions/1234";

    /**
     * A DSpace DIM metadata document that does not contain the metadata field which references the unique PASS
     * Submission identifier.
     */
    private static final String NO_PASS_SUBMISSION_FIELD = "" +
        "<dim:dim xmlns:dim=\"http://www.dspace.org/xmlns/dspace/dim\" xmlns:doc=\"http://www.lyncode.com/xoai\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://www.dspace.org/xmlns/dspace/dim http://www.dspace.org/schema/dim.xsd\">\n" +
            "          <dim:field mdschema=\"dc\" element=\"contributor\" qualifier=\"advisor\" authority=\"8ea888dbb9c98e856decb7404336efab\" confidence=\"-1\">Sharpe, Arlene H.</dim:field>\n" +
            "          <dim:field mdschema=\"dc\" element=\"contributor\" qualifier=\"advisor\" authority=\"a567c1322e160cda97a7f90b9d12bead\" confidence=\"-1\">Golub, Todd R.</dim:field>\n" +
            "          <dim:field mdschema=\"dc\" element=\"contributor\" qualifier=\"advisor\" authority=\"a6a441cf778a223ef6a80db03c00b7b1\" confidence=\"-1\">Dougan, Stephanie K.</dim:field>\n" +
            "          <dim:field mdschema=\"dc\" element=\"contributor\" qualifier=\"advisor\" authority=\"bcdd5be471ac3145da7f41c56905c617\" confidence=\"-1\">Wherry, John</dim:field>\n" +
            "          <dim:field mdschema=\"dc\" element=\"contributor\" qualifier=\"author\" authority=\"b1f8749b18d6ca4a66216a899cdc8383\" confidence=\"-1\">Manguso, Robert T.</dim:field>\n" +
            "          <dim:field mdschema=\"dc\" element=\"date\" qualifier=\"accessioned\">2019-05-17T14:17:22Z</dim:field>\n" +
            "          <dim:field mdschema=\"dc\" element=\"date\" qualifier=\"created\">2017-11</dim:field>\n" +
            "          <dim:field mdschema=\"dc\" element=\"date\" qualifier=\"issued\">2017-10-10</dim:field>\n" +
            "          <dim:field mdschema=\"dc\" element=\"date\" qualifier=\"submitted\">2017</dim:field>\n" +
            "          <dim:field mdschema=\"dc\" element=\"date\" qualifier=\"available\">2019-05-17T14:17:22Z</dim:field>\n" +
            "          <dim:field mdschema=\"dc\" element=\"identifier\" qualifier=\"uri\" lang=\"*\">" + REPOCOPY_URI + "</dim:field>\n" +
            "          <dim:field mdschema=\"dc\" element=\"description\" qualifier=\"abstract\">Despite the dramatic clinical success of cancer immunotherapy with PD-1 checkpoint blockade, most patients do not experience sustained clinical benefit from treatment, suggesting that additional therapeutic strategies are needed. Major efforts to develop new immunotherapies are currently focused on a limited set of molecules known to be expressed by cancer cells such as PD-L1, CD47 and IDO1. Although cancer cells could use many more genes to evade detection by the immune system, methods to systematically identify such genes are lacking. \n" +
            "To discover new immunotherapy targets, we developed a pooled, loss-of-function in vivo genetic screening approach using CRISPR/Cas9 genome editing in mouse transplantable tumors treated with vaccination and PD-1 checkpoint blockade. We optimized the expression of CRISPR genome-editing components in immunocompetent mice and screened 2,400 genes expressed by melanoma cells for those that synergize with or cause resistance to checkpoint blockade. We recovered the known immune evasion molecules, PD-L1 and CD47, and also identified new immunotherapy targets such as Qa-1b (HLA-E). We found that loss of function of genes required for sensing interferon-γ caused resistance to immunotherapy, which was caused by failure to present antigen and a resistance to the growth inhibiting effect of interferon-γ. \n" +
            "Deletion of Ptpn2, a pleotropic protein tyrosine phosphatase, improved response to immunotherapy. Cellular, biochemical, transcriptional, and genetic epistasis experiments demonstrated that loss of function of Ptpn2 sensitizes tumors to immunotherapy by enhancing interferon-γ-mediated effects on antigen presentation and growth suppression. Thus, strategies that increase interferon-γ sensing by tumor cells could improve the efficacy of immunotherapy. \n" +
            "To extend the benefits of cancer immunotherapy to more patients, it is critical that we develop methods to understand the interactions between tumor cells and the immune system. Our technology is the first to use in vivo genetic screens to understand these complex interactions, and our findings demonstrate the potential impact of this approach. These screens can, in theory, be applied to any transplantable mouse model of cancer and any immunotherapy approach. A broad application of this technology in additional tumor models will identify new immunotherapy targets, model resistance mechanisms, and may accelerate the rational selection of combination immunotherapy.</dim:field>\n" +
            "          <dim:field mdschema=\"dc\" element=\"description\" qualifier=\"sponsorship\">Medical Sciences</dim:field>\n" +
            "          <dim:field mdschema=\"dc\" element=\"description\" qualifier=\"keywords\">Immunotherapy, Cancer biology</dim:field>\n" +
            "          <dim:field mdschema=\"dc\" element=\"format\" qualifier=\"mimetype\">application/pdf</dim:field>\n" +
            "          <dim:field mdschema=\"dc\" element=\"language\" qualifier=\"iso\">en</dim:field>\n" +
            "          <dim:field mdschema=\"dc\" element=\"subject\">Biology, Molecular</dim:field>\n" +
            "          <dim:field mdschema=\"dc\" element=\"subject\">Health Sciences, Immunology</dim:field>\n" +
            "          <dim:field mdschema=\"dc\" element=\"subject\">Health Sciences, Oncology</dim:field>\n" +
            "          <dim:field mdschema=\"dc\" element=\"title\">In Vivo Genetic Screening to Identify Mechanisms of Resistance to Cancer Immunotherapy</dim:field>\n" +
            "          <dim:field mdschema=\"dc\" element=\"type\">Thesis or Dissertation</dim:field>\n" +
            "          <dim:field mdschema=\"dc\" element=\"type\" qualifier=\"material\">text</dim:field>\n" +
            "          <dim:field mdschema=\"dash\" element=\"license\">LAA</dim:field>\n" +
            "          <dim:field mdschema=\"dash\" element=\"depositing\" qualifier=\"author\" authority=\"b1f8749b18d6ca4a66216a899cdc8383\" confidence=\"-1\">Manguso, Robert T.</dim:field>\n" +
            "          <dim:field mdschema=\"dash\" element=\"identifier\" qualifier=\"vireo\">http://etds.lib.harvard.edu/gsas/admin/view/1810</dim:field>\n" +
            "          <dim:field mdschema=\"dash\" element=\"author\" qualifier=\"email\">manguso.robert@gmail.com</dim:field>\n" +
            "          <dim:field mdschema=\"thesis\" element=\"degree\" qualifier=\"date\">2017</dim:field>\n" +
            "          <dim:field mdschema=\"thesis\" element=\"degree\" qualifier=\"grantor\">Graduate School of Arts &amp; Sciences</dim:field>\n" +
            "          <dim:field mdschema=\"thesis\" element=\"degree\" qualifier=\"level\">Doctoral</dim:field>\n" +
            "          <dim:field mdschema=\"thesis\" element=\"degree\" qualifier=\"name\">Doctor of Philosophy</dim:field>\n" +
            "          <dim:field mdschema=\"thesis\" element=\"degree\" qualifier=\"department\">Medical Sciences</dim:field>\n" +
            "        </dim:dim>";

    /**
     * A DSpace DIM metadata document that contains the metadata field which references the unique PASS Submission
     * identifier.
     */
    private static final String MATCHING_SUBMISSION = "" +
            "<dim:dim xmlns:dim=\"http://www.dspace.org/xmlns/dspace/dim\" xmlns:doc=\"http://www.lyncode.com/xoai\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://www.dspace.org/xmlns/dspace/dim http://www.dspace.org/schema/dim.xsd\">\n" +
            "          <dim:field mdschema=\"dc\" element=\"contributor\" qualifier=\"advisor\" authority=\"8ea888dbb9c98e856decb7404336efab\" confidence=\"-1\">Sharpe, Arlene H.</dim:field>\n" +
            "          <dim:field mdschema=\"dc\" element=\"contributor\" qualifier=\"advisor\" authority=\"a567c1322e160cda97a7f90b9d12bead\" confidence=\"-1\">Golub, Todd R.</dim:field>\n" +
            "          <dim:field mdschema=\"dc\" element=\"contributor\" qualifier=\"advisor\" authority=\"a6a441cf778a223ef6a80db03c00b7b1\" confidence=\"-1\">Dougan, Stephanie K.</dim:field>\n" +
            "          <dim:field mdschema=\"dc\" element=\"contributor\" qualifier=\"advisor\" authority=\"bcdd5be471ac3145da7f41c56905c617\" confidence=\"-1\">Wherry, John</dim:field>\n" +
            "          <dim:field mdschema=\"dc\" element=\"contributor\" qualifier=\"author\" authority=\"b1f8749b18d6ca4a66216a899cdc8383\" confidence=\"-1\">Manguso, Robert T.</dim:field>\n" +
            "          <dim:field mdschema=\"dc\" element=\"date\" qualifier=\"accessioned\">2019-05-17T14:17:22Z</dim:field>\n" +
            "          <dim:field mdschema=\"dc\" element=\"date\" qualifier=\"created\">2017-11</dim:field>\n" +
            "          <dim:field mdschema=\"dc\" element=\"date\" qualifier=\"issued\">2017-10-10</dim:field>\n" +
            "          <dim:field mdschema=\"dc\" element=\"date\" qualifier=\"submitted\">2017</dim:field>\n" +
            "          <dim:field mdschema=\"dc\" element=\"date\" qualifier=\"available\">2019-05-17T14:17:22Z</dim:field>\n" +
            "          <dim:field mdschema=\"dc\" element=\"identifier\" qualifier=\"uri\" lang=\"*\">" + REPOCOPY_URI + "</dim:field>\n" +
            "          <dim:field mdschema=\"dc\" element=\"identifier\" qualifier=\"uri\" lang=\"*\">" + SUBMISSION_URI + "</dim:field>\n" +
            "          <dim:field mdschema=\"dc\" element=\"description\" qualifier=\"abstract\">Despite the dramatic clinical success of cancer immunotherapy with PD-1 checkpoint blockade, most patients do not experience sustained clinical benefit from treatment, suggesting that additional therapeutic strategies are needed. Major efforts to develop new immunotherapies are currently focused on a limited set of molecules known to be expressed by cancer cells such as PD-L1, CD47 and IDO1. Although cancer cells could use many more genes to evade detection by the immune system, methods to systematically identify such genes are lacking. \n" +
            "To discover new immunotherapy targets, we developed a pooled, loss-of-function in vivo genetic screening approach using CRISPR/Cas9 genome editing in mouse transplantable tumors treated with vaccination and PD-1 checkpoint blockade. We optimized the expression of CRISPR genome-editing components in immunocompetent mice and screened 2,400 genes expressed by melanoma cells for those that synergize with or cause resistance to checkpoint blockade. We recovered the known immune evasion molecules, PD-L1 and CD47, and also identified new immunotherapy targets such as Qa-1b (HLA-E). We found that loss of function of genes required for sensing interferon-γ caused resistance to immunotherapy, which was caused by failure to present antigen and a resistance to the growth inhibiting effect of interferon-γ. \n" +
            "Deletion of Ptpn2, a pleotropic protein tyrosine phosphatase, improved response to immunotherapy. Cellular, biochemical, transcriptional, and genetic epistasis experiments demonstrated that loss of function of Ptpn2 sensitizes tumors to immunotherapy by enhancing interferon-γ-mediated effects on antigen presentation and growth suppression. Thus, strategies that increase interferon-γ sensing by tumor cells could improve the efficacy of immunotherapy. \n" +
            "To extend the benefits of cancer immunotherapy to more patients, it is critical that we develop methods to understand the interactions between tumor cells and the immune system. Our technology is the first to use in vivo genetic screens to understand these complex interactions, and our findings demonstrate the potential impact of this approach. These screens can, in theory, be applied to any transplantable mouse model of cancer and any immunotherapy approach. A broad application of this technology in additional tumor models will identify new immunotherapy targets, model resistance mechanisms, and may accelerate the rational selection of combination immunotherapy.</dim:field>\n" +
            "          <dim:field mdschema=\"dc\" element=\"description\" qualifier=\"sponsorship\">Medical Sciences</dim:field>\n" +
            "          <dim:field mdschema=\"dc\" element=\"description\" qualifier=\"keywords\">Immunotherapy, Cancer biology</dim:field>\n" +
            "          <dim:field mdschema=\"dc\" element=\"format\" qualifier=\"mimetype\">application/pdf</dim:field>\n" +
            "          <dim:field mdschema=\"dc\" element=\"language\" qualifier=\"iso\">en</dim:field>\n" +
            "          <dim:field mdschema=\"dc\" element=\"subject\">Biology, Molecular</dim:field>\n" +
            "          <dim:field mdschema=\"dc\" element=\"subject\">Health Sciences, Immunology</dim:field>\n" +
            "          <dim:field mdschema=\"dc\" element=\"subject\">Health Sciences, Oncology</dim:field>\n" +
            "          <dim:field mdschema=\"dc\" element=\"title\">In Vivo Genetic Screening to Identify Mechanisms of Resistance to Cancer Immunotherapy</dim:field>\n" +
            "          <dim:field mdschema=\"dc\" element=\"type\">Thesis or Dissertation</dim:field>\n" +
            "          <dim:field mdschema=\"dc\" element=\"type\" qualifier=\"material\">text</dim:field>\n" +
            "          <dim:field mdschema=\"dash\" element=\"license\">LAA</dim:field>\n" +
            "          <dim:field mdschema=\"dash\" element=\"depositing\" qualifier=\"author\" authority=\"b1f8749b18d6ca4a66216a899cdc8383\" confidence=\"-1\">Manguso, Robert T.</dim:field>\n" +
            "          <dim:field mdschema=\"dash\" element=\"identifier\" qualifier=\"vireo\">http://etds.lib.harvard.edu/gsas/admin/view/1810</dim:field>\n" +
            "          <dim:field mdschema=\"dash\" element=\"author\" qualifier=\"email\">manguso.robert@gmail.com</dim:field>\n" +
            "          <dim:field mdschema=\"thesis\" element=\"degree\" qualifier=\"date\">2017</dim:field>\n" +
            "          <dim:field mdschema=\"thesis\" element=\"degree\" qualifier=\"grantor\">Graduate School of Arts &amp; Sciences</dim:field>\n" +
            "          <dim:field mdschema=\"thesis\" element=\"degree\" qualifier=\"level\">Doctoral</dim:field>\n" +
            "          <dim:field mdschema=\"thesis\" element=\"degree\" qualifier=\"name\">Doctor of Philosophy</dim:field>\n" +
            "          <dim:field mdschema=\"thesis\" element=\"degree\" qualifier=\"department\">Medical Sciences</dim:field>\n" +
            "        </dim:dim>";

    private Document noMatch;

    private Document matching;

    private DimRepositoryCopyLocationAnalyzer underTest = new DimRepositoryCopyLocationAnalyzer();

    @Before
    public void setUp() throws Exception {
        underTest = new DimRepositoryCopyLocationAnalyzer();
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);

        noMatch = dbf.newDocumentBuilder().parse(IOUtils.toInputStream(NO_PASS_SUBMISSION_FIELD, "UTF-8"));
        matching = dbf.newDocumentBuilder().parse(IOUtils.toInputStream(MATCHING_SUBMISSION, "UTF-8"));
    }

    @Test
    public void simpleMatch() throws MalformedURLException {
        URL actual = underTest.analyze(matching, URI.create(SUBMISSION_URI),
                (mdSchema, element, qualifier, textContent) -> textContent.startsWith(SUBMISSION_URI),
                (mdSchema, element, qualifier, textContent) -> textContent.startsWith("http://nrs.harvard.edu/"));

        assertEquals(new URL(REPOCOPY_URI), actual);
    }

    @Test
    public void simpleNoMatch() throws MalformedURLException {
        URL actual = underTest.analyze(noMatch, URI.create(SUBMISSION_URI),
                (mdSchema, element, qualifier, textContent) -> textContent.startsWith(SUBMISSION_URI),
                (mdSchema, element, qualifier, textContent) -> textContent.startsWith("http://nrs.harvard.edu/"));

        assertNull(actual);
    }
}