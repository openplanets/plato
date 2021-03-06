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
package eu.scape_project.planning.plato.wfview.full;

import java.util.ArrayList;
import java.util.List;

import javax.enterprise.context.ConversationScoped;
import javax.faces.model.SelectItem;
import javax.inject.Inject;
import javax.inject.Named;

import org.slf4j.Logger;

import eu.scape_project.planning.model.Alternative;
import eu.scape_project.planning.model.Decision;
import eu.scape_project.planning.model.Plan;
import eu.scape_project.planning.model.PlanState;
import eu.scape_project.planning.model.Decision.GoDecision;
import eu.scape_project.planning.plato.wf.AbstractWorkflowStep;
import eu.scape_project.planning.plato.wf.TakeGoDecision;
import eu.scape_project.planning.plato.wfview.AbstractView;
import eu.scape_project.planning.utils.FacesMessages;

@Named("takeGoDecision")
@ConversationScoped
public class TakeGoDecisionView extends AbstractView {
    private static final long serialVersionUID = 1L;

    @Inject
    private Logger log;

    @Inject
    private FacesMessages facesMessages;

    @Inject
    private TakeGoDecision takeGoDecision;

    private List<Alternative> alternatives;

    private Decision goDecision;

    public TakeGoDecisionView() {
        currentPlanState = PlanState.ALTERNATIVES_DEFINED;
        name = "Take Go Decision";
        viewUrl = "/plan/takegodecision.jsf";
        group = "menu.evaluateAlternatives";

    }

    public void init(Plan plan) {
        super.init(plan);
        alternatives = plan.getAlternativesDefinition().getAlternatives();
        goDecision = plan.getDecision();
    }

    public List<Alternative> getAlternatives() {
        return alternatives;
    }

    public void setAlternatives(List<Alternative> alternatives) {
        this.alternatives = alternatives;
    }

    public Decision getGoDecision() {
        return goDecision;
    }

    public void setGoDecision(Decision goDecision) {
        this.goDecision = goDecision;
    }

    public List<SelectItem> getGoDecisionSelectItems() {
        List<SelectItem> selectItems = new ArrayList<SelectItem>();
        selectItems.add(new SelectItem(GoDecision.UNDEFINED, "Undefined"));
        selectItems.add(new SelectItem(GoDecision.GO, "Go"));
        selectItems.add(new SelectItem(GoDecision.PROVISIONAL_GO, "Provisional Go"));
        selectItems.add(new SelectItem(GoDecision.DEFERRED_GO, "Deferred Go"));
        selectItems.add(new SelectItem(GoDecision.NO_GO, "No Go"));

        return selectItems;
    }

    @Override
    protected AbstractWorkflowStep getWfStep() {
        return takeGoDecision;
    }
}
