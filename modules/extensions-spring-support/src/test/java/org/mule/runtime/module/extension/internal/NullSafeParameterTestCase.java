/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.extension.internal;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import org.mule.functional.junit4.ExtensionFunctionalTestCase;
import org.mule.test.vegan.extension.BananaConfig;
import org.mule.test.vegan.extension.FarmedFood;
import org.mule.test.vegan.extension.HealthyFood;
import org.mule.test.vegan.extension.VeganExtension;
import org.mule.test.vegan.extension.VeganPolicy;

import org.junit.Test;

public class NullSafeParameterTestCase extends ExtensionFunctionalTestCase {

  @Override
  protected Class<?>[] getAnnotatedExtensionClasses() {
    return new Class[] {VeganExtension.class};
  }

  @Override
  protected String getConfigFile() {
    return "vegan-null-safe-operation.xml";
  }

  @Test
  public void getNullSafeObject() throws Exception {
    VeganPolicy policy = (VeganPolicy) flowRunner("policy").run().getMessage().getPayload().getValue();
    assertThat(policy, is(notNullValue()));
    assertThat(policy.getMeetAllowed(), is(false));
    assertThat(policy.getIngredients(), is(notNullValue()));
    assertThat(policy.getIngredients().getSaltMiligrams(), is(0));
    assertThat(policy.getIngredients().getSaltReplacementName(), is(nullValue()));
  }

  @Test
  public void getNullSafeAbstractObjectWithDefault() throws Exception {
    FarmedFood response = (FarmedFood) flowRunner("implementingType").run().getMessage().getPayload().getValue();
    assertThat(response, is(notNullValue()));
    assertThat(response, instanceOf(HealthyFood.class));
    assertThat(response.canBeEaten(), is(true));
  }

  @Test
  public void nestedNullSafe() throws Exception {
    assertNestedNullSafe("implementingType");
  }

  private void assertNestedNullSafe(String flowName) throws Exception {
    FarmedFood response = (FarmedFood) flowRunner(flowName).run().getMessage().getPayload().getValue();
    assertThat(response, is(instanceOf(HealthyFood.class)));
    HealthyFood healthyFood = (HealthyFood) response;
    assertHealthyFood(healthyFood);
  }

  @Test
  public void topLevelNestedNullSafe() throws Exception {
    assertNestedNullSafe("topLevelNullSafe");
  }

  private void assertHealthyFood(HealthyFood healthyFood) {
    assertThat(healthyFood.getTasteProfile(), is(notNullValue()));
    assertThat(healthyFood.getTasteProfile().isTasty(), is(false));
  }

  @Test
  public void nestedNullSafeInConfig() throws Exception {
    BananaConfig config = (BananaConfig) flowRunner("inConfig").run().getMessage().getPayload().getValue();
    assertHealthyFood(config.getHealthyFood());
  }

}
