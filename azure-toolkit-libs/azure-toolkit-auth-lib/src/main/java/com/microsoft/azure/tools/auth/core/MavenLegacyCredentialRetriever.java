/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.azure.tools.auth.core;

import com.azure.core.credential.TokenCredential;
import com.microsoft.azure.AzureEnvironment;
import com.microsoft.azure.tools.auth.exception.LoginFailureException;
import com.microsoft.azure.tools.auth.maven.MavenAzureCredential;
import com.microsoft.azure.tools.auth.model.AuthMethod;
import com.microsoft.azure.tools.auth.model.AzureCredentialWrapper;
import com.microsoft.azure.tools.common.util.JsonUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;

/***
 * Handle the legacy login: `mvn com.microsoft.azure:azure-maven-plugin:0.2.0:login`
 */
public class MavenLegacyCredentialRetriever extends AbstractCredentialRetriever {
    private static final String AZURE_SECRET_FILE = "azure-secret.json";
    private static final String AZURE_CONFIG_DIR = "AZURE_CONFIG_DIR";
    private static final String AZURE_FOLDER = ".azure";
    private static final String USER_HOME = "user.home";

    public AzureCredentialWrapper retrieveInternal() throws LoginFailureException {
        throw new NotImplementedException("The support for legacy maven login.");
    }

    public static AzureCredentialWrapper getAzureMavenPluginCredential() throws LoginFailureException {
        if (existsAzureSecretFile()) {
            return new AzureCredentialWrapper(AuthMethod.AZURE_SECRET_FILE, getMavenAzureLoginCredentials(), AzureEnvironment.AZURE);
        }
        return null;
    }

    /**
     * Get azure token credential from $HOME/.azure/azure-secret.json(created by `mvn azure:login`)
     *
     * @return the azure token credential can be used in Azure SDK.
     * @throws LoginFailureException when there are login errors.
     */
    public static TokenCredential getMavenAzureLoginCredentials() throws LoginFailureException {
        final MavenAzureCredential credentials;
        File file = getAzureSecretFile();
        try {
            credentials = readAzureCredentials(file);
            return getMavenAzureLoginCredentials(credentials);
        } catch (IOException e) {
            throw new LoginFailureException(String.format("Cannot get azure credentials from file '%s'", file.getAbsolutePath()));
        }

    }

    /**
     * Get azure credential from MavenAzureCredential instance.
     *
     * @param credentials the azure credential
     * @return the azure token credential can be used in Azure SDK.
     */
    private static TokenCredential getMavenAzureLoginCredentials(MavenAzureCredential credentials) {
        throw new UnsupportedOperationException("This operation is not implemented yet.");
    }

    /***
     * Read the credential from a file.
     *
     * @param file the file to be read
     * @return the saved credential
     * @throws IOException if there is any IO error.
     */
    public static MavenAzureCredential readAzureCredentials(File file) throws IOException {
        if (file == null) {
            throw new IllegalArgumentException("Parameter 'file' cannot be null.");
        }
        final String jsonStr = FileUtils.readFileToString(file, "utf8");
        return JsonUtils.fromJson(jsonStr, MavenAzureCredential.class);
    }

    /**
     * Get the azure-secret.json file according to environment variable, the default location is $HOME/.azure/azure-secret.json
     */
    private static File getAzureSecretFile() {
        return new File(getAzureConfigFolder(), AZURE_SECRET_FILE);
    }

    /**
     * Get the azure config folder location, the default location is $HOME/.azure.
     */
    public static File getAzureConfigFolder() {
        return StringUtils.isNotBlank(System.getenv(AZURE_CONFIG_DIR)) ? new File(System.getenv(AZURE_CONFIG_DIR)) :
                Paths.get(System.getProperty(USER_HOME), AZURE_FOLDER).toFile();
    }

    /**
     * Check whether the azure-secret.json file exists and is not empty.
     */
    private static boolean existsAzureSecretFile() {
        final File azureSecretFile = getAzureSecretFile();
        return azureSecretFile.exists() && azureSecretFile.isFile() && azureSecretFile.length() > 0;
    }

}
