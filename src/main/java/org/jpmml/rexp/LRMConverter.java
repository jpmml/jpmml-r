/*
 * Copyright (c) 2017 Villu Ruusmann
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

import org.dmg.pmml.DataField;
import org.dmg.pmml.DataType;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.Model;
import org.dmg.pmml.general_regression.GeneralRegressionModel;
import org.jpmml.converter.CategoricalLabel;
import org.jpmml.converter.Feature;
import org.jpmml.converter.Label;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.general_regression.GeneralRegressionModelUtil;

public class LRMConverter extends RMSConverter {

	public LRMConverter(RGenericVector lrm){
		super(lrm);
	}

	@Override
	public void encodeSchema(RExpEncoder encoder){
		RGenericVector lrm = getObject();

		RIntegerVector freq = (RIntegerVector)lrm.getValue("freq");

		RStringVector freqNames = freq.dimnames(0);

		super.encodeSchema(encoder);

		Label label = encoder.getLabel();

		DataField dataField = encoder.toCategorical(label.getName(), freqNames.getValues());

		encoder.setLabel(dataField);
	}

	@Override
	public Model encodeModel(Schema schema){
		RGenericVector lrm = getObject();

		RDoubleVector coefficients = (RDoubleVector)lrm.getValue("coefficients");

		CategoricalLabel categoricalLabel = (CategoricalLabel)schema.getLabel();

		if(categoricalLabel.size() != 2){
			throw new IllegalArgumentException();
		}

		String targetCategory = categoricalLabel.getValue(1);

		Double intercept = coefficients.getValue(getInterceptName(), true);

		List<Feature> features = schema.getFeatures();

		if(coefficients.size() != (features.size() + (intercept != null ? 1 : 0))){
			throw new IllegalArgumentException();
		}

		List<Double> featureCoefficients = getFeatureCoefficients(features, coefficients);

		GeneralRegressionModel generalRegressionModel = new GeneralRegressionModel(GeneralRegressionModel.ModelType.GENERALIZED_LINEAR, MiningFunction.CLASSIFICATION, ModelUtil.createMiningSchema(categoricalLabel), null, null, null)
			.setLinkFunction(GeneralRegressionModel.LinkFunction.LOGIT)
			.setOutput(ModelUtil.createProbabilityOutput(DataType.DOUBLE, categoricalLabel));

		GeneralRegressionModelUtil.encodeRegressionTable(generalRegressionModel, features, intercept, featureCoefficients, targetCategory);

		return generalRegressionModel;
	}
}