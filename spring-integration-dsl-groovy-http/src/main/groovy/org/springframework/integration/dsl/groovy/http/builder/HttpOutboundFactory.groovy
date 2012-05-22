/*
 * Copyright 2002-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.springframework.integration.dsl.groovy.http.builder

import org.springframework.integration.dsl.groovy.BaseIntegrationComposition
import org.springframework.integration.dsl.groovy.builder.IntegrationComponentFactory
import org.springframework.integration.dsl.groovy.http.HttpOutbound

/**
 * @author David Turanski
 *
 */
class HttpOutboundFactory extends IntegrationComponentFactory {
	@Override
	public doNewInstance(FactoryBuilderSupport builder, Object name, Object value, Map attributes) {
		new HttpOutbound(attributes)
	}

	@Override
	void setParent(FactoryBuilderSupport builder, Object parent, Object httpOutbound) {
		assert parent instanceof BaseIntegrationComposition, "'${httpOutbound.builderName}' cannot be a child of '${parent.builderName}'"
		parent.add(httpOutbound)
	}
}