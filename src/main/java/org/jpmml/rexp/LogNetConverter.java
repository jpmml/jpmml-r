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

import org.dmg.pmml.DataType;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.Model;
import org.dmg.pmml.general_regression.GeneralRegressionModel;
import org.jpmml.converter.CategoricalLabel;
import org.jpmml.converter.Feature;
import org.jpmml.converter.Label;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.general_regression.GeneralRegressionModelUtil;

public class LogNetConverter extends GLMNetConverter {

	public LogNetConverter(RGenericVector lognet){
		super(lognet);
	}

	@Override
	public Model encodeModel(Label label, List<? extends Feature> features, List<Double> coefficients, Double intercept){
		CategoricalLabel categoricalLabel = (CategoricalLabel)label;

		if(categoricalLabel.size() != 2){
			throw new IllegalArgumentException();
		}

		String targetCategory = categoricalLabel.getValue(1);

		GeneralRegressionModel generalRegressionModel = new GeneralRegressionModel(GeneralRegressionModel.ModelType.GENERALIZED_LINEAR, MiningFunction.CLASSIFICATION, ModelUtil.createMiningSchema(label), null, null, null)
			.setLinkFunction(GeneralRegressionModel.LinkFunction.LOGIT)
			.setOutput(ModelUtil.createProbabilityOutput(DataType.DOUBLE, categoricalLabel));

		GeneralRegressionModelUtil.encodeRegressionTable(generalRegressionModel, features, intercept, coefficients, targetCategory);

		return generalRegressionModel;
	}
}