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
package eu.scape_project.planning.validation;

import java.io.Serializable;

/**
 * Represents a validation error.
 * 
 * TODO For multi-lingual error messages we have to add a message identifier rather than the message itself. 
 *  
 * @author Michael Kraxner
 *
 */
public class ValidationError implements Serializable {
	private static final long serialVersionUID = 3615070773368229163L;
	
	private String reason;
	
	private Object invalidObject;
	
	public ValidationError(String reason) {
		this.reason = reason;
	}
	public ValidationError(String reason, Object invalidObject) {
		this.reason = reason;
		this.invalidObject = invalidObject;
	}

	public String getMessage() {
		return reason;
	}

	public void setMessage(String message) {
		this.reason = message;
	}
	public Object getInvalidObject() {
		return invalidObject;
	}

}
