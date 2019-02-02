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

public class CrossValGLMNetConverter extends FilterModelConverter<RGenericVector, RGenericVector> {

	public CrossValGLMNetConverter(RGenericVector cvGlmnet){
		super(cvGlmnet);
	}

	@Override
	public GLMNetConverter createConverter(){
		RGenericVector cvGlmnet = getObject();

		RGenericVector glmnetFit = cvGlmnet.getGenericValue("glmnet.fit");
		RDoubleVector lambda1SE = cvGlmnet.getDoubleValue("lambda.1se");

		GLMNetConverter converter = (GLMNetConverter)newConverter(glmnetFit);
		converter.setLambdaS(lambda1SE.asScalar());

		return converter;
	}
}