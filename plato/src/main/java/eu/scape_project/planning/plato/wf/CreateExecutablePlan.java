/*******************************************************************************
 * Copyright 2006 - 2014 Vienna University of Technology,
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
package eu.scape_project.planning.plato.wf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.ejb.Stateful;
import javax.enterprise.context.ConversationScoped;
import javax.inject.Inject;

import org.dom4j.Document;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;
import org.slf4j.Logger;

import eu.scape_project.planning.exception.PlanningException;
import eu.scape_project.planning.manager.StorageException;
import eu.scape_project.planning.model.ByteStream;
import eu.scape_project.planning.model.DigitalObject;
import eu.scape_project.planning.model.Plan;
import eu.scape_project.planning.model.PlanState;
import eu.scape_project.planning.services.taverna.parser.T2FlowParser;
import eu.scape_project.planning.services.taverna.parser.TavernaParserException;
import eu.scape_project.planning.utils.FileUtils;
import eu.scape_project.planning.xml.PlanXMLConstants;
import eu.scape_project.planning.xml.PreservationActionPlanGenerator;

/**
 * Workflow step to configure the executable plan.
 * 
 * @author Michael Kraxner
 */
@Stateful
@ConversationScoped
public class CreateExecutablePlan extends AbstractWorkflowStep {
    private static final long serialVersionUID = -971490825722362606L;

    @Inject
    private Logger log;

    @Inject
    private PreservationActionPlanGenerator generator;

    /**
     * Constructs a new create executable plan object.
     */
    public CreateExecutablePlan() {
        requiredPlanState = PlanState.ANALYSED;
        correspondingPlanState = PlanState.EXECUTEABLE_PLAN_CREATED;
    }

    @Override
    public void init(Plan p) {
        super.init(p);

        // If we don't have tool parameters, we copy them from the chosen
        // alternative's config settings:
        if (plan.getExecutablePlanDefinition().getToolParameters() == null
            || "".equals(plan.getExecutablePlanDefinition().getToolParameters())) {
            plan.getExecutablePlanDefinition().setToolParameters(
                plan.getRecommendation().getAlternative().getExperiment().getSettings());
        }
    }

    @Override
    protected void saveStepSpecific() {
        saveEntity(plan);
    }

    /**
     * Reads an executable plan from the provided inputstream and stores it.
     * 
     * @param stream
     *            the executable plan
     * @throws PlanningException
     *             if an error occurred
     * @throws TavernaParserException
     *             if the executable plan could not be parsed
     */
    public void readT2flowExecutablePlan(InputStream stream) throws PlanningException, TavernaParserException {
        ByteStream bsData = this.convertToByteStream(stream);
        if (bsData == null) {
            throw new PlanningException("An error occurred while storing the executable plan");
        }

        T2FlowParser parser = T2FlowParser.createParser(new ByteArrayInputStream(bsData.getData()));

        String name = parser.getName();
        storeExecutablePlan(FileUtils.makeFilename(name) + ".t2flow", bsData);
    }

    /**
     * Generates the preservation action plan other plan information and stores
     * it in the plan.
     * 
     * @throws PlanningException
     *             if an error occurred
     */
    public void generatePreservationActionPlan() throws PlanningException {
        try {
            Document papDoc = generator.generatePreservationActionPlanDocument(plan.getSampleRecordsDefinition()
                .getCollectionProfile(), plan.getExecutablePlanDefinition(), plan);

            ByteArrayOutputStream out = new ByteArrayOutputStream();

            OutputFormat outputFormat = OutputFormat.createPrettyPrint();
            outputFormat.setEncoding(PlanXMLConstants.ENCODING);

            XMLWriter writer = new XMLWriter(out, outputFormat);

            writer.write(papDoc);
            writer.close();

            ByteStream bs = new ByteStream();
            bs.setData(out.toByteArray());
            bs.setSize(out.size());

            DigitalObject digitalObject = new DigitalObject();
            digitalObject.setFullname(PreservationActionPlanGenerator.FULL_NAME);
            digitalObject.setContentType("application/xml");
            digitalObject.setData(bs);

            digitalObjectManager.moveDataToStorage(digitalObject);

            if (plan.getPreservationActionPlan() != null && plan.getPreservationActionPlan().isDataExistent()) {
                bytestreamsToRemove.add(plan.getPreservationActionPlan().getPid());
            }

            plan.setPreservationActionPlan(digitalObject);
            addedBytestreams.add(digitalObject.getPid());
            plan.getPreservationActionPlan().touch();
        } catch (IOException e) {
            log.error("Error generating preservation action plan {}.", e.getMessage());
            throw new PlanningException("Error generating preservation action plan.", e);
        } catch (StorageException e) {
            log.error("An error occurred while storing the executable plan: {}", e.getMessage());
            throw new PlanningException("An error occurred while storing the profile", e);
        }
    }

    /**
     * Uses the experiment workflow from the recommended alternative as
     * executable plan.
     * 
     * @throws PlanningException
     *             if an error occurred during storing
     */
    public void copyRecommendedExperimentWorkflow() throws PlanningException {
        if (plan.getRecommendation().getAlternative().getExperiment() == null) {
            throw new PlanningException("The recommendation has no experiment defined.");
        }
        if (plan.getRecommendation().getAlternative().getExperiment().getWorkflow() == null) {
            throw new PlanningException("The recommendation experiment has no workflow defined.");
        }

        DigitalObject workflow = plan.getRecommendation().getAlternative().getExperiment().getWorkflow();
        DigitalObject copy = digitalObjectManager.getCopyOfDataFilledDigitalObject(workflow);
        digitalObjectManager.moveDataToStorage(copy);
        addedBytestreams.add(copy.getPid());
        plan.getExecutablePlanDefinition().setT2flowExecutablePlan(copy);
        plan.getExecutablePlanDefinition().getT2flowExecutablePlan().touch();
    }

    /**
     * Converts the input stream object to a {@link ByteStream} wrapper.
     * 
     * @param stream
     *            the stream to wrap.
     * @return the new {@link ByteStream} or null if an error occurred.
     */
    private ByteStream convertToByteStream(InputStream stream) {
        ByteStream bsData = null;
        byte[] bytes = null;
        try {
            bytes = FileUtils.inputStreamToBytes(stream);
            bsData = new ByteStream();
            bsData.setData(bytes);
            bsData.setSize(bytes.length);
        } catch (IOException e) {
            log.error("An error occurred while converting the stream: {}", e.getMessage());
        }

        return bsData;
    }

    /**
     * Stores the provided executable plan in the executable plan definition.
     * 
     * @param name
     *            the name of the plan
     * @param executablePlan
     *            the executable plan as t2flow
     * @throws PlanningException
     *             if an error occurred during storing
     */
    private void storeExecutablePlan(String name, ByteStream executablePlan) throws PlanningException {
        DigitalObject object = new DigitalObject();
        object.setContentType("application/vnd.taverna.t2flow+xml");
        object.setFullname(name);
        object.setData(executablePlan);

        try {
            digitalObjectManager.moveDataToStorage(object);
            plan.getExecutablePlanDefinition().setT2flowExecutablePlan(object);
            plan.getExecutablePlanDefinition().getT2flowExecutablePlan().touch();
            addedBytestreams.add(object.getPid());
        } catch (StorageException e) {
            log.error("An error occurred while storing the executable plan: {}", e.getMessage());
            throw new PlanningException("An error occurred while storing the profile", e);
        }
    }
}
