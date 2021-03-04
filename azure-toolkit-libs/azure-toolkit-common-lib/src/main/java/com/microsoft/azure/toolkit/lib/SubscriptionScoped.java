/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for license information.
 */

package com.microsoft.azure.toolkit.lib;

import com.microsoft.azure.toolkit.lib.common.model.Subscription;
import lombok.RequiredArgsConstructor;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

@RequiredArgsConstructor
public abstract class SubscriptionScoped<T extends AzureService> {
    private final Function<List<Subscription>, T> creator;

    public T subscriptions(@Nonnull final List<Subscription> subscriptions) {
        return this.creator.apply(subscriptions);
    }

    public T subscription(@Nonnull final Subscription subscription) {
        return this.subscriptions(Collections.singletonList(subscription));
    }
}
