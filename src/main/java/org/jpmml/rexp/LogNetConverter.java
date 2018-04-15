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

import java.util.List;

import org.dmg.pmml.Model;
import org.dmg.pmml.regression.RegressionModel;
import org.jpmml.converter.Schema;
import org.jpmml.converter.regression.RegressionModelUtil;

public class LogNetConverter extends GLMNetConverter {

	public LogNetConverter(RGenericVector lognet){
		super(lognet);
	}

	@Override
	public Model encodeModel(RDoubleVector a0, RExp beta, int column, Schema schema){
		Double intercept = a0.getValue(column);
		List<Double> coefficients = getCoefficients((S4Object)beta, column);

		return RegressionModelUtil.createBinaryLogisticClassification(schema.getFeatures(), coefficients, intercept, RegressionModel.NormalizationMethod.LOGIT, true, schema);
	}
}