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
package eu.scape_project.pw.idp.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityListeners;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToMany;
import javax.persistence.PrePersist;
import javax.persistence.Transient;
import javax.validation.constraints.Size;

import org.hibernate.validator.constraints.Email;

/**
 * User object used in the identity provider.
 */
@Entity
@EntityListeners(PasswordHashingEntityListener.class)
public class IdpUser {
    @Id
    @GeneratedValue
    private int id;

    /**
     * Unique name of the user.
     */
    @Size(min = 4)
    @Column(unique = true)
    private String username;

    /**
     * First name of the user.
     */
    private String firstName;

    /**
     * Last name of the user.
     */
    private String lastName;

    /**
     * E-Mail of the user.
     */
    @Email
    @Column(unique = true)
    private String email;

    /**
     * Password of the user.
     */
    @Transient
    @Size(min = 6)
    private String plainPassword;

    /**
     * Password hash of the user.
     */
    private String password;

    /**
     * Roles of this user.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    private List<IdpRole> roles = new ArrayList<IdpRole>();

    /**
     * State of the user. Used e.g. to identify if a user is just created or
     * already activated (e.g. by email validation)
     */
    @Enumerated(EnumType.STRING)
    private IdpUserState status;

    /**
     * Token required to be allowed to execute specific actions on the user
     * (e.g. activate a created user) (After a token is used one time it should
     * be deleted)
     */
    private String actionToken;

    private Date dateCreated;

    /**
     * Constructor for IdpUser.
     */
    public IdpUser() {
        this.status = IdpUserState.CREATED;
    }

    /**
     * Fills the date created before persisting the object.
     */
    @SuppressWarnings("unused")
    @PrePersist
    private void fillDateCreated() {
        dateCreated = new Date();
    }

    // ---------- getter/setter ----------

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(final String username) {
        this.username = username;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(final String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(final String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(final String email) {
        this.email = email;
    }

    public String getPlainPassword() {
        return plainPassword;
    }

    public void setPlainPassword(final String password) {
        this.plainPassword = password;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }

    public List<IdpRole> getRoles() {
        return roles;
    }

    public void setRoles(final List<IdpRole> roles) {
        this.roles = roles;
    }

    public IdpUserState getStatus() {
        return status;
    }

    public void setStatus(IdpUserState status) {
        this.status = status;
    }

    public String getActionToken() {
        return actionToken;
    }

    public void setActionToken(String actionToken) {
        this.actionToken = actionToken;
    }

    public Date getDateCreated() {
        return dateCreated;
    }

}
