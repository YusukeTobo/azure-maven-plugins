/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.tools.auth.core;

import com.azure.identity.CredentialUnavailableException;
import com.azure.identity.implementation.VisualStudioCacheAccessor;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.azure.tools.auth.exception.InvalidConfigurationException;
import com.microsoft.azure.tools.auth.model.VsCodeAccountProfile;
import com.sun.jna.Platform;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

class VsCodeProfileRetriever {
    private static final String PLATFORM_NOT_SUPPORTED_ERROR = "Platform could not be determined for VS Code credential authentication.";

    public static VsCodeAccountProfile[] getProfiles() {
        try {
            VisualStudioCacheAccessor accessor = new VisualStudioCacheAccessor();

            JsonNode userSettings = getUserSettings();
            if (Objects.isNull(userSettings)) {
                return null;
            }
            String cloud = getCloudFromUserSettings(userSettings);

            if (StringUtils.isNotEmpty(accessor.getCredentials("VS Code Azure", cloud))) {
                return getVsCodeAccountProfiles(userSettings, cloud);
            }
            return null;
        } catch (CredentialUnavailableException | InvalidConfigurationException exception) {
            return null;
        }
    }

    private static String getCloudFromUserSettings(JsonNode userSettings) {
        String cloud = "AzureCloud";
        if (userSettings != null && !userSettings.isNull()) {
            if (userSettings.has("azure.cloud")) {
                cloud = userSettings.get("azure.cloud").asText();
            }
        }
        return cloud;
    }

    private static VsCodeAccountProfile[] getVsCodeAccountProfiles(JsonNode userSettings, String cloud) throws InvalidConfigurationException {
        List<VsCodeAccountProfile> res = new ArrayList<>();
        if (userSettings.has("azure.resourceFilter")) {
            for (JsonNode filter : userSettings.get("azure.resourceFilter")) {
                String[] tenantAndSubsId = StringUtils.split(filter.asText(), "/");
                if (tenantAndSubsId.length == 2) {
                    VsCodeAccountProfile profile = new VsCodeAccountProfile(tenantAndSubsId[0], cloud, tenantAndSubsId[1]);
                    res.add(profile);
                } else {
                    throw new InvalidConfigurationException(
                            String.format("Invalid 'azure.resourceFilter' settings '%s' in VSCode settings.json.", filter.asText()));
                }
            }
        }
        return res.toArray(new VsCodeAccountProfile[0]);
    }

    /**
     * Code copied from https://github.com/Azure/azure-sdk-for-java/blob/master/sdk/identity/azure-identity/src/main/java/com/azure/identity/implementation/VisualStudioCacheAccessor.java#L32
     * , since it doesn't support profile, we need to get the setting for user
     * selected subscription
     */
    private static JsonNode getUserSettings() {
        JsonNode output = null;
        String homeDir = System.getProperty("user.home");
        String settingsPath = "";
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature(), true);
        mapper.configure(JsonReadFeature.ALLOW_JAVA_COMMENTS.mappedFeature(), true);
        try {
            if (Platform.isWindows()) {
                settingsPath = Paths.get(System.getenv("APPDATA"), "Code", "User", "settings.json")
                        .toString();
            } else if (Platform.isMac()) {
                settingsPath = Paths.get(homeDir, "Library",
                        "Application Support", "Code", "User", "settings.json").toString();
            } else if (Platform.isLinux()) {
                settingsPath = Paths.get(homeDir, ".config", "Code", "User", "settings.json")
                        .toString();
            } else {
                throw new CredentialUnavailableException(PLATFORM_NOT_SUPPORTED_ERROR);
            }
            File settingsFile = new File(settingsPath);
            output = mapper.readTree(settingsFile);
        } catch (Exception e) {
            return null;
        }
        return output;
    }
}
