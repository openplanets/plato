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
package eu.scape_project.planning.plato.wfview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Iterator;
import java.util.List;

import javax.enterprise.inject.Instance;

import org.junit.Test;

import eu.scape_project.planning.exception.PlanningException;
import eu.scape_project.planning.model.Plan;
import eu.scape_project.planning.plato.wfview.full.AnalyseResultsView;
import eu.scape_project.planning.plato.wfview.full.CreateExecutablePlanView;
import eu.scape_project.planning.plato.wfview.full.DefineAlternativesView;
import eu.scape_project.planning.plato.wfview.full.DefineBasisView;
import eu.scape_project.planning.plato.wfview.full.DefinePreservationPlanView;
import eu.scape_project.planning.plato.wfview.full.DefineSampleRecordsView;
import eu.scape_project.planning.plato.wfview.full.DevelopExperimentsView;
import eu.scape_project.planning.plato.wfview.full.EvaluateExperimentsView;
import eu.scape_project.planning.plato.wfview.full.IdentifyRequirementsView;
import eu.scape_project.planning.plato.wfview.full.RunExperimentsView;
import eu.scape_project.planning.plato.wfview.full.SetImportanceFactorsView;
import eu.scape_project.planning.plato.wfview.full.TakeGoDecisionView;
import eu.scape_project.planning.plato.wfview.full.TransformMeasuredValuesView;
import eu.scape_project.planning.plato.wfview.full.ValidatePlanView;

public class WorkflowStepFactoryTest {
	
	ViewWorkflowFactory wfStepFactory;

	public WorkflowStepFactoryTest() {
		wfStepFactory = new ViewWorkflowFactory();

		Instance<DefineBasisView> defineBasisSource = mock(Instance.class);
		when(defineBasisSource.get()).thenReturn(new DefineBasisView());
		wfStepFactory.setDefineBasisSource(defineBasisSource);
		
		Instance<DefineSampleRecordsView> defineSampleRecordsSource = mock(Instance.class);
		when(defineSampleRecordsSource.get()).thenReturn(new DefineSampleRecordsView());
		wfStepFactory.setDefineSampleRecordsSource(defineSampleRecordsSource);

		Instance<IdentifyRequirementsView> identifyRequirementsSource = mock(Instance.class);
		when(identifyRequirementsSource.get()).thenReturn(new IdentifyRequirementsView());
		wfStepFactory.setIdentifyRequirementsSource(identifyRequirementsSource);
		
		Instance<DefineAlternativesView> defineAlternativesSource = mock(Instance.class);
		when(defineAlternativesSource.get()).thenReturn(new DefineAlternativesView());
		wfStepFactory.setDefineAlternativesSource(defineAlternativesSource);
		
		Instance<TakeGoDecisionView> takeGoDecisionSource = mock(Instance.class);
		when(takeGoDecisionSource.get()).thenReturn(new TakeGoDecisionView());
		wfStepFactory.setTakeGoDecisionSource(takeGoDecisionSource);
		
		Instance<DevelopExperimentsView> developExperimentsSource = mock(Instance.class);
		when(developExperimentsSource.get()).thenReturn(new DevelopExperimentsView());
		wfStepFactory.setDevelopExperimentsSource(developExperimentsSource);

		Instance<RunExperimentsView> runExperimentsSource = mock(Instance.class);
		when(runExperimentsSource.get()).thenReturn(new RunExperimentsView());
		wfStepFactory.setRunExperimentsSource(runExperimentsSource);

		Instance<EvaluateExperimentsView> evaluateExperimentsSource = mock(Instance.class);
		when(evaluateExperimentsSource.get()).thenReturn(new EvaluateExperimentsView());
		wfStepFactory.setEvaluateExperimentsSource(evaluateExperimentsSource);

		Instance<TransformMeasuredValuesView> transformMeasuredValuesSource = mock(Instance.class);
		when(transformMeasuredValuesSource.get()).thenReturn(new TransformMeasuredValuesView());
		wfStepFactory.setTransformMeasuredValuesSource(transformMeasuredValuesSource);
		
		Instance<SetImportanceFactorsView> setImportanceFactorsSource = mock(Instance.class);
		when(setImportanceFactorsSource.get()).thenReturn(new SetImportanceFactorsView());
		wfStepFactory.setSetImportanceFactorsSource(setImportanceFactorsSource);
		
		Instance<AnalyseResultsView> analyseResultsSource = mock(Instance.class);
		when(analyseResultsSource.get()).thenReturn(new AnalyseResultsView());
		wfStepFactory.setAnalyseResultsSource(analyseResultsSource);
		
		Instance<CreateExecutablePlanView> createExecutablePlanSource = mock(Instance.class);
		when(createExecutablePlanSource.get()).thenReturn(new CreateExecutablePlanView());
		wfStepFactory.setCreateExecutablePlanSource(createExecutablePlanSource);
		
		Instance<DefinePreservationPlanView> definePreservationPlanSource = mock(Instance.class);
		when(definePreservationPlanSource.get()).thenReturn(new DefinePreservationPlanView());
		wfStepFactory.setDefinePreservationPlanSource(definePreservationPlanSource);

		Instance<ValidatePlanView> validatePlanSource = mock(Instance.class);
		when(validatePlanSource.get()).thenReturn(new ValidatePlanView());
		wfStepFactory.setValidatePlanSource(validatePlanSource);

	}
			
	@Test
	public void constructWorkflowSteps_standardPlanReturnsAllStandardSteps() throws PlanningException {
		Plan plan = mock(Plan.class);
		
		// number of steps
		List<AbstractView> result = wfStepFactory.constructWorkflowSteps(plan);
		assertEquals(14, result.size());
		
		// correct steps
		Iterator<AbstractView> stepIter = result.iterator();
		assertTrue(stepIter.next() instanceof DefineBasisView);
		assertTrue(stepIter.next() instanceof DefineSampleRecordsView);
		assertTrue(stepIter.next() instanceof IdentifyRequirementsView);
		assertTrue(stepIter.next() instanceof DefineAlternativesView);
		assertTrue(stepIter.next() instanceof TakeGoDecisionView);
		assertTrue(stepIter.next() instanceof DevelopExperimentsView);
		assertTrue(stepIter.next() instanceof RunExperimentsView);
		assertTrue(stepIter.next() instanceof EvaluateExperimentsView);
		assertTrue(stepIter.next() instanceof TransformMeasuredValuesView);
		assertTrue(stepIter.next() instanceof SetImportanceFactorsView);
		assertTrue(stepIter.next() instanceof AnalyseResultsView);
		assertTrue(stepIter.next() instanceof CreateExecutablePlanView);
		assertTrue(stepIter.next() instanceof DefinePreservationPlanView);
		assertTrue(stepIter.next() instanceof ValidatePlanView);
	}

}
