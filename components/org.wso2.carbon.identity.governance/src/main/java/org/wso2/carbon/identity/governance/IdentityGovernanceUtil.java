/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wso2.carbon.identity.governance;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.identity.application.common.model.FederatedAuthenticatorConfig;
import org.wso2.carbon.identity.application.common.model.IdentityProvider;
import org.wso2.carbon.identity.application.common.model.IdentityProviderProperty;
import org.wso2.carbon.identity.application.common.util.IdentityApplicationConstants;
import org.wso2.carbon.identity.event.EventMgtConstants;
import org.wso2.carbon.identity.governance.common.IdentityGovernanceConnector;
import org.wso2.carbon.identity.governance.internal.IdentityMgtServiceDataHolder;
import org.wso2.carbon.idp.mgt.IdentityProviderManagementException;
import org.wso2.carbon.idp.mgt.IdpManager;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;

public class IdentityGovernanceUtil {

    private static final Log log = LogFactory.getLog(IdentityGovernanceUtil.class);

    public static void saveConnectorDefaultProperties (IdentityGovernanceConnector identityGovernanceConnector,
                                                       String tenantDomain) throws IdentityGovernanceException{

        Properties connectorProperties = identityGovernanceConnector.getDefaultPropertyValues(tenantDomain);
        IdpManager identityProviderManager = IdentityMgtServiceDataHolder.getInstance().getIdpManager();

        try {
            Enumeration enuKeys = connectorProperties.keys();
            IdentityProvider residentIdp = identityProviderManager.getResidentIdP(tenantDomain);
            IdentityProviderProperty[] idpProperties = residentIdp.getIdpProperties();
            List<String> idpPropertyKeys = new ArrayList<>();
            List<IdentityProviderProperty> propertyList = new ArrayList<>();
            for (IdentityProviderProperty idpProperty : idpProperties) {
                String propertyName = idpProperty.getName();
                if ((identityGovernanceConnector.getName() + "." + EventMgtConstants.PropertyConfig.ALREADY_WRITTEN_PROPERTY_KEY).equals(propertyName)) {
                    if (log.isDebugEnabled()) {
                        log.debug("Identity management property saving skipped for tenant : " + tenantDomain);
                    }
                    return;
                }
                idpPropertyKeys.add(idpProperty.getName());
                propertyList.add(idpProperty);
            }
            while (enuKeys.hasMoreElements()) {
                String key = (String) enuKeys.nextElement();
                String value = connectorProperties.getProperty(key);
                IdentityProviderProperty property = new IdentityProviderProperty();
                property.setName(key);
                property.setValue(value);
                propertyList.add(property);
            }
            IdentityProviderProperty property = new IdentityProviderProperty();
            property.setName(identityGovernanceConnector.getName() + "." + EventMgtConstants.PropertyConfig
                    .ALREADY_WRITTEN_PROPERTY_KEY);
            property.setValue(EventMgtConstants.PropertyConfig.ALREADY_WRITTEN_PROPERTY_VALUE);
            propertyList.add(property);
            residentIdp.setIdpProperties(propertyList.toArray(new IdentityProviderProperty[propertyList.size()]));
            FederatedAuthenticatorConfig[] authenticatorConfigs = residentIdp.getFederatedAuthenticatorConfigs();
            List<FederatedAuthenticatorConfig> configsToSave = new ArrayList<>();
            for (FederatedAuthenticatorConfig authenticatorConfig : authenticatorConfigs) {
                if (IdentityApplicationConstants.Authenticator.PassiveSTS.NAME.equals(authenticatorConfig.getName
                        ()) || IdentityApplicationConstants.NAME.equals(authenticatorConfig.getName()) ||
                        IdentityApplicationConstants.Authenticator.SAML2SSO.NAME.equals(authenticatorConfig
                                .getName())) {
                    configsToSave.add(authenticatorConfig);
                }
            }
            residentIdp.setFederatedAuthenticatorConfigs(configsToSave.toArray(new
                    FederatedAuthenticatorConfig[configsToSave.size()]));

            identityProviderManager.updateResidentIdP(residentIdp, tenantDomain);
            if (log.isDebugEnabled()) {
                log.debug("New resident IDP properties for tenant : " + tenantDomain + " written to database");
            }

        } catch (IdentityProviderManagementException e) {
            log.error("Error while adding identity management properties to resident Idp.", e);
        }

    }

}
