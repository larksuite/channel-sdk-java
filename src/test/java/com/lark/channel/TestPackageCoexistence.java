// Copyright (c) 2026 Lark Technologies Pte. Ltd.
// SPDX-License-Identifier: MIT

package com.lark.channel;

import org.junit.Assert;
import org.junit.Test;

public class TestPackageCoexistence {
    @Test
    public void testLegacyAndStandalonePackagesCanLoadTogether() throws Exception {
        Class<?> standaloneClass = Class.forName("com.lark.channel.LarkChannel");
        Class<?> legacyClass = Class.forName("com.lark.oapi.channel.LarkChannel");

        Assert.assertEquals("com.lark.channel.LarkChannel", standaloneClass.getName());
        Assert.assertEquals("com.lark.oapi.channel.LarkChannel", legacyClass.getName());
        Assert.assertNotSame(standaloneClass, legacyClass);
    }
}
