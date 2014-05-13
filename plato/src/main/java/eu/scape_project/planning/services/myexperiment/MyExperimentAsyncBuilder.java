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
package eu.scape_project.planning.services.myexperiment;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Future;

import javax.ejb.AsyncResult;
import javax.ejb.Asynchronous;
import javax.ejb.Stateful;
import javax.inject.Inject;

import org.slf4j.Logger;

import eu.scape_project.planning.exception.PlanningException;
import eu.scape_project.planning.model.Alternative;
import eu.scape_project.planning.model.ByteStream;
import eu.scape_project.planning.model.DigitalObject;
import eu.scape_project.planning.model.Parameter;
import eu.scape_project.planning.model.PreservationActionDefinition;
import eu.scape_project.planning.model.User;
import eu.scape_project.planning.services.myexperiment.MyExperimentRESTClient.ComponentQuery;
import eu.scape_project.planning.services.myexperiment.domain.ComponentConstants;
import eu.scape_project.planning.services.myexperiment.domain.WorkflowDescription;
import eu.scape_project.planning.services.myexperiment.domain.WorkflowDescription.Port;
import eu.scape_project.planning.services.myexperiment.domain.WorkflowInfo;
import eu.scape_project.planning.services.taverna.generator.T2FlowExecutablePlanGenerator;
import eu.scape_project.planning.services.taverna.generator.T2FlowExecutablePlanGenerator.InputSource;
import eu.scape_project.planning.services.taverna.generator.T2FlowExecutablePlanGenerator.RelatedObject;
import eu.scape_project.planning.utils.FileUtils;

/**
 * Async builder for executable plans based on components from myExperiment.
 */
@Stateful
public class MyExperimentAsyncBuilder {
    @Inject
    private Logger log;

    @Inject
    private User user;

    @Inject
    private MyExperimentRESTClient myExperiment;

    /**
     * Generates an executable plan.
     * 
     * @param planName
     *            the plan name
     * @param alternative
     *            the alternative to base the workflow on
     * @param measures
     *            the measures to include in the workflow
     * @param sourceMimetype
     *            the source mimetype of the workflow
     * @param targetMimetype
     *            the target mimetype of the workflow
     * @return an executable plan
     * @throws PlanningException
     *             if an error occurred during generation
     */
    @Asynchronous
    public Future<DigitalObject> generateExecutablePlan(final String planName, final Alternative alternative,
        final List<String> measures, final String sourceMimetype, final String targetMimetype) throws PlanningException {
        DigitalObject workflow = new DigitalObject();

        String name = planName + " - " + alternative.getName();
        T2FlowExecutablePlanGenerator generator = new T2FlowExecutablePlanGenerator(name, user.getFullName());

        // Add ports
        generator.addSourcePort();
        generator.addTargetPort();

        // Migration action
        PreservationActionDefinition action = alternative.getAction();
        if (action != null) {
            try {
                WorkflowDescription wf = MyExperimentRESTClient.getWorkflow(action.getDescriptor());
                HashMap<String, String> parameters = new HashMap<String, String>();
                for (Parameter p : action.getParams()) {
                    parameters.put(p.getName(), p.getValue());
                }
                wf.readMetadata();
                String workflowContent = MyExperimentRESTClient.getWorkflowContent(wf);
                generator.setMigrationComponent(wf, workflowContent, parameters);
            } catch (Exception e) {
                log.warn("An error occured querying myExperiment migration component.", e.getMessage());
                throw new PlanningException("An error occured querying myExperiment migration component", e);
            }
        }

        // Add QA components
        addQaComponents(generator, measures, sourceMimetype, targetMimetype);

        // Create digital object
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        OutputStreamWriter writer = new OutputStreamWriter(out);

        try {
            generator.generate(writer);
        } catch (IOException e) {
            log.warn("An error occured generating the executable plan.", e.getMessage());
            throw new PlanningException("An error occured generating the executable plan.", e);
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    log.warn("An error occured closing the executable plan generator writer.", e.getMessage());
                    throw new PlanningException("An error occured closing the executable plan generator writer.", e);
                }
            }
        }

        byte[] data = out.toByteArray();
        ByteStream bsData = new ByteStream();
        bsData.setData(data);

        workflow.setContentType("application/vnd.taverna.t2flow+xml");
        workflow.setData(bsData);
        workflow.setFullname(FileUtils.makeFilename(name + ".t2flow"));

        return new AsyncResult<DigitalObject>(workflow);
    }

    /**
     * Adds QA components to the generator for the provided measures.
     * 
     * @param generator
     *            the generate used to add components
     * @param measures
     *            measures to look for components
     * @param sourceMimetype
     *            source mimetype for the generator
     * @param targetMimetype
     *            target mimetype for the generator
     */
    private void addQaComponents(final T2FlowExecutablePlanGenerator generator, final List<String> measures,
        final String sourceMimetype, final String targetMimetype) {

        List<RecommendedComponent> recommendedComponents = recommendComponents(measures, sourceMimetype, targetMimetype);
        for (RecommendedComponent recommendedComponent : recommendedComponents) {
            String workflowContent = MyExperimentRESTClient.getWorkflowContent(recommendedComponent.workflow);
            if (ComponentConstants.PROFILE_OBJECT_QA.equals(recommendedComponent.workflow.getProfile())) {
                generator.addQaComponent(recommendedComponent.workflow, workflowContent,
                    recommendedComponent.leftSource, recommendedComponent.rightSource, new HashMap<String, String>(0),
                    recommendedComponent.measures, recommendedComponent.relatedObject);
            } else if (ComponentConstants.PROFILE_CC.equals(recommendedComponent.workflow.getProfile())) {
                generator.addCcComponent(recommendedComponent.workflow, workflowContent,
                    new HashMap<String, String>(0), recommendedComponent.measures);
            } else {
                log.debug("Component search returned component with invalid or no profile.");
            }

        }
    }

    /**
     * Finds QA components for the provided measures.
     * 
     * @param measures
     *            measures to find components for
     * @param sourceMimetype
     *            source mimetype of the workflow
     * @param targetMimetype
     *            target mimetype of the workflow
     * @return a list of workflow description
     */
    public List<RecommendedComponent> recommendComponents(final List<String> measures, final String sourceMimetype,
        final String targetMimetype) {
        List<RecommendedComponent> recommendedComponents = new ArrayList<RecommendedComponent>();
        Set<String> processedMeasures = new HashSet<String>();

        for (String measure : measures) {
            if (!processedMeasures.contains(measure)) {
                List<WorkflowInfo> qaWfs = queryQaComponents(measure, sourceMimetype, targetMimetype);
                Iterator<WorkflowInfo> qaIt = qaWfs.iterator();
                if (qaIt.hasNext()) {
                    WorkflowInfo wfi = qaIt.next();
                    WorkflowDescription wfd = MyExperimentRESTClient.getWorkflow(wfi.getDescriptor());
                    wfd.readMetadata();
                    List<Port> outputPorts = wfd.getOutputPorts();

                    List<String> leftMeasures = new ArrayList<String>();
                    List<String> rightMeasures = new ArrayList<String>();

                    for (Port port : outputPorts) {
                        if (measures.contains(port.getValue())) {
                            if (port.getRelatedObject() == null) {
                                leftMeasures.add(port.getValue());
                                rightMeasures.add(port.getValue());
                            } else if (ComponentConstants.VALUE_LEFT_OBJECT.equals(port.getRelatedObject())) {
                                leftMeasures.add(port.getValue());
                            } else if (ComponentConstants.VALUE_RIGHT_OBJECT.equals(port.getRelatedObject())) {
                                rightMeasures.add(port.getValue());
                            }
                        }
                    }
                    boolean acceptsLeftMimetype = wfd.acceptsLeftMimetype(targetMimetype);
                    boolean acceptsRightMimetype = wfd.acceptsRightMimetype(targetMimetype);
                    if (acceptsLeftMimetype && acceptsRightMimetype) {
                        if (leftMeasures.size() > rightMeasures.size()) {
                            processedMeasures.addAll(leftMeasures);
                            recommendedComponents.add(new RecommendedComponent(wfd, leftMeasures,
                                InputSource.TARGET_OBJECT, InputSource.SOURCE_OBJECT, RelatedObject.LEFT_OBJECT));
                        } else {
                            processedMeasures.addAll(rightMeasures);
                            recommendedComponents.add(new RecommendedComponent(wfd, rightMeasures,
                                InputSource.SOURCE_OBJECT, InputSource.TARGET_OBJECT, RelatedObject.RIGHT_OBJECT));
                        }
                    } else if (acceptsLeftMimetype) {
                        processedMeasures.addAll(leftMeasures);
                        recommendedComponents.add(new RecommendedComponent(wfd, leftMeasures,
                            InputSource.TARGET_OBJECT, InputSource.SOURCE_OBJECT, RelatedObject.LEFT_OBJECT));
                    } else if (acceptsRightMimetype) {
                        processedMeasures.addAll(rightMeasures);
                        recommendedComponents.add(new RecommendedComponent(wfd, rightMeasures,
                            InputSource.SOURCE_OBJECT, InputSource.TARGET_OBJECT, RelatedObject.RIGHT_OBJECT));
                    }
                } else {
                    recommendCcComponents(recommendedComponents, processedMeasures, measures, measure, targetMimetype);
                }
            }
        }

        return recommendedComponents;
    }

    /**
     * Recommends CC components and updates {@code recommendetComponents} and
     * {@code processMeasures}.
     * 
     * @param recommendedComponents
     *            the recommended measures
     * @param processedMeasures
     *            the process measures
     * @param measures
     *            measures to consider
     * @param measure
     *            measure to find
     * @param targetMimetype
     *            the target mimetype
     */
    private void recommendCcComponents(List<RecommendedComponent> recommendedComponents, Set<String> processedMeasures,
        final List<String> measures, final String measure, final String targetMimetype) {
        List<WorkflowInfo> ccWfs = queryCcComponents(measure, targetMimetype);
        Iterator<WorkflowInfo> ccIt = ccWfs.iterator();
        if (ccIt.hasNext()) {
            WorkflowInfo wfi = ccIt.next();
            WorkflowDescription wfd = MyExperimentRESTClient.getWorkflow(wfi.getDescriptor());
            wfd.readMetadata();
            List<Port> outputPorts = wfd.getOutputPorts();
            List<String> ccMeasures = new ArrayList<String>();
            for (Port port : outputPorts) {
                if (measures.contains(port.getValue())) {
                    ccMeasures.add(port.getValue());
                }
            }
            processedMeasures.addAll(ccMeasures);
            recommendedComponents.add(new RecommendedComponent(wfd, ccMeasures, null, null, null));
        }

    }

    /**
     * Queries myExperiment for workflows that provide the required measure for
     * the digital objects.
     * 
     * @param measure
     *            the required measure
     * @param sourceMimetype
     *            the source mimetype
     * @param targetMimetype
     *            the target mimetype
     * @return a list of workflows
     */
    private List<WorkflowInfo> queryQaComponents(String measure, String sourceMimetype, String targetMimetype) {
        ComponentQuery q = myExperiment.createComponentQuery();

        q.addHandlesMimetype(sourceMimetype, targetMimetype).addHandlesMimetypeWildcard(sourceMimetype, targetMimetype)
            .addHandlesMimetypes(sourceMimetype, targetMimetype)
            .addHandlesMimetypesWildcard(sourceMimetype, targetMimetype);
        if (!sourceMimetype.equals(targetMimetype)) {
            q.addHandlesMimetypes(targetMimetype, sourceMimetype).addHandlesMimetypesWildcard(targetMimetype,
                sourceMimetype);
        }
        q.addInputPort(ComponentConstants.VALUE_LEFT_OBJECT).addInputPort(ComponentConstants.VALUE_RIGHT_OBJECT)
            .addMeasureOutputPort(measure).finishQuery();

        return myExperiment.searchComponents(q);
    }

    /**
     * Queries myExperiment for workflows that provide the required measure for
     * the digital objects.
     * 
     * @param measure
     *            the required measure
     * @param targetMimetype
     *            the target mimetype
     * @return a list of workflows
     */
    private List<WorkflowInfo> queryCcComponents(String measure, String targetMimetype) {
        ComponentQuery q = myExperiment.createComponentQuery();

        q.addHandlesMimetype(targetMimetype).addHandlesMimetypeWildcard(targetMimetype)
            .addInputPort(ComponentConstants.VALUE_SOURCE_OBJECT).addMeasureOutputPort(measure).finishQuery();

        return myExperiment.searchComponents(q);
    }

    /**
     * Describes a recommended component and related data.
     */
    public static final class RecommendedComponent {
        /**
         * Description of the recommended component workflow.
         */
        public final WorkflowDescription workflow;

        /**
         * Measures recommended that this component is used for.
         */
        public final List<String> measures;

        /**
         * The related object of the measures for the component.
         */
        public final RelatedObject relatedObject;

        /**
         * Source for left input.
         */
        public final InputSource leftSource;

        /**
         * Source for right input.
         */
        public final InputSource rightSource;

        /**
         * Creates a new recommended component.
         * 
         * @param workflowDescription
         *            the workflow description
         * @param measures
         *            the measures of this recommended component
         * @param relatedObject
         *            the related object or null
         */
        private RecommendedComponent(final WorkflowDescription workflowDescription, final List<String> measures,
            final InputSource leftSource, final InputSource rightSource, final RelatedObject relatedObject) {
            this.workflow = workflowDescription;
            this.measures = measures;
            this.leftSource = leftSource;
            this.rightSource = rightSource;
            this.relatedObject = relatedObject;
        }
    }
}
