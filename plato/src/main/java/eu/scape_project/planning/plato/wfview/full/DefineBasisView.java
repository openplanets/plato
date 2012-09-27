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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.enterprise.context.ConversationScoped;
import javax.inject.Inject;
import javax.inject.Named;

import eu.scape_project.planning.model.Plan;
import eu.scape_project.planning.model.PlanState;
import eu.scape_project.planning.model.PolicyNode;
import eu.scape_project.planning.plato.bean.TreeHelperBean;
import eu.scape_project.planning.plato.wf.AbstractWorkflowStep;
import eu.scape_project.planning.plato.wf.DefineBasis;
import eu.scape_project.planning.plato.wfview.AbstractView;

/**
 * Bean for the viewWorkflow step 'Define Basis'.
 */
@Named("defineBasis")
@ConversationScoped
public class DefineBasisView extends AbstractView implements Serializable {
    private static final long serialVersionUID = 8237053627553012469L;

    @Inject
    private DefineBasis defineBasis;

    @Inject
    private TreeHelperBean treeHelper;

    public DefineBasisView() {
        currentPlanState = PlanState.INITIALISED;
        name = "Define Basis";
        viewUrl = "/plan/definebasis.jsf";
        group = "menu.defineRequirements";
    }

    /**
     * Initializes 'Define Basis' viewWorkflow step, at the moment just the
     * triggers.
     * 
     * @see AbstractView#init()
     */
    public void init(Plan plan) {
        super.init(plan);

        // expand all nodes of the displayed policy-tree (if existent)
        treeHelper.expandAll(plan.getProjectBasis().getPolicyTree().getRoot());
    }

    @Override
    protected AbstractWorkflowStep getWfStep() {
        return defineBasis;
    }

    // /**
    // * Method responsible for triggering removal of the current assigned
    // * policy-tree.
    // */
    // public void removePolicyTree() {
    // defineBasis.removePolicyTree();
    // }

    /**
     * Method responsible for returning the policy-tree appropriate for
     * displaying with rich:treeModelRecursiveAdaptor. (This richfaces component
     * requires a list of nodes to be returned.)
     * 
     * @return Policy-tree in list representation (for use in
     *         rich:treeModelRecursiveAdaptor).
     */
    public List<PolicyNode> getPolicyRoot() {
        List<PolicyNode> l = new ArrayList<PolicyNode>();
        if (plan.getProjectBasis().getPolicyTree() != null) {
            l.add(plan.getProjectBasis().getPolicyTree().getRoot());
        }
        return l;
    }

    // ---------- getter/setter ----------

    public TreeHelperBean getTreeHelper() {
        return treeHelper;
    }
}
