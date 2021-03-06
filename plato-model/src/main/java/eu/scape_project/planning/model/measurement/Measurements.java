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
package eu.scape_project.planning.model.measurement;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import javax.persistence.CascadeType;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.OneToMany;

import eu.scape_project.planning.model.values.INumericValue;

@Entity
public class Measurements implements Serializable {

    private static final long serialVersionUID = -6824569989115569984L;
    @Id
    @GeneratedValue
    private int id;

    @OneToMany(cascade = CascadeType.ALL)
    private List<Measurement> list = new ArrayList<Measurement>();

    public void addMeasurement(Measurement measurement) {
        list.add(measurement);
    }

    public Measurement getAverage() {
        if (list.size() == 0) {
            return null;
        }
        Measurement m = list.get(0);

        if (!(m.getValue() instanceof INumericValue)) {
            throw new IllegalArgumentException("cannot calculate average of nun-numeric value: " + m.getMeasureId());
        }

        double average = 0.0;
        for (Measurement meas : list) {
            INumericValue value = (INumericValue) meas.getValue();
            average += value.value();
        }
        average = average / list.size();

        Measurement measurement = new Measurement(m.getMeasureId() + ":accumulated:average(" + list.size() + ")",
            average);
        return measurement;
    }

    public int getSize() {
        return list.size();
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public List<Measurement> getList() {
        return list;
    }

    public void setList(List<Measurement> list) {
        this.list = list;
    }
}
