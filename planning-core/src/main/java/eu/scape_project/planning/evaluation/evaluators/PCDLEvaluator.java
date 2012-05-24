/*******************************************************************************
 * Copyright 2012 Vienna University of Technology
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
 * 
 * This work originates from the Planets project, co-funded by the European Union under the Sixth Framework Programme.
 ******************************************************************************/
package eu.scape_project.planning.evaluation.evaluators;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import eu.scape_project.planning.evaluation.EvaluatorBase;
import eu.scape_project.planning.evaluation.EvaluatorException;
import eu.scape_project.planning.evaluation.IActionEvaluator;
import eu.scape_project.planning.evaluation.IStatusListener;
import eu.scape_project.planning.model.Alternative;
import eu.scape_project.planning.model.scales.Scale;
import eu.scape_project.planning.model.util.CriterionUri;
import eu.scape_project.planning.model.values.Value;

/**
 * This class extracts values from PCDL descriptors
 * @author cb
 *
 */
public class PCDLEvaluator extends EvaluatorBase implements IActionEvaluator {
    
    private static final String DESCRIPTOR_FILE = "data/evaluation/measurementsConsolidated.xml";

	private static Logger log = LoggerFactory.getLogger(PCDLEvaluator.class);

    private HashMap<String, String> extractionPaths = new HashMap<String, String>();
    // maybe another hashmap for commentsPaths

    public PCDLEvaluator() {
        // load information about measurements
        loadMeasurementsDescription(DESCRIPTOR_FILE);
        addExtractionPaths();        
    }
    
    public HashMap<CriterionUri, Value> evaluate(Alternative alternative,
            List<CriterionUri> criterionUris, IStatusListener listener)
            throws EvaluatorException {
        
        HashMap<CriterionUri, Value> results = new HashMap<CriterionUri, Value>();
        if ((alternative.getAction() == null) || (alternative.getAction().getDescriptor() == null)) {
            return results;
        }

        try {
            // yes, this is a hack. It's a demo.
            // If this is a minimee action, we know that there will be 
            // a PCDL for it, so we retrieve it locally:
            if (alternative.getAction().getUrl().contains("minimee/")) {
                String pcdlFile = alternative.getAction().getDescriptor()+".xml";
                InputStream pcdlStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(
                        "data/pcdl/"+pcdlFile);
                if (pcdlStream == null) {
                    log.debug("pcdl descriptor not found: " + pcdlFile);
                    return results;
                }
                XmlExtractor xmlExtractor = new XmlExtractor();
                Document doc = xmlExtractor.getDocument(new InputSource(pcdlStream));
                
                for(CriterionUri criterionUri: criterionUris) {
                    String propertyURI = criterionUri.getAsURI();
                    Scale scale = descriptor.getMeasurementScale(criterionUri);
                    if (scale == null)  {
                        // This means that I am not entitled to evaluate this criterion and therefore supposed to skip it:
                        continue;
                    }
                    if (ACTION_RETAIN_FILENAME.equals(propertyURI)) {
                        // for all wrapped minimee migrators the output filename can be determined by -o <filename> or something similar  
                        Value v = scale.createValue();
                        v.setComment("obtained from PCDL descriptor");
                        v.parse("Yes");
                        results.put(criterionUri, v);
                    }
                    
                    String extractionPath = extractionPaths.get(propertyURI);
                    if (extractionPath != null) {
                    	Value v = new XmlExtractor().extractValue(doc, scale, extractionPath, null);
                        if (v != null) {
                            v.setComment("obtained from PCDL descriptor");
                            results.put(criterionUri, v);                        
                        } else {
                            // No: only successfully evaluated values are returned  
                            // v = leaf.getScale().createValue();
                            // v.setComment("failed to obtain value from PCDL descriptor");
                            log.debug("failed to obtain value from PCDL descriptor for path: " + extractionPath);
                        }
                    }
                }
            }
            return results;
            
        } catch (ParserConfigurationException e) {
            throw new EvaluatorException("Could not access PCDL descriptor", e);
        } catch (SAXException e) {
            throw new EvaluatorException("Could not access PCDL descriptor", e);
        } catch (IOException e) {
            throw new EvaluatorException("Could not access PCDL descriptor", e);
        }
    }
    
    private void addExtractionPaths() {
        extractionPaths.put(ACTION_BUSINESS_LICENCING_SCHEMA, "//Licensing/Schema/text()");
        extractionPaths.put(ACTION_LICENSE, "//Licensing/License/text()");
    }
}