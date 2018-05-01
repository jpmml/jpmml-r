/*
 * Copyright (c) 2018 Villu Ruusmann
 *
 * This file is part of JPMML-R
 *
 * JPMML-R is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JPMML-R is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with JPMML-R.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.jpmml.rexp;

import org.dmg.pmml.PMML;

public class CrossValGLMNetConverter extends Converter<RGenericVector> {

	private ConverterFactory converterFactory = ConverterFactory.newInstance();


	public CrossValGLMNetConverter(RGenericVector cvGlmnet){
		super(cvGlmnet);
	}

	@Override
	public PMML encodePMML(RExpEncoder encoder){
		RGenericVector cvGlmnet = getObject();

		RGenericVector glmnetFit = (RGenericVector)cvGlmnet.getValue("glmnet.fit");
		RDoubleVector lambda1SE = (RDoubleVector)cvGlmnet.getValue("lambda.1se");

		GLMNetConverter converter = (GLMNetConverter)this.converterFactory.newConverter(glmnetFit);
		converter.setLambdaS(lambda1SE.asScalar());

		return converter.encodePMML(encoder);
	}
}