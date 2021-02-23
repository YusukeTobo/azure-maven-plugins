/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib.auth;

import com.azure.core.credential.TokenCredential;
import com.azure.core.management.AzureEnvironment;
import com.azure.core.management.profile.AzureProfile;
import com.azure.resourcemanager.AzureResourceManager;
import com.azure.resourcemanager.resources.models.Subscription;
import com.azure.resourcemanager.resources.models.Tenant;
import com.google.common.base.MoreObjects;
import com.microsoft.aad.adal4j.AuthenticationException;
import com.microsoft.azure.credentials.AzureTokenCredentials;
import com.microsoft.azure.toolkit.lib.auth.core.ICredentialProvider;
import com.microsoft.azure.toolkit.lib.auth.exception.LoginFailureException;
import com.microsoft.azure.toolkit.lib.auth.model.AccountEntity;
import com.microsoft.azure.toolkit.lib.auth.model.CachedTokenCredential;
import com.microsoft.azure.toolkit.lib.auth.model.SubscriptionEntity;
import com.microsoft.azure.toolkit.lib.auth.util.AzureIdentityCredentialTokenCredentialsConverter;
import com.microsoft.azure.toolkit.lib.common.utils.Utils;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class Account {
    @Getter
    @Setter
    private AccountEntity entity;

    @Setter
    @Getter
    private ICredentialProvider credentialBuilder;

    private Map<String, TokenCredential> tenantToCredential = new HashMap<>();

    public Account logout() {
        this.entity = null;
        this.credentialBuilder = null;
        this.tenantToCredential = new HashMap<>();
        return this;
    }

    public AzureEnvironment getEnvironment() {
        return entity.getEnvironment();
    }

    public void initialize() {
        Objects.requireNonNull(entity, "Cannot initialize from null account entity.");
        AzureEnvironment env = MoreObjects.firstNonNull(getEnvironment(), AzureEnvironment.AZURE);
        AzureProfile azureProfile = new AzureProfile(env);
        Set<String> validTenantIds = new HashSet<>();
        if (this.entity.getTenantIds() == null) {
            this.entity.setTenantIds(AzureResourceManager.authenticate(this.credentialBuilder.provideCredentialCommon()
                    , azureProfile).tenants().list().stream().map(Tenant::tenantId).collect(Collectors.toList()));

        }

        Map<String, SubscriptionEntity> subscriptionMap = new HashMap<>();
        this.entity.getTenantIds().forEach(tenantId -> {
            try {
                List<SubscriptionEntity> subscriptionsOnTenant =
                        AzureResourceManager.authenticate(this.credentialBuilder.provideCredentialForTenant(tenantId), azureProfile).subscriptions().list()
                                .mapPage(s -> this.toSubscriptionEntity(entity.getEnvironment(), tenantId, s)).stream().collect(Collectors.toList());

                for (SubscriptionEntity s : subscriptionsOnTenant) {
                    String key = StringUtils.lowerCase(s.getId());
                    if (subscriptionMap.putIfAbsent(key, s) != null) {
                        validTenantIds.add(tenantId);
                    }
                }
            } catch (Exception ex) {
                // ignore AuthenticationException since on some tenants, it doesn't allow list subscriptions
                if (!(ExceptionUtils.getRootCause(ex) instanceof AuthenticationException)) {
                    this.entity.setAuthenticated(false);
                    Account.this.entity.setError(ex);
                }

            }
        });

        // since some tenants doesn't have subscriptions, reduce the tenants
        this.entity.setTenantIds(new ArrayList<>(validTenantIds));
        this.entity.setSubscriptions(new ArrayList<>(subscriptionMap.values()));
        if (subscriptionMap.isEmpty()) {
            this.entity.setAuthenticated(false);
        }
    }

    public void selectSubscriptions(List<String> subscriptionIds) {
        // select subscriptions
        if (CollectionUtils.isNotEmpty(subscriptionIds) && CollectionUtils.isNotEmpty(this.entity.getSubscriptions())) {
            entity.getSubscriptions().stream().filter(s ->
                    Utils.containsIgnoreCase(subscriptionIds, s.getId())
            ).forEach(t -> {
                t.setSelected(true);
            });
        }
        entity.setSelectedSubscriptions(entity.getSubscriptions().stream().filter(SubscriptionEntity::isSelected).collect(Collectors.toList()));
    }

    private SubscriptionEntity toSubscriptionEntity(AzureEnvironment env, String tenantId, Subscription subscription) {
        final SubscriptionEntity subscriptionEntity = new SubscriptionEntity();
        subscriptionEntity.setId(subscription.subscriptionId());
        subscriptionEntity.setName(subscription.displayName());
        subscriptionEntity.setTenantId(tenantId);
        subscriptionEntity.setEnvironment(env);
        return subscriptionEntity;
    }

    public boolean isAuthenticated() {
        return this.entity != null && this.entity.isAuthenticated();
    }

    public List<SubscriptionEntity> getSubscriptions() {
        if (this.entity != null) {
            return this.entity.getSubscriptions();
        }
        return null;
    }

    public List<SubscriptionEntity> getSelectedSubscriptions() {
        if (this.entity != null) {
            return this.entity.getSelectedSubscriptions();
        }
        return null;
    }

    public TokenCredential getCredential(String subscriptionId) throws LoginFailureException {
        if (!this.isAuthenticated()) {
            throw new LoginFailureException("Please login first.");
        }
        Objects.requireNonNull(this.credentialBuilder, "Azure Account should be initialized first.");
        SubscriptionEntity subscriptionEntity = getSubscriptionById(subscriptionId);
        if (subscriptionEntity != null) {
            return getCredentialInternal(subscriptionEntity);
        }
        return null;
    }

    public AzureTokenCredentials getCredentialV1(String subscriptionId) throws LoginFailureException {
        if (!this.isAuthenticated()) {
            throw new LoginFailureException("Please login first.");
        }
        Objects.requireNonNull(this.credentialBuilder, "Azure Account should be initialized first.");

        SubscriptionEntity subscriptionEntity = getSubscriptionById(subscriptionId);
        if (subscriptionEntity != null) {
            return AzureIdentityCredentialTokenCredentialsConverter.convert(this.getEnvironment(), subscriptionEntity.getTenantId(),
                    getCredentialInternal(subscriptionEntity));
        }
        return null;
    }

    private SubscriptionEntity getSubscriptionById(String subscriptionId) {
        return getSubscriptions().stream()
                .filter(s -> StringUtils.equalsIgnoreCase(subscriptionId, s.getId())).findFirst().orElse(null);
    }

    private TokenCredential getCredentialInternal(SubscriptionEntity subscriptionEntity) {
        if (subscriptionEntity != null && StringUtils.isNotBlank(subscriptionEntity.getTenantId())) {
            return tenantToCredential.computeIfAbsent(subscriptionEntity.getTenantId(),
                    e -> new CachedTokenCredential(credentialBuilder.provideCredentialForTenant(subscriptionEntity.getTenantId())));
        }
        return null;
    }
}
