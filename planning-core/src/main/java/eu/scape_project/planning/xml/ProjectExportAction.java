/*******************************************************************************
 * Copyright 2006 - 2012 Vienna University of Technology,
 * Department of Software Technology and Interactive Systems, IFS
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package eu.scape_project.planning.xml;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.inject.Inject;
import javax.persistence.EntityManager;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import eu.scape_project.planning.manager.DigitalObjectManager;
import eu.scape_project.planning.manager.StorageException;
import eu.scape_project.planning.model.ByteStream;
import eu.scape_project.planning.model.DigitalObject;
import eu.scape_project.planning.model.Plan;
import eu.scape_project.planning.model.PlanProperties;
import eu.scape_project.planning.utils.FileUtils;
import eu.scape_project.planning.utils.OS;
import eu.scape_project.planning.utils.ParserException;

import org.dom4j.Document;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.dom4j.Node;
import org.dom4j.XPath;
import org.dom4j.io.DocumentSource;
import org.dom4j.io.XMLWriter;
import org.slf4j.Logger;

import sun.misc.BASE64Encoder;

/**
 * This class inserts test data into the persistence layer, including import of
 * objective trees from case studies.
 * 
 * @author Christoph Becker
 */
public class ProjectExportAction implements Serializable {
    private static final long serialVersionUID = 2155152208617526555L;

    /**
     * Boundary of data to load before calling the garbage collector.
     */
    private static final int LOADED_DATA_SIZE_BOUNDARY = 200 * 1024 * 1024;

    @Inject
    private Logger log;

    @Inject
    private EntityManager em;

    @Inject
    protected DigitalObjectManager digitalObjectManager;

    private String lastProjectExportPath;

    public ProjectExportAction() {
        lastProjectExportPath = null;
    }

    /**
     * Exports all projects into separate xml files and adds them to a zip
     * archive.
     * 
     * @return True if export was successful, false otherwise.
     */
    public boolean exportAllProjectsToZip() {
        List<PlanProperties> ppList = em.createQuery("select p from PlanProperties p order by p.id").getResultList();

        return exportPPListToZip(ppList);
    }

    /**
     * UNTESTED, NOT SURE IF EVEN THE QUERY CODE WORKS!
     * 
     * @param fromID
     *            from-ID in table PlanProperties, which is used to filter
     *            PlanProperties
     * @param toID
     *            to-ID in table PlanProperties, which is used to filter
     *            PlanProperties
     * @return True if export was successful, false otherwise.
     */
    public boolean exportSomeProjectsToZip(Integer fromID, Integer toID) {
        List<PlanProperties> ppList = em.createQuery(
            "select p.planProperties from Plan p where " + " p.planProperties.id >= " + fromID
                + " and p.planProperties.id <= " + toID + " order by p.planProperties.id").getResultList();

        return exportPPListToZip(ppList);
    }

    /**
     * Exports the project identified by PlanProperties.Id ppid and writes the
     * document to the given OutputStream - including all binary data.
     * (currently required by {@link #exportAllProjectsToZip()} ) - Does NOT
     * clean up temp files written to baseTempPath
     * 
     * @param ppid
     * @param out
     * @param baseTempPath
     *            used to write temp files for binary data, must not be used by
     *            other exports at the same time
     * @return True if export was successful, false otherwise.
     */
    public boolean exportComplete(int ppid, OutputStream out, String baseTempPath) {
        BASE64Encoder encoder = new BASE64Encoder();
        ProjectExporter exporter = new ProjectExporter();
        Document doc = exporter.createProjectDoc();

        Plan plan = null;
        try {
            plan = em.createQuery("select p from Plan p where p.planProperties.id = " + ppid, Plan.class)
                .getSingleResult();
        } catch (Exception e) {
            log.error("Could not load planProperties: ", e);
            log.debug("Skipping the export of the plan with properties" + ppid + ": Couldnt load.");
            return false;
        }
        try {
            String tempPath = baseTempPath;
            File tempDir = new File(tempPath);
            tempDir.mkdirs();

            try {
                exporter.addProject(plan, doc, false);

                // Perform XSLT transformation to get the DATA into the PLANS
                List<Integer> binaryObjectIds = getBinaryObjectIds(doc);
                writeBinaryObjects(binaryObjectIds, tempPath, encoder);
                addBinaryData(doc, out, tempPath);

                addPreservationActionPlanData(doc, out, tempPath);
            } catch (IOException e) {
                log.error("Could not open outputstream: ", e);
                return false;
            } catch (TransformerException e) {
                log.error("failed to generate export file.", e);
                return false;
            } catch (StorageException e) {
                log.error("Could not load object from stoarge.", e);
                return false;
            } catch (ParserException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        } finally {
            // Clean up
            plan = null;

            em.clear();
            System.gc();
        }

        return true;
    }

    /**
     * Returns a list of object IDs that are stored in the document without
     * binary data.
     * 
     * @param doc
     *            the document to search
     * @return a list of IDs
     */
    private List<Integer> getBinaryObjectIds(Document doc) {

        // Get data elements that have data and a number as content
        XPath xpath = doc.createXPath("//plato:data[@hasData='true' and number(.) = number(.)]");

        Map<String, String> namespaceMap = new HashMap<String, String>();
        namespaceMap.put("plato", PreservationPlanXML.PLATO_NS);
        xpath.setNamespaceURIs(namespaceMap);

        @SuppressWarnings("unchecked")
        List<Element> elements = xpath.selectNodes(doc);

        List<Integer> objectIds = new ArrayList<Integer>(elements.size());
        for (Element element : elements) {
            objectIds.add(Integer.parseInt(element.getStringValue()));
        }
        return objectIds;
    }

    private void addPreservationActionPlanData(Document doc, OutputStream out, String tempPath)
        throws StorageException, IOException {
        List<Integer> t2flowExecutablePlanIds = getT2flowExecutablePlanIds(doc);
        List<Integer> collectionProfileIds = getCollectionProfileIds(doc);

        writeDigitalObjects(t2flowExecutablePlanIds, tempPath);
        writeDigitalObjects(collectionProfileIds, tempPath);

    }

    /**
     * Returns the t2flow executable plan IDs that are in the document without
     * data.
     * 
     * @param doc
     *            the document to search
     * @return a list of IDs
     */
    private List<Integer> getT2flowExecutablePlanIds(Document doc) {
        // Get data elements that have data and a number as content
        XPath xpath = doc.createXPath("//plato:executablePlan[@type='t2flow']");

        Map<String, String> namespaceMap = new HashMap<String, String>();
        namespaceMap.put("plato", PreservationPlanXML.PLATO_NS);
        xpath.setNamespaceURIs(namespaceMap);

        @SuppressWarnings("unchecked")
        List<Element> elements = xpath.selectNodes(doc);

        List<Integer> objectIds = new ArrayList<Integer>(elements.size());
        for (Element element : elements) {
            objectIds.add(Integer.parseInt(element.getStringValue()));
        }
        return objectIds;
    }

    /**
     * Returns the collection profile IDs that are in the document without data.
     * 
     * @param doc
     *            the docuemnt to seasrch
     * @return a list of IDs
     */
    private List<Integer> getCollectionProfileIds(Document doc) {
        // Get data elements that have data and a number as content
        XPath xpath = doc.createXPath("//plato:preservationActionPlan/objects");

        Map<String, String> namespaceMap = new HashMap<String, String>();
        namespaceMap.put("plato", PreservationPlanXML.PLATO_NS);
        xpath.setNamespaceURIs(namespaceMap);

        @SuppressWarnings("unchecked")
        List<Element> elements = xpath.selectNodes(doc);

        List<Integer> objectIds = new ArrayList<Integer>(elements.size());
        for (Element element : elements) {
            objectIds.add(Integer.parseInt(element.getStringValue()));
        }
        return objectIds;
    }

    private void writeDigitalObjects(List<Integer> objectIds, String aTempDir) throws IOException, StorageException {
        int counter = 0;
        int skip = 0;
        log.info("Writing bytestreams of digital objects. Size = " + objectIds.size());
        for (Integer id : objectIds) {
            if (counter > LOADED_DATA_SIZE_BOUNDARY) { // Call GC if unused data
                                                       // exceeds boundary
                System.gc();
                counter = 0;
            }
            DigitalObject object = em.find(DigitalObject.class, id);
            if (object.isDataExistent()) {
                counter += object.getData().getSize();
                File f = new File(aTempDir + object.getId() + ".xml");
                DigitalObject dataFilledObject = digitalObjectManager.getCopyOfDataFilledDigitalObject(object);
                FileOutputStream out = new FileOutputStream(f);
                try {
                    out.write(dataFilledObject.getData().getData());
                } finally {
                    out.close();
                }
                dataFilledObject = null;
            } else {
                skip++;
            }
            object = null;
        }
        em.clear();
        System.gc();
        log.info("Finished writing bytestreams of digital objects. Skipped empty objects: " + skip);
    }

    /**
     * Performs XSLT transformation to get the DATA into the PLANS
     */
    private void addT2flowExecutablePlan(Document doc, OutputStream out, String aTempDir) throws TransformerException {
        InputStream xsl = Thread.currentThread().getContextClassLoader()
            .getResourceAsStream("data/xslt/addT2flowToPlan.xsl");

        TransformerFactory transformerFactory = TransformerFactory.newInstance();

        Transformer transformer = transformerFactory.newTransformer(new StreamSource(xsl));
        transformer.setParameter("tempDir", aTempDir);

        Source xmlSource = new DocumentSource(doc);

        Result outputTarget = new StreamResult(out); // new
                                                     // FileWriter(outFile));

        log.debug("starting bytestream transformation ...");
        transformer.transform(xmlSource, outputTarget);
        log.debug("FINISHED bytestream transformation!");
    }

    /**
     * new helper method that was refactored from
     * {@link #exportAllProjectsToZip()} It takes a list of
     * {@link PlanProperties} and exports it to a zip file.
     * 
     * @param ppList
     *            {@link PlanProperties} for plans to export
     * 
     * @return True if export was successful, false otherwise.
     */
    private boolean exportPPListToZip(List<PlanProperties> ppList) {
        if (!ppList.isEmpty()) {
            log.debug("number of plans to export: " + ppList.size());
            String filename = "allprojects.zip";

            lastProjectExportPath = OS.getTmpPath() + "export" + System.currentTimeMillis() + "/";
            new File(lastProjectExportPath).mkdirs();

            String binarydataTempPath = lastProjectExportPath + "binarydata/";
            File binarydataTempDir = new File(binarydataTempPath);
            binarydataTempDir.mkdirs();

            try {
                OutputStream out = new BufferedOutputStream(new FileOutputStream(lastProjectExportPath + filename));
                ZipOutputStream zipOut = new ZipOutputStream(out);

                for (PlanProperties pp : ppList) {
                    log.debug("EXPORTING: " + pp.getName());
                    ZipEntry zipAdd = new ZipEntry(String.format("%1$03d", pp.getId()) + "-"
                        + FileUtils.makeFilename(pp.getName()) + ".xml");
                    zipOut.putNextEntry(zipAdd);
                    // export the complete project, including binary data
                    exportComplete(pp.getId(), zipOut, binarydataTempPath);
                    zipOut.closeEntry();
                }
                zipOut.close();
                out.close();
                new File(lastProjectExportPath + "finished.info").createNewFile();

                // FacesMessages.instance().add(FacesMessage.SEVERITY_INFO,
                // "Export was written to: " + exportPath);
                log.info("Export was written to: " + lastProjectExportPath);
            } catch (IOException e) {
                // FacesMessages.instance().add(FacesMessage.SEVERITY_ERROR,
                // "An error occured while generating the export file.");
                log.error("An error occured while generating the export file.", e);
                File errorInfo = new File(lastProjectExportPath + "error.info");
                try {
                    Writer w = new FileWriter(errorInfo);
                    w.write("An error occured while generating the export file:");
                    w.write(e.getMessage());
                    w.close();
                } catch (IOException e1) {
                    log.error("Could not write error file.");
                }

                return false;
            } finally {
                // remove all binary temp files
                OS.deleteDirectory(binarydataTempDir);
            }
        }

        return true;
    }

    /**
     * Performs XSLT transformation to get the DATA into the PLANS
     */
    private void addBinaryData(Document doc, OutputStream out, String aTempDir) throws TransformerException {
        InputStream xsl = Thread.currentThread().getContextClassLoader()
            .getResourceAsStream("data/xslt/bytestreams.xsl");

        TransformerFactory transformerFactory = TransformerFactory.newInstance();

        Transformer transformer = transformerFactory.newTransformer(new StreamSource(xsl));
        transformer.setParameter("tempDir", aTempDir);

        Source xmlSource = new DocumentSource(doc);

        Result outputTarget = new StreamResult(out); // new
                                                     // FileWriter(outFile));

        log.debug("starting bytestream transformation ...");
        transformer.transform(xmlSource, outputTarget);
        log.debug("FINISHED bytestream transformation!");
    }

    /**
     * Loads all binary data for the given digital objects and dumps it to XML
     * files, located in tempDir.
     * 
     * @param objectIds
     * @param tempDir
     * @param encoder
     * @throws IOException
     * @throws StorageException
     */
    private void writeBinaryObjects(List<Integer> objectIds, String aTempDir, BASE64Encoder encoder)
        throws IOException, StorageException {
        int counter = 0;
        int skip = 0;
        log.info("writing XMLs for bytestreams of digital objects. Size = " + objectIds.size());
        for (Integer id : objectIds) {
            if (counter > LOADED_DATA_SIZE_BOUNDARY) { // Call GC if unused data
                                                       // exceeds boundary
                System.gc();
                counter = 0;
            }
            DigitalObject object = em.find(DigitalObject.class, id);
            if (object.isDataExistent()) {
                counter += object.getData().getSize();
                File f = new File(aTempDir + object.getId() + ".xml");
                DigitalObject dataFilledObject = digitalObjectManager.getCopyOfDataFilledDigitalObject(object);
                writeBinaryData(id, dataFilledObject.getData(), f, encoder);
                dataFilledObject = null;
            } else {
                skip++;
            }
            object = null;
        }
        em.clear();
        System.gc();
        log.info("Finished writing bytestreams of digital objects. Skipped empty objects: " + skip);
    }

    /**
     * Dumps binary data to provided file. It results in an XML file with a
     * single element: data.
     * 
     * @param id
     * @param data
     * @param f
     * @param encoder
     * @throws IOException
     */
    private static void writeBinaryData(int id, ByteStream data, File f, BASE64Encoder encoder) throws IOException {
        Document streamDoc = DocumentHelper.createDocument();
        Element d = streamDoc.addElement("data");
        d.addAttribute("id", "" + id);
        d.setText(encoder.encode(data.getData()));
        XMLWriter writer = new XMLWriter(new BufferedWriter(new FileWriter(f)), ProjectExporter.prettyFormat);
        writer.write(streamDoc);
        writer.flush();
        writer.close();
    }

    // -------- getter/setter --------
    public String getLastProjectExportPath() {
        return lastProjectExportPath;
    }

    public void setLastProjectExportPath(String lastProjectExportPath) {
        this.lastProjectExportPath = lastProjectExportPath;
    }

    // /**
    // * Adds all enlisted plans to an XML document, but does NOT write binary
    // data.
    // * Instead the Id's of all referenced uploads and sample records are added
    // to the provided lists,
    // * this way they can be added later.
    // *
    // * @param ppids
    // * @param uploadIDs
    // * @param recordIDs
    // * @return
    // */
    // public Document exportToXml(List<Integer> ppids, List<Integer> uploadIDs,
    // List<Integer> recordIDs) {
    // ProjectExporter exporter = new ProjectExporter();
    // Document doc = exporter.createProjectDoc();
    //
    // int i = 0;
    // for (Integer id: ppids) {
    // // load one plan after the other:
    // List<Plan> list = em.createQuery(
    // "select p from Plan p where p.planProperties.id = "
    // + id).getResultList();
    // if (list.size() != 1) {
    // FacesMessages.instance().add(FacesMessage.SEVERITY_ERROR,
    // "Skipping the export of the plan with properties"+id+": Couldnt load.");
    // } else {
    // //log.debug("adding project "+p.getplanProperties().getName()+" to XML...");
    // exporter.addProject(list.get(0), doc, uploadIDs, recordIDs);
    // }
    // list.clear();
    // list = null;
    //
    // log.info("XMLExport: addString destinationed project ppid="+id);
    // i++;
    // if ((i%10==0)) {
    // em.clear();
    // System.gc();
    // }
    // }
    // return doc;
    // }
}
