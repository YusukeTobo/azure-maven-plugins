/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.maven.webapp;

import com.microsoft.azure.maven.model.DeploymentResource;
import com.microsoft.azure.maven.webapp.configuration.DeploymentSlotConfig;
import com.microsoft.azure.maven.webapp.task.DeployExternalResourcesTask;
import com.microsoft.azure.toolkit.lib.Azure;
import com.microsoft.azure.toolkit.lib.appservice.config.AppServiceConfig;
import com.microsoft.azure.toolkit.lib.appservice.config.RuntimeConfig;
import com.microsoft.azure.toolkit.lib.appservice.model.JavaVersion;
import com.microsoft.azure.toolkit.lib.appservice.model.OperatingSystem;
import com.microsoft.azure.toolkit.lib.appservice.model.PricingTier;
import com.microsoft.azure.toolkit.lib.appservice.model.WebAppArtifact;
import com.microsoft.azure.toolkit.lib.appservice.model.WebContainer;
import com.microsoft.azure.toolkit.lib.appservice.service.IWebApp;
import com.microsoft.azure.toolkit.lib.appservice.service.IWebAppBase;
import com.microsoft.azure.toolkit.lib.appservice.service.IWebAppDeploymentSlot;
import com.microsoft.azure.toolkit.lib.appservice.task.CreateOrUpdateWebAppTask;
import com.microsoft.azure.toolkit.lib.appservice.task.DeployWebAppTask;
import com.microsoft.azure.toolkit.lib.auth.AzureAccount;
import com.microsoft.azure.toolkit.lib.common.bundle.AzureString;
import com.microsoft.azure.toolkit.lib.common.exception.AzureExecutionException;
import com.microsoft.azure.toolkit.lib.common.exception.AzureToolkitRuntimeException;
import com.microsoft.azure.toolkit.lib.common.messager.AzureMessager;
import com.microsoft.azure.toolkit.lib.common.model.Region;
import org.apache.commons.lang3.StringUtils;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

import java.util.List;

/**
 * Deploy an Azure Web App, either Windows-based or Linux-based.
 */
@Mojo(name = "deploy", defaultPhase = LifecyclePhase.DEPLOY)
public class DeployMojo extends AbstractWebAppMojo {
    private static final String WEBAPP_NOT_EXIST_FOR_SLOT = "The Web App specified in pom.xml does not exist. " +
            "Please make sure the Web App name is correct.";
    private static final String CREATE_DEPLOYMENT_SLOT = "Creating deployment slot %s in web app %s";
    private static final String CREATE_DEPLOYMENT_SLOT_DONE = "Successfully created the Deployment Slot.";
    private static final String CREATE_NEW_DEPLOYMENT_SLOT = "createNewDeploymentSlot";

    @Override
    protected void doExecute() throws AzureExecutionException {
        validateConfiguration(message -> AzureMessager.getMessager().error(message.getMessage()), true);
        // initialize library client
        az = getOrCreateAzureAppServiceClient();
        final IWebAppBase<?> target = createOrUpdateResource();
        deployExternalResources(target, getConfigParser().getExternalArtifacts());
        deploy(target, getConfigParser().getArtifacts());
    }

    private IWebAppBase<?> createOrUpdateResource() throws AzureExecutionException {
        if (!isDeployToDeploymentSlot()) {
            final AppServiceConfig appServiceConfig = getConfigParser().getAppServiceConfig();
            return new CreateOrUpdateWebAppTask(appServiceConfig, buildDefaultConfig(appServiceConfig.subscriptionId())).execute();
        } else {
            // todo: New CreateOrUpdateDeploymentSlotTask
            final DeploymentSlotConfig config = getConfigParser().getDeploymentSlotConfig();
            final IWebAppDeploymentSlot slot = getDeploymentSlot(config);
            return slot.exists() ? updateDeploymentSlot(slot, config) : createDeploymentSlot(slot, config);
        }
    }

    private AppServiceConfig buildDefaultConfig(String subscriptionId) {
        final ComparableVersion javaVersionForProject = new ComparableVersion(System.getProperty("java.version"));
        // get java version according to project java version
        JavaVersion javaVersion = javaVersionForProject.compareTo(new ComparableVersion("9")) < 0 ? JavaVersion.JAVA_8 : JavaVersion.JAVA_11;
        RuntimeConfig runtimeConfig = new RuntimeConfig().os(OperatingSystem.LINUX).webContainer(StringUtils.equalsIgnoreCase(this.project.getPackaging(), "jar") ?
            WebContainer.JAVA_SE : (StringUtils.equalsIgnoreCase(this.project.getPackaging(), "ear") ? WebContainer.JBOSS_7 : WebContainer.TOMCAT_85))
            .javaVersion(javaVersion);
        AppServiceConfig appServiceConfig = new AppServiceConfig();
        appServiceConfig.region(getRegionOrDefault(Azure.az(AzureAccount.class).listRegions(subscriptionId)));
        appServiceConfig.runtime(runtimeConfig);
        appServiceConfig.pricingTier(runtimeConfig.webContainer() == WebContainer.JBOSS_7 ? PricingTier.PREMIUM_P1V3 : PricingTier.PREMIUM_P1V2);
        return appServiceConfig;
    }

    private static Region getRegionOrDefault(List<Region> regions) {
        if (regions.isEmpty()) {
            throw new AzureToolkitRuntimeException("No region is available.");
        }
        return regions.contains(Region.US_CENTRAL) ? Region.US_CENTRAL : regions.get(0);
    }

    private IWebAppDeploymentSlot getDeploymentSlot(final DeploymentSlotConfig config) throws AzureExecutionException {
        final IWebApp webApp = az.webapp(config.getResourceGroup(), config.getAppName());
        if (!webApp.exists()) {
            throw new AzureExecutionException(WEBAPP_NOT_EXIST_FOR_SLOT);
        }
        return webApp.deploymentSlot(config.getName());
    }

    private IWebAppDeploymentSlot createDeploymentSlot(final IWebAppDeploymentSlot slot, final DeploymentSlotConfig slotConfig) {
        AzureMessager.getMessager().info(AzureString.format(CREATE_DEPLOYMENT_SLOT, slotConfig.getName(), slotConfig.getAppName()));
        getTelemetryProxy().addDefaultProperty(CREATE_NEW_DEPLOYMENT_SLOT, String.valueOf(true));
        final IWebAppDeploymentSlot result = slot.create().withName(slotConfig.getName())
                .withConfigurationSource(slotConfig.getConfigurationSource())
                .withAppSettings(slotConfig.getAppSettings())
                .commit();
        AzureMessager.getMessager().info(CREATE_DEPLOYMENT_SLOT_DONE);
        return result;
    }

    // update existing slot is not supported in current version, will implement it later
    private IWebAppDeploymentSlot updateDeploymentSlot(final IWebAppDeploymentSlot slot, final DeploymentSlotConfig slotConfig) {
        return slot;
    }

    private void deploy(IWebAppBase<?> target, List<WebAppArtifact> artifacts) {
        new DeployWebAppTask(target, artifacts, isStopAppDuringDeployment()).execute();
    }

    private void deployExternalResources(final IWebAppBase<?> target, final List<DeploymentResource> resources) {
        new DeployExternalResourcesTask(target, resources).execute();
    }
}
