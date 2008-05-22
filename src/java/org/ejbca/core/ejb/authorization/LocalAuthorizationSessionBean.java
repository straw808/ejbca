/*************************************************************************
 *                                                                       *
 *  EJBCA: The OpenSource Certificate Authority                          *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public           *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/

package org.ejbca.core.ejb.authorization;

import java.security.cert.X509Certificate;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;

import javax.ejb.CreateException;
import javax.ejb.EJBException;
import javax.ejb.FinderException;

import org.apache.commons.lang.StringUtils;
import org.ejbca.core.ejb.BaseSessionBean;
import org.ejbca.core.ejb.JNDINames;
import org.ejbca.core.ejb.ServiceLocator;
import org.ejbca.core.ejb.ca.caadmin.ICAAdminSessionLocal;
import org.ejbca.core.ejb.ca.caadmin.ICAAdminSessionLocalHome;
import org.ejbca.core.ejb.ca.store.ICertificateStoreSessionLocal;
import org.ejbca.core.ejb.ca.store.ICertificateStoreSessionLocalHome;
import org.ejbca.core.ejb.log.ILogSessionLocal;
import org.ejbca.core.ejb.log.ILogSessionLocalHome;
import org.ejbca.core.ejb.ra.raadmin.IRaAdminSessionLocal;
import org.ejbca.core.ejb.ra.raadmin.IRaAdminSessionLocalHome;
import org.ejbca.core.ejb.ra.userdatasource.IUserDataSourceSessionLocal;
import org.ejbca.core.ejb.ra.userdatasource.IUserDataSourceSessionLocalHome;
import org.ejbca.core.model.InternalResources;
import org.ejbca.core.model.authorization.AccessRule;
import org.ejbca.core.model.authorization.AdminEntity;
import org.ejbca.core.model.authorization.AdminGroup;
import org.ejbca.core.model.authorization.AdminGroupExistsException;
import org.ejbca.core.model.authorization.AuthenticationFailedException;
import org.ejbca.core.model.authorization.AuthorizationDeniedException;
import org.ejbca.core.model.authorization.Authorizer;
import org.ejbca.core.model.authorization.AvailableAccessRules;
import org.ejbca.core.model.log.Admin;
import org.ejbca.core.model.log.LogConstants;
import org.ejbca.util.JDBCUtil;


/**
 * Stores data used by web server clients.
 * Uses JNDI name for datasource as defined in env 'Datasource' in ejb-jar.xml.
 *
 * @version $Id$
 *
 * @ejb.bean
 *   description="Session bean handling interface with ra authorization"
 *   display-name="AuthorizationSessionSB"
 *   name="AuthorizationSession"
 *   jndi-name="AuthorizationSession"
 *   local-jndi-name="AuthorizationSessionLocal"
 *   view-type="both"
 *   type="Stateless"
 *   transaction-type="Container"
 *
 * @ejb.transaction type="Required"
 *
 * @weblogic.enable-call-by-reference True
 *
 * @ejb.env-entry
 * name="DataSource"
 * type="java.lang.String"
 * value="${datasource.jndi-name-prefix}${datasource.jndi-name}"
 *
 * @ejb.env-entry
 *   description="Custom Available Access Rules, use ';' to separate multiple accessrules"
 *   name="CustomAvailableAccessRules"
 *   type="java.lang.String"
 *   value=""
 *
 * @ejb.ejb-external-ref
 *   description="The log session bean"
 *   view-type="local"
 *   ref-name="ejb/LogSessionLocal"
 *   type="Session"
 *   home="org.ejbca.core.ejb.log.ILogSessionLocalHome"
 *   business="org.ejbca.core.ejb.log.ILogSessionLocal"
 *   link="LogSession"
 *
 * @ejb.ejb-external-ref
 *   description="The RA Session Bean"
 *   view-type="local"
 *   ref-name="ejb/RaAdminSessionLocal"
 *   type="Session"
 *   home="org.ejbca.core.ejb.ra.raadmin.IRaAdminSessionLocalHome"
 *   business="org.ejbca.core.ejb.ra.raadmin.IRaAdminSessionLocal"
 *   link="RaAdminSession"
 *
 * @ejb.ejb-external-ref
 *   description="The CAAdmin Session Bean"
 *   view-type="local"
 *   ref-name="ejb/CAAdminSessionLocal"
 *   type="Session"
 *   home="org.ejbca.core.ejb.ca.caadmin.ICAAdminSessionLocalHome"
 *   business="org.ejbca.core.ejb.ca.caadmin.ICAAdminSessionLocal"
 *   link="CAAdminSession"
 *
 * @ejb.ejb-external-ref
 *   description="The Certificate Store Session bean"
 *   view-type="local"
 *   ref-name="ejb/CertificateStoreSessionLocal"
 *   type="Session"
 *   home="org.ejbca.core.ejb.ca.store.ICertificateStoreSessionLocalHome"
 *   business="org.ejbca.core.ejb.ca.store.ICertificateStoreSessionLocal"
 *   link="CertificateStoreSession"
 *   
 * @ejb.ejb-external-ref
 *   description="The User Data Source Session bean"
 *   view-type="local"
 *   ref-name="ejb/UserDataSourceSessionLocal"
 *   type="Session"
 *   home="org.ejbca.core.ejb.ra.userdatasource.IUserDataSourceSessionLocalHome"
 *   business="org.ejbca.core.ejb.ra.userdatasource.IUserDataSourceSessionLocal"
 *   link="UserDataSourceSession"
 *
 * @ejb.ejb-external-ref
 *   description="Authorization Tree Update Bean"
 *   view-type="local"
 *   ref-name="ejb/AuthorizationTreeUpdateDataLocal"
 *   type="Entity"
 *   home="org.ejbca.core.ejb.authorization.AuthorizationTreeUpdateDataLocalHome"
 *   business="org.ejbca.core.ejb.authorization.AuthorizationTreeUpdateDataLocal"
 *   link="AuthorizationTreeUpdateData"
 *
 * @ejb.ejb-external-ref
 *   description="Admin Groups"
 *   view-type="local"
 *   ref-name="ejb/AdminGroupDataLocal"
 *   type="Entity"
 *   home="org.ejbca.core.ejb.authorization.AdminGroupDataLocalHome"
 *   business="org.ejbca.core.ejb.authorization.AdminGroupDataLocal"
 *   link="AdminGroupData"
 *
 * @ejb.home
 *   extends="javax.ejb.EJBHome"
 *   local-extends="javax.ejb.EJBLocalHome"
 *   local-class="org.ejbca.core.ejb.authorization.IAuthorizationSessionLocalHome"
 *   remote-class="org.ejbca.core.ejb.authorization.IAuthorizationSessionHome"
 *
 * @ejb.interface
 *   extends="javax.ejb.EJBObject"
 *   local-extends="javax.ejb.EJBLocalObject"
 *   local-class="org.ejbca.core.ejb.authorization.IAuthorizationSessionLocal"
 *   remote-class="org.ejbca.core.ejb.authorization.IAuthorizationSessionRemote"
 *
 * @jonas.bean
 *   ejb-name="AuthorizationSession"
 */
public class LocalAuthorizationSessionBean extends BaseSessionBean {

    /**
     * Constant indicating minimum time between updates. In milliseconds
     */
    public static final long MIN_TIME_BETWEEN_UPDATES = 60000 * 1;
    
    /** Internal localization of logs and errors */
    private static final InternalResources intres = InternalResources.getInstance();

    /**
     * The home interface of  AdminGroupData entity bean
     */
    private AdminGroupDataLocalHome admingrouphome = null;

    /**
     * The home interface of AuthorizationTreeUpdateData entity bean
     */
    private AuthorizationTreeUpdateDataLocalHome authorizationtreeupdatehome = null;

    /**
     * help variable used to check that authorization trees is updated.
     */
    private int authorizationtreeupdate = -1;

    /**
     * help variable used to control that update isn't performed to often.
     */
    private long lastupdatetime = -1;

    /**
     * The local interface of  log session bean
     */
    private ILogSessionLocal logsession = null;

    /**
     * The local interface of  raadmin session bean
     */
    private IRaAdminSessionLocal raadminsession = null;

    /**
     * The local interface of  ca admim session bean
     */
    private ICAAdminSessionLocal caadminsession = null;

    /**
     * The local interface of certificate store session bean
     */
    private ICertificateStoreSessionLocal certificatestoresession = null;

    /**
     * The local interface of user data source session bean
     */
    private IUserDataSourceSessionLocal userdatasourcesession = null;
    
    private Authorizer authorizer = null;

    private String[] customaccessrules = null;

    private static final String DEFAULTGROUPNAME = "DEFAULT";
    protected static final String PUBLICWEBGROUPNAME = "Public Web Users"; // protected so it's available for unit tests

    /**
     * Default create for SessionBean without any creation Arguments.
     *
     * @throws CreateException if bean instance can't be created
     */
    public void ejbCreate() throws CreateException {
        debug(">ejbCreate()");
        ServiceLocator locator = ServiceLocator.getInstance();
        admingrouphome = (AdminGroupDataLocalHome) locator.getLocalHome(AdminGroupDataLocalHome.COMP_NAME);
        authorizationtreeupdatehome = (AuthorizationTreeUpdateDataLocalHome) locator.getLocalHome(AuthorizationTreeUpdateDataLocalHome.COMP_NAME);
        String customrules = locator.getString("java:comp/env/CustomAvailableAccessRules");
        if (customrules == null) {
        	customrules = "";
        } 
        customaccessrules = StringUtils.split(customrules, ';');
        debug("<ejbCreate()");
    }
    
    private Authorizer getAuthorizer() {
        try {
        	if (authorizer == null) {
                authorizer = new Authorizer(getAdminGroups(), admingrouphome,
                        getLogSession(), getCertificateStoreSession(), getRaAdminSession(), getCAAdminSession(), new Admin(Admin.TYPE_INTERNALUSER), LogConstants.MODULE_AUTHORIZATION);
        	}
        	return authorizer;
        } catch (Exception e) {
            throw new EJBException(e);
        }
    	
    }


    /**
     * Gets connection to log session bean
     *
     * @return Connection
     */
    private ILogSessionLocal getLogSession() {
        if (logsession == null) {
            try {
                ILogSessionLocalHome logsessionhome = (ILogSessionLocalHome) ServiceLocator.getInstance().getLocalHome(ILogSessionLocalHome.COMP_NAME);
                logsession = logsessionhome.create();
            } catch (Exception e) {
                throw new EJBException(e);
            }
        }
        return logsession;
    } //getLogSession


    /**
     * Gets connection to ra admin session bean
     *
     * @return Connection
     */
    private IRaAdminSessionLocal getRaAdminSession() {
        if (raadminsession == null) {
            try {
                IRaAdminSessionLocalHome home = (IRaAdminSessionLocalHome) ServiceLocator.getInstance()
                        .getLocalHome(IRaAdminSessionLocalHome.COMP_NAME);
                raadminsession = home.create();
            } catch (Exception e) {
                throw new EJBException(e);
            }
        }
        return raadminsession;
    } //getRaAdminSession

    /**
     * Gets connection to certificate store session bean
     *
     * @return ICertificateStoreSessionLocal
     */
    private ICertificateStoreSessionLocal getCertificateStoreSession() {
        if (certificatestoresession == null) {
            try {
                ICertificateStoreSessionLocalHome home = (ICertificateStoreSessionLocalHome) ServiceLocator.getInstance()
                        .getLocalHome(ICertificateStoreSessionLocalHome.COMP_NAME);
                certificatestoresession = home.create();
            } catch (Exception e) {
                throw new EJBException(e);
            }
        }
        return certificatestoresession;
    } //getCertificateStoreSession
    
    /**
     * Gets connection to user data source session bean
     *
     * @return IUserDataSourceSessionLocal
     */
    private IUserDataSourceSessionLocal getUserDataSourceSession() {
        if (userdatasourcesession == null) {
            try {
                IUserDataSourceSessionLocalHome home = (IUserDataSourceSessionLocalHome) ServiceLocator.getInstance()
                        .getLocalHome(IUserDataSourceSessionLocalHome.COMP_NAME);
                userdatasourcesession = home.create();
            } catch (Exception e) {
                throw new EJBException(e);
            }
        }
        return userdatasourcesession;
    } //getUserDataSourceSession


    /**
     * Gets connection to ca admin session bean
     *
     * @return ICAAdminSessionLocal
     */
    private ICAAdminSessionLocal getCAAdminSession() {
        if (caadminsession == null) {
            try {
                ICAAdminSessionLocalHome home = (ICAAdminSessionLocalHome) ServiceLocator.getInstance()
                        .getLocalHome(ICAAdminSessionLocalHome.COMP_NAME);
                caadminsession = home.create();
            } catch (Exception e) {
                throw new EJBException(e);
            }
        }
        return caadminsession;
    }

    // Methods used with AdminGroupData Entity Beans

    /**
     * Method to initialize authorization bean, must be called directly after creation of bean. Should only be called once.
     *
     * @ejb.interface-method view-type="both"
     */
    public void initialize(Admin admin, int caid) throws AdminGroupExistsException {
    	if (log.isDebugEnabled()) {
    		log.debug(">initialize, caid: "+caid);
    	}
        // Check if admingroup table is empty, if so insert default superuser
        // and create "special edit accessrules count group"
        try {
            Collection result = admingrouphome.findAll();
            if (result.size() == 0) {
                // Authorization table is empty, fill with default and special admingroups.
                String admingroupname = "Temporary Super Administrator Group";
                addAdminGroup(admin, admingroupname, caid);
                ArrayList adminentities = new ArrayList();
                adminentities.add(new AdminEntity(AdminEntity.WITH_COMMONNAME, AdminEntity.TYPE_EQUALCASEINS, "SuperAdmin", caid));
                addAdminEntities(admin, admingroupname, caid, adminentities);
                ArrayList accessrules = new ArrayList();
                accessrules.add(new AccessRule("/super_administrator", AccessRule.RULE_ACCEPT, false));
                addAccessRules(admin, admingroupname, caid, accessrules);
            }
        } catch (FinderException e) {
        	debug("initialize: FinderEx, findAll failed.");
        }
        // Add Special Admin Group
        // Special admin group is a group that is not authenticated with client certificate, such as batch tool etc
        try {
            admingrouphome.findByGroupNameAndCAId(DEFAULTGROUPNAME, LogConstants.INTERNALCAID);
        } catch (FinderException e) {
        	debug("initialize: FinderEx, add default group.");
            // Add Default Special Admin Group
            try {
                AdminGroupDataLocal agdl = admingrouphome.create(new Integer(findFreeAdminGroupId()), DEFAULTGROUPNAME, LogConstants.INTERNALCAID);

                ArrayList adminentities = new ArrayList();
                adminentities.add(new AdminEntity(AdminEntity.SPECIALADMIN_BATCHCOMMANDLINEADMIN));
                adminentities.add(new AdminEntity(AdminEntity.SPECIALADMIN_CACOMMANDLINEADMIN));
                adminentities.add(new AdminEntity(AdminEntity.SPECIALADMIN_RAADMIN));
                adminentities.add(new AdminEntity(AdminEntity.SPECIALADMIN_INTERNALUSER));
                agdl.addAdminEntities(adminentities);

                ArrayList accessrules = new ArrayList();
                accessrules.add(new AccessRule("/administrator", AccessRule.RULE_ACCEPT, true));
                accessrules.add(new AccessRule("/super_administrator", AccessRule.RULE_ACCEPT, false));

                accessrules.add(new AccessRule("/ca_functionality", AccessRule.RULE_ACCEPT, true));
                accessrules.add(new AccessRule("/ra_functionality", AccessRule.RULE_ACCEPT, true));
                accessrules.add(new AccessRule("/log_functionality", AccessRule.RULE_ACCEPT, true));
                accessrules.add(new AccessRule("/system_functionality", AccessRule.RULE_ACCEPT, true));
                accessrules.add(new AccessRule("/hardtoken_functionality", AccessRule.RULE_ACCEPT, true));
                accessrules.add(new AccessRule("/ca", AccessRule.RULE_ACCEPT, true));
                accessrules.add(new AccessRule("/endentityprofilesrules", AccessRule.RULE_ACCEPT, true));

                agdl.addAccessRules(accessrules);

                signalForAuthorizationTreeUpdate();
            } catch (CreateException ce) {
            	error("initialize continues after Exception: ", ce);
            }
        }
        // Add Public Web Group
        try {
            AdminGroupDataLocal agl = admingrouphome.findByGroupNameAndCAId(PUBLICWEBGROUPNAME, caid);
            removeAndAddDefaultPublicWebGroupRules(agl);
        } catch (FinderException e) {
        	debug("initialize: FinderEx, can't find public web group for caid "+caid);
        	debug("initialize: FinderEx, create public web group for caid "+caid);
        	try {
                AdminGroupDataLocal agdl = admingrouphome.create(new Integer(findFreeAdminGroupId()), PUBLICWEBGROUPNAME, caid);
                addDefaultPublicWebGroupRules(agdl);
                signalForAuthorizationTreeUpdate();
            } catch (CreateException ce) {
            	error("initialize continues after Exception: ", ce);
            }
        }

    	if (log.isDebugEnabled()) {
    		log.debug("<initialize, caid: "+caid);
    	}
    }


	private void addDefaultPublicWebGroupRules(AdminGroupDataLocal agdl) {
    	debug("create public web group for caid "+agdl.getCaId());
		ArrayList adminentities = new ArrayList();
		adminentities.add(new AdminEntity(AdminEntity.SPECIALADMIN_PUBLICWEBUSER));
		agdl.addAdminEntities(adminentities);

		ArrayList accessrules = new ArrayList();
		accessrules.add(new AccessRule("/public_web_user", AccessRule.RULE_ACCEPT, false));

		accessrules.add(new AccessRule("/ca_functionality/basic_functions", AccessRule.RULE_ACCEPT, false));
		accessrules.add(new AccessRule("/ca_functionality/view_certificate", AccessRule.RULE_ACCEPT, false));
		accessrules.add(new AccessRule("/ca_functionality/create_certificate", AccessRule.RULE_ACCEPT, false));
		accessrules.add(new AccessRule("/ca_functionality/store_certificate", AccessRule.RULE_ACCEPT, false));
		accessrules.add(new AccessRule("/ra_functionality/view_end_entity", AccessRule.RULE_ACCEPT, false));
		accessrules.add(new AccessRule("/ca", AccessRule.RULE_ACCEPT, true));
		accessrules.add(new AccessRule("/endentityprofilesrules", AccessRule.RULE_ACCEPT, true));

		agdl.addAccessRules(accessrules);
	}


    /**
     */
    private void removeAndAddDefaultPublicWebGroupRules(AdminGroupDataLocal agl) {
    	if (log.isDebugEnabled()) {
    		debug("Removing old and adding new accessrules and admin entitites to admin group "+agl.getAdminGroupName()+" for caid "+agl.getCaId());
    	}
        removeEntitiesAndRulesFromGroup(agl);
        addDefaultPublicWebGroupRules(agl);
        signalForAuthorizationTreeUpdate();
    }

    /**
     * Method to check if a user is authorized to a certain resource.
     *
     * @param admin    the administrator about to be authorized, see org.ejbca.core.model.log.Admin class.
     * @param resource the resource to check authorization for.
     * @ejb.interface-method view-type="both"
     * @ejb.transaction type="Supports"
     */
    public boolean isAuthorized(Admin admin, String resource) throws AuthorizationDeniedException {
        if (updateNeccessary())
            updateAuthorizationTree();
        
        return getAuthorizer().isAuthorized(admin, resource);
    }

    /**
     * Method to check if a user is authorized to a certain resource without performing any logging.
     *
     * @param admin    the administrator about to be authorized, see org.ejbca.core.model.log.Admin class.
     * @param resource the resource to check authorization for.
     * @ejb.interface-method view-type="both"
     * @ejb.transaction type="Supports"
     */
    public boolean isAuthorizedNoLog(Admin admin, String resource) throws AuthorizationDeniedException {
        if (updateNeccessary())
            updateAuthorizationTree();
        return getAuthorizer().isAuthorizedNoLog(admin, resource);
    }

    /**
     * Method to check if a group is authorized to a resource.
     *
     * @ejb.interface-method view-type="both"
     * @ejb.transaction type="Supports"
     */
    public boolean isGroupAuthorized(Admin admin, int admingrouppk, String resource) throws AuthorizationDeniedException {
        if (updateNeccessary())
            updateAuthorizationTree();
        return getAuthorizer().isGroupAuthorized(admin, admingrouppk, resource);
    }

    /**
     * Method to check if a group is authorized to a resource without any logging.
     *
     * @ejb.interface-method view-type="both"
     * @ejb.transaction type="Supports"
     */
    public boolean isGroupAuthorizedNoLog(Admin admin, int admingrouppk, String resource) throws AuthorizationDeniedException {
        if (updateNeccessary())
            updateAuthorizationTree();
        return getAuthorizer().isGroupAuthorizedNoLog(admin, admingrouppk, resource);
    }

    /**
     * Method to check if an administrator exists in the specified admingroup.
     *
     * @ejb.interface-method view-type="both"
     * @ejb.transaction type="Supports"
     */
    public boolean existsAdministratorInGroup(Admin admin, int admingrouppk) {
        boolean returnval = false;
        if (updateNeccessary())
            updateAuthorizationTree();

        try {
            AdminGroupDataLocal agdl = admingrouphome.findByPrimaryKey(new Integer(admingrouppk));
            Iterator adminentitites = agdl.getAdminGroup().getAdminEntities().iterator();
            while (adminentitites.hasNext()) {
                AdminEntity ae = (AdminEntity) adminentitites.next();
                returnval = returnval || ae.match(admin.getAdminInformation());
            }
        } catch (FinderException fe) {
        }

        return returnval;
    }


    /**
     * Method to validate and check revokation status of a users certificate.
     *
     * @param certificate the users X509Certificate.
     * @ejb.interface-method view-type="both"
     * @ejb.transaction type="Supports"
     */

    public void authenticate(X509Certificate certificate) throws AuthenticationFailedException {
        getAuthorizer().authenticate(certificate);
    }

    /**
     * Method to add an admingroup.
     *
     * @param admingroupname name of new admingroup, have to be unique.
     * @throws AdminGroupExistsException if admingroup already exists.
     * @ejb.interface-method view-type="both"
     */
    public void addAdminGroup(Admin admin, String admingroupname, int caid) throws AdminGroupExistsException {
        if (!(admingroupname.equals(DEFAULTGROUPNAME) && caid == LogConstants.INTERNALCAID)) {

            boolean success = true;
            try {
                admingrouphome.findByGroupNameAndCAId(admingroupname, caid);
                success = false;
            } catch (FinderException e) {
            }
            if (success) {
                try {
                    admingrouphome.create(new Integer(findFreeAdminGroupId()), admingroupname, caid);
                    success = true;
                } catch (CreateException e) {
            		String msg = intres.getLocalizedMessage("authorization.erroraddadmingroup", admingroupname);            	
                    error(msg, e);
                    success = false;
                }
            }


            if (success) {
        		String msg = intres.getLocalizedMessage("authorization.admingroupadded", admingroupname);            	
        		getLogSession().log(admin, caid, LogConstants.MODULE_RA, new java.util.Date(), null, null, LogConstants.EVENT_INFO_EDITEDADMINISTRATORPRIVILEGES, msg);
            } else {
        		String msg = intres.getLocalizedMessage("authorization.erroraddadmingroup", admingroupname);            	
        		getLogSession().log(admin, caid, LogConstants.MODULE_RA, new java.util.Date(), null, null, LogConstants.EVENT_ERROR_EDITEDADMINISTRATORPRIVILEGES, msg);
                throw new AdminGroupExistsException();
            }
        }
    } // addAdminGroup

    /**
     * Method to remove a admingroup.
     *
     * @ejb.interface-method view-type="both"
     */
    public void removeAdminGroup(Admin admin, String admingroupname, int caid) {
    	if (log.isDebugEnabled()) {
    		debug("Removing admin group "+admingroupname+" for caid "+caid);
    	}
        if (!(admingroupname.equals(DEFAULTGROUPNAME) && caid == LogConstants.INTERNALCAID)) {
            try {
                AdminGroupDataLocal agl = admingrouphome.findByGroupNameAndCAId(admingroupname, caid);
                removeEntitiesAndRulesFromGroup(agl);

                agl.remove();
                signalForAuthorizationTreeUpdate();

        		String msg = intres.getLocalizedMessage("authorization.admingroupremoved", admingroupname);            	
        		getLogSession().log(admin, caid, LogConstants.MODULE_RA, new java.util.Date(), null, null, LogConstants.EVENT_INFO_EDITEDADMINISTRATORPRIVILEGES, msg);
            } catch (Exception e) {
        		String msg = intres.getLocalizedMessage("authorization.errorremoveadmingroup", admingroupname);            	
                error(msg, e);
                getLogSession().log(admin, caid, LogConstants.MODULE_RA, new java.util.Date(), null, null, LogConstants.EVENT_ERROR_EDITEDADMINISTRATORPRIVILEGES, msg);
            }
        }
    } // removeAdminGroup


	private void removeEntitiesAndRulesFromGroup(AdminGroupDataLocal agl) {
    	debug("removing entities and rules for caid "+agl.getCaId());
		// Remove groups user entities.
		agl.removeAdminEntities(agl.getAdminEntityObjects());

		// Remove groups accessrules.
		Iterator iter = agl.getAccessRuleObjects().iterator();
		ArrayList remove = new ArrayList();
		while (iter.hasNext()) {
		    remove.add(((AccessRule) iter.next()).getAccessRule());
		}
		agl.removeAccessRules(remove);
	}

    /**
     * Metod to rename a admingroup
     *
     * @throws AdminGroupExistsException if admingroup already exists.
     * @ejb.interface-method view-type="both"
     */
    public void renameAdminGroup(Admin admin, String oldname, int caid, String newname) throws AdminGroupExistsException {
        if (!(oldname.equals(DEFAULTGROUPNAME) && caid == LogConstants.INTERNALCAID)) {
            boolean success = false;
            AdminGroupDataLocal agl = null;
            try {
                agl = admingrouphome.findByGroupNameAndCAId(newname, caid);
                throw new AdminGroupExistsException();
            } catch (FinderException e) {
                success = true;
            }
            if (success) {
                try {
                    agl = admingrouphome.findByGroupNameAndCAId(oldname, caid);
                    agl.setAdminGroupName(newname);
                    agl.setCaId(caid);
                    signalForAuthorizationTreeUpdate();
                } catch (Exception e) {
                    error("Can't rename admingroup: ", e);
                    success = false;
                }
            }

            if (success) {
        		String msg = intres.getLocalizedMessage("authorization.admingrouprenamed", oldname, newname);            	
        		getLogSession().log(admin, caid, LogConstants.MODULE_RA, new java.util.Date(), null, null, LogConstants.EVENT_INFO_EDITEDADMINISTRATORPRIVILEGES, msg);
            } else {
        		String msg = intres.getLocalizedMessage("authorization.errorrenameadmingroup", oldname, newname);            	
        		getLogSession().log(admin, caid, LogConstants.MODULE_RA, new java.util.Date(), null, null, LogConstants.EVENT_ERROR_EDITEDADMINISTRATORPRIVILEGES, msg);            	
            }
        }
    } // renameAdminGroup


    /**
     * Method to get a reference to a admingroup.
     *
     * @ejb.interface-method view-type="both"
     * @ejb.transaction type="Supports"
     */

    public AdminGroup getAdminGroup(Admin admin, String admingroupname, int caid) {
        AdminGroup returnval = null;
        try {
            returnval = (admingrouphome.findByGroupNameAndCAId(admingroupname, caid)).getAdminGroup();
        } catch (Exception e) {
            error("Can't get admingroup: ", e);
        }
        return returnval;
    } // getAdminGroup


    /**
     * Returns the total number of admingroups
     */
    private Collection getAdminGroups() {
        ArrayList returnval = new ArrayList();
        try {
            Iterator iter = admingrouphome.findAll().iterator();
            while (iter.hasNext())
                returnval.add(((AdminGroupDataLocal) iter.next()).getAdminGroup());
        } catch (FinderException e) {
        }

        return returnval;
    } // getAdminGroups


    /**
     * Returns a Collection of AdminGroup the administrator is authorized to.
     * <p/>
     * SuperAdmin is autorized to all groups
     * Other admins are only authorized to the groups cointaining a subset of authorized CA that the admin
     * himself is authorized to.
     * <p/>
     * The AdminGroup objects only contains only name and caid and no accessdata
     *
     * @ejb.interface-method view-type="both"
     * @ejb.transaction type="Supports"
     */

    public Collection getAuthorizedAdminGroupNames(Admin admin) {
        ArrayList returnval = new ArrayList();


        boolean issuperadmin = false;
        try {
            issuperadmin = this.isAuthorizedNoLog(admin, AvailableAccessRules.ROLE_SUPERADMINISTRATOR);
        } catch (AuthorizationDeniedException e1) {
        }
        HashSet authorizedcaids = new HashSet();
        HashSet allcaids = new HashSet();
        if (!issuperadmin) {
            authorizedcaids.addAll(getAuthorizer().getAuthorizedCAIds(admin));
            allcaids.addAll(getCAAdminSession().getAvailableCAs(admin));
        }

        try {
            Collection result = admingrouphome.findAll();
            Iterator i = result.iterator();

            while (i.hasNext()) {
                AdminGroupDataLocal agdl = (AdminGroupDataLocal) i.next();

                boolean allauthorized = false;
                boolean carecursive = false;
                boolean superadmingroup = false;
                boolean authtogroup = false;

                ArrayList groupcaids = new ArrayList();
                if (!issuperadmin) {
                    // Is admin authorized to group caid.
                    if (authorizedcaids.contains(new Integer(agdl.getCaId()))) {
                        authtogroup = true;
                        // check access rules
                        Iterator iter = agdl.getAccessRuleObjects().iterator();
                        while (iter.hasNext()) {
                            AccessRule accessrule = ((AccessRule) iter.next());
                            String rule = accessrule.getAccessRule();
                            if (rule.equals(AvailableAccessRules.ROLE_SUPERADMINISTRATOR) && accessrule.getRule() == AccessRule.RULE_ACCEPT) {
                                superadmingroup = true;
                                break;
                            }
                            if (rule.equals(AvailableAccessRules.CABASE)) {
                                if (accessrule.getRule() == AccessRule.RULE_ACCEPT && accessrule.isRecursive()) {
                                    if (authorizedcaids.containsAll(allcaids)) {
                                        carecursive = true;
                                    }
                                }
                            } else {
                                if (rule.startsWith(AvailableAccessRules.CAPREFIX) && accessrule.getRule() == AccessRule.RULE_ACCEPT) {
                                    groupcaids.add(new Integer(rule.substring(AvailableAccessRules.CAPREFIX.length())));
                                }
                            }
                        }
                    }
                }

                allauthorized = authorizedcaids.containsAll(groupcaids);

                if (issuperadmin || ((allauthorized || carecursive) && authtogroup && !superadmingroup)) {
                    if (!agdl.getAdminGroupName().equals(PUBLICWEBGROUPNAME) && !(agdl.getAdminGroupName().equals(DEFAULTGROUPNAME) && agdl.getCaId() == LogConstants.INTERNALCAID))
                        returnval.add(agdl.getAdminGroupNames());
                }
            }
        } catch (FinderException e) {
        }
        return returnval;
    } // getAuthorizedAdminGroupNames

    /**
     * Adds a Collection of AccessRule to an an admin group.
     *
     * @ejb.interface-method view-type="both"
     */
    public void addAccessRules(Admin admin, String admingroupname, int caid, Collection accessrules) {
        if (!(admingroupname.equals(DEFAULTGROUPNAME) && caid == LogConstants.INTERNALCAID)) {
            try {
                (admingrouphome.findByGroupNameAndCAId(admingroupname, caid)).addAccessRules(accessrules);
                signalForAuthorizationTreeUpdate();               
        		String msg = intres.getLocalizedMessage("authorization.accessrulesadded", admingroupname);            	
        		getLogSession().log(admin, caid, LogConstants.MODULE_RA, new java.util.Date(), null, null, LogConstants.EVENT_INFO_EDITEDADMINISTRATORPRIVILEGES, msg);
            } catch (Exception e) {
        		String msg = intres.getLocalizedMessage("authorization.erroraddaccessrules", admingroupname);            	
                error(msg, e);
                getLogSession().log(admin, caid, LogConstants.MODULE_RA, new java.util.Date(), null, null, LogConstants.EVENT_ERROR_EDITEDADMINISTRATORPRIVILEGES, msg);
            }
        }
    } // addAccessRules


    /**
     * Removes a Collection of (String) containing accessrules to remove from admin group.
     *
     * @ejb.interface-method view-type="both"
     */
    public void removeAccessRules(Admin admin, String admingroupname, int caid, Collection accessrules) {
        if (!(admingroupname.equals(DEFAULTGROUPNAME) && caid == LogConstants.INTERNALCAID)) {
            try {
                (admingrouphome.findByGroupNameAndCAId(admingroupname, caid)).removeAccessRules(accessrules);
                signalForAuthorizationTreeUpdate();
        		String msg = intres.getLocalizedMessage("authorization.accessrulesremoved", admingroupname);            	
        		getLogSession().log(admin, caid, LogConstants.MODULE_RA, new java.util.Date(), null, null, LogConstants.EVENT_INFO_EDITEDADMINISTRATORPRIVILEGES, msg);
            } catch (Exception e) {
        		String msg = intres.getLocalizedMessage("authorization.errorremoveaccessrules", admingroupname);            	
            	error(msg, e);
            	getLogSession().log(admin, caid, LogConstants.MODULE_RA, new java.util.Date(), null, null, LogConstants.EVENT_INFO_EDITEDADMINISTRATORPRIVILEGES, msg);
            }
        }
    } // removeAccessRules

    /**
     * Replaces a groups accessrules with a new set of rules
     *
     * @ejb.interface-method view-type="both"
     */
    public void replaceAccessRules(Admin admin, String admingroupname, int caid, Collection accessrules) {
        if (!(admingroupname.equals(DEFAULTGROUPNAME) && caid == LogConstants.INTERNALCAID)) {
            try {
                AdminGroupDataLocal agdl = admingrouphome.findByGroupNameAndCAId(admingroupname, caid);
                Collection currentrules = agdl.getAdminGroup().getAccessRules();
                ArrayList removerules = new ArrayList();
                Iterator iter = currentrules.iterator();
                while (iter.hasNext()) {
                    removerules.add(((AccessRule) iter.next()).getAccessRule());
                }
                agdl.removeAccessRules(removerules);
                agdl.addAccessRules(accessrules);
                signalForAuthorizationTreeUpdate();
        		String msg = intres.getLocalizedMessage("authorization.accessrulesreplaced", admingroupname);            	
        		getLogSession().log(admin, caid, LogConstants.MODULE_RA, new java.util.Date(), null, null, LogConstants.EVENT_INFO_EDITEDADMINISTRATORPRIVILEGES, msg);
            } catch (Exception e) {
        		String msg = intres.getLocalizedMessage("authorization.errorreplaceaccessrules", admingroupname);            	
            	error(msg, e);
            	getLogSession().log(admin, caid, LogConstants.MODULE_RA, new java.util.Date(), null, null, LogConstants.EVENT_INFO_EDITEDADMINISTRATORPRIVILEGES, msg);
            }
        }
    } // replaceAccessRules

    /**
     * Adds a Collection of AdminEnity to the admingroup. Changes their values if they already exists.
     *
     * @ejb.interface-method view-type="both"
     */

    public void addAdminEntities(Admin admin, String admingroupname, int caid, Collection adminentities) {
        if (!(admingroupname.equals(DEFAULTGROUPNAME) && caid == LogConstants.INTERNALCAID)) {
            try {
                (admingrouphome.findByGroupNameAndCAId(admingroupname, caid)).addAdminEntities(adminentities);
                signalForAuthorizationTreeUpdate();
        		String msg = intres.getLocalizedMessage("authorization.adminadded", admingroupname);            	
        		getLogSession().log(admin, caid, LogConstants.MODULE_RA, new java.util.Date(), null, null, LogConstants.EVENT_INFO_EDITEDADMINISTRATORPRIVILEGES, msg);
            } catch (Exception e) {
        		String msg = intres.getLocalizedMessage("authorization.erroraddadmin", admingroupname);            	
            	error(msg, e);
            	getLogSession().log(admin, caid, LogConstants.MODULE_RA, new java.util.Date(), null, null, LogConstants.EVENT_ERROR_EDITEDADMINISTRATORPRIVILEGES, msg);
            }
        }
    } // addAdminEntity


    /**
     * Removes a Collection of AdminEntity from the administrator group.
     *
     * @ejb.interface-method view-type="both"
     */
    public void removeAdminEntities(Admin admin, String admingroupname, int caid, Collection adminentities) {
        if (!(admingroupname.equals(DEFAULTGROUPNAME) && caid == LogConstants.INTERNALCAID)) {
            try {
                (admingrouphome.findByGroupNameAndCAId(admingroupname, caid)).removeAdminEntities(adminentities);
                signalForAuthorizationTreeUpdate();
        		String msg = intres.getLocalizedMessage("authorization.adminremoved", admingroupname);            	
        		getLogSession().log(admin, caid, LogConstants.MODULE_RA, new java.util.Date(), null, null, LogConstants.EVENT_INFO_EDITEDADMINISTRATORPRIVILEGES, msg);
            } catch (Exception e) {
        		String msg = intres.getLocalizedMessage("authorization.errorremoveadmin", admingroupname);            	
            	error(msg, e);
            	getLogSession().log(admin, caid, LogConstants.MODULE_RA, new java.util.Date(), null, null, LogConstants.EVENT_ERROR_EDITEDADMINISTRATORPRIVILEGES, msg);
            }
        }
    } // removeAdminEntity


    /**
     * Method used to collect an administrators available access rules based on which rule
     * he himself is authorized to.
     *
     * @param admin is the administrator calling the method.
     * @return a Collection of String containing available accessrules.
     * @ejb.interface-method view-type="both"
     * @ejb.transaction type="Supports"
     */

    public Collection getAuthorizedAvailableAccessRules(Admin admin) {
        AvailableAccessRules aar = null;
        try {
            aar = new AvailableAccessRules(admin, getAuthorizer(), getRaAdminSession(),getUserDataSourceSession(), customaccessrules);
        } catch (Exception e) {
            throw new EJBException(e);
        }

        return aar.getAvailableAccessRules(admin);
    }

    /**
     * Method used to return an Collection of Integers indicating which CAids a administrator
     * is authorized to access.
     *
     * @ejb.interface-method view-type="both"
     * @ejb.transaction type="Supports"
     */
    public Collection getAuthorizedCAIds(Admin admin) {
        return getAuthorizer().getAuthorizedCAIds(admin);
    }


    /**
     * Method used to return an Collection of Integers indicating which end entity profiles
     * the administrator is authorized to view.
     *
     * @param admin        the administrator
     * @param rapriviledge should be one of the end entity profile authorization constans defined in AvailableAccessRules.
     * @ejb.interface-method view-type="both"
     * @ejb.transaction type="Supports"
     */
    public Collection getAuthorizedEndEntityProfileIds(Admin admin, String rapriviledge) {
        return getAuthorizer().getAuthorizedEndEntityProfileIds(admin, rapriviledge);
    }

    /**
     * Method to check if an end entity profile exists in any end entity profile rules. Used to avoid desyncronization of profilerules.
     *
     * @param profileid the profile id to search for.
     * @return true if profile exists in any of the accessrules.
     * @ejb.interface-method view-type="both"
     * @ejb.transaction type="Supports"
     */
    public boolean existsEndEntityProfileInRules(Admin admin, int profileid) {
        debug(">existsEndEntityProfileInRules()");
        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        int count = 1; // return true as default.

        String whereclause = "accessRule  LIKE '" + AvailableAccessRules.ENDENTITYPROFILEPREFIX + profileid + "%'";

        try {
            // Construct SQL query.
            con = JDBCUtil.getDBConnection(JNDINames.DATASOURCE);
            ps = con.prepareStatement("select COUNT(*) from AccessRulesData where " + whereclause);
            // Execute query.
            rs = ps.executeQuery();
            // Assemble result.
            if (rs.next()) {
                count = rs.getInt(1);
            }
            debug("<existsEndEntityProfileInRules()");
            return count > 0;

        } catch (Exception e) {
            throw new EJBException(e);
        } finally {
            JDBCUtil.close(con, ps, rs);
        }
    } // existsEndEntityProfileInRules

    /**
     * Method to check if a ca exists in any ca specific rules. Used to avoid desyncronization of CA rules when ca is removed
     *
     * @param caid the ca id to search for.
     * @return true if ca exists in any of the accessrules.
     * @ejb.interface-method view-type="both"
     * @ejb.transaction type="Supports"
     */

    public boolean existsCAInRules(Admin admin, int caid) {
        return existsCAInAdminGroups(caid) && existsCAInAccessRules(caid);
    } // existsCAInRules
    
    /**
     * Method  to force an update of the autorization rules without any wait.
     *
     * @ejb.interface-method view-type="both"
     * @ejb.transaction type="Supports"
     */

    public void forceRuleUpdate(Admin admin) {
        signalForAuthorizationTreeUpdate();
        updateAuthorizationTree();
    } // existsCAInRules


    /**
     * Help function to existsCAInRules, checks if caid axists among admingroups.
     */
    private boolean existsCAInAdminGroups(int caid) {
        debug(">existsCAInAdminGroups()");
        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        int count = 1; // return true as default.
        try {
            // Construct SQL query.
            con = JDBCUtil.getDBConnection(JNDINames.DATASOURCE);
            ps = con.prepareStatement("select COUNT(*) from AdminGroupData where cAId = ?");
			ps.setInt(1, caid);
            // Execute query.
            rs = ps.executeQuery();
            // Assemble result.
            if (rs.next()) {
                count = rs.getInt(1);
            }
            boolean exists = count > 0;
            debug("<existsCAInAdminGroups(): "+exists);
            return exists;
        } catch (Exception e) {
            throw new EJBException(e);
        } finally {
            JDBCUtil.close(con, ps, rs);
        }
    }

    /**
     * Help function to existsCAInRules, checks if caid axists among accessrules.
     */
    private boolean existsCAInAccessRules(int caid) {
        debug(">existsCAInAccessRules()");
        Connection con = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        int count = 1; // return true as default.

        String whereclause = "accessRule  LIKE '" + AvailableAccessRules.CABASE + "/" + caid + "%'";

        try {
            // Construct SQL query.
            con = JDBCUtil.getDBConnection(JNDINames.DATASOURCE);
            ps = con.prepareStatement("select COUNT(*) from AccessRulesData where " + whereclause);
            // Execute query.
            rs = ps.executeQuery();
            // Assemble result.
            if (rs.next()) {
                count = rs.getInt(1);
            }
            boolean exists = count > 0;
            debug("<existsCAInAccessRules(): "+exists);
            return exists;
        } catch (Exception e) {
            throw new EJBException(e);
        } finally {
            JDBCUtil.close(con, ps, rs);
        }
    } // existsCAInAccessRules

    /**
     * Returns a reference to the AuthorizationTreeUpdateDataBean
     */
    private AuthorizationTreeUpdateDataLocal getAuthorizationTreeUpdateData() {
        AuthorizationTreeUpdateDataLocal atu = null;
        try {
            atu = authorizationtreeupdatehome.findByPrimaryKey(AuthorizationTreeUpdateDataBean.AUTHORIZATIONTREEUPDATEDATA);
        } catch (FinderException e) {
            try {
                atu = authorizationtreeupdatehome.create();
            } catch (CreateException ce) {
        		String msg = intres.getLocalizedMessage("authorization.errorcreateauthtree");            	
                error(msg, ce);
                throw new EJBException(ce);
            }
        }
        return atu;
    }


    /**
     * Method used check if a reconstruction of authorization tree is needed in the
     * authorization beans.
     *
     * @return true if update is needed.
     */

    private boolean updateNeccessary() {
        return getAuthorizationTreeUpdateData().updateNeccessary(this.authorizationtreeupdate) && lastupdatetime < ((new java.util.Date()).getTime() - MIN_TIME_BETWEEN_UPDATES);
    } // updateNeccessary

    /**
     * method updating authorization tree.
     */
    private void updateAuthorizationTree() {
        getAuthorizer().buildAccessTree(getAdminGroups());
        this.authorizationtreeupdate = getAuthorizationTreeUpdateData().getAuthorizationTreeUpdateNumber();
        this.lastupdatetime = (new java.util.Date()).getTime();
    }

    /**
     * Method incrementing the authorizationtreeupdatenumber and thereby signaling
     * to other beans that they should reconstruct their accesstrees.
     */
    private void signalForAuthorizationTreeUpdate() {
    	if (log.isDebugEnabled()) {
    		log.debug(">signalForAuthorizationTreeUpdate");
    	}
        getAuthorizationTreeUpdateData().incrementAuthorizationTreeUpdateNumber();
    	if (log.isDebugEnabled()) {
    		log.debug("<signalForAuthorizationTreeUpdate");
    	}
    }

    private int findFreeAdminGroupId() {
        Random random = new Random();
        int id = random.nextInt();
        boolean foundfree = false;

        while (!foundfree) {
            try {
                this.admingrouphome.findByPrimaryKey(new Integer(id));
                id = random.nextInt();
            } catch (FinderException e) {
                foundfree = true;
            }
        }
        return id;
    } // findFreeCertificateProfileId

} // LocalAuthorizationSessionBean

