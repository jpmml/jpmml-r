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
import org.jpmml.converter.ContinuousLabel;
import org.jpmml.converter.Feature;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.SchemaUtil;
import org.jpmml.converter.general_regression.GeneralRegressionModelUtil;

public class LRMConverter extends RMSConverter {

	public LRMConverter(RGenericVector lrm){
		super(lrm);
	}

	@Override
	public void encodeSchema(RExpEncoder encoder){
		RGenericVector lrm = getObject();

		RIntegerVector freq = lrm.getIntegerElement("freq");

		RStringVector freqNames;

		if(freq.hasAttribute("names")){
			freqNames = freq.names();
		} else

		{
			freqNames = freq.dimnames(0);
		}

		super.encodeSchema(encoder);

		ContinuousLabel continuousLabel = (ContinuousLabel)encoder.getLabel();

		DataField dataField = (DataField)encoder.toCategorical(continuousLabel.getName(), freqNames.getValues());

		encoder.setLabel(dataField);
	}

	@Override
	public Model encodeModel(Schema schema){
		RGenericVector lrm = getObject();

		RDoubleVector coefficients = lrm.getDoubleElement("coefficients");

		CategoricalLabel categoricalLabel = (CategoricalLabel)schema.getLabel();

		SchemaUtil.checkSize(2, categoricalLabel);

		Object targetCategory = categoricalLabel.getValue(1);

		Double intercept = coefficients.getElement(getInterceptName(), false);

		List<? extends Feature> features = schema.getFeatures();

		SchemaUtil.checkSize(coefficients.size() - (intercept != null ? 1 : 0), features);

		List<Double> featureCoefficients = getFeatureCoefficients(features, coefficients);

		GeneralRegressionModel generalRegressionModel = new GeneralRegressionModel(GeneralRegressionModel.ModelType.GENERALIZED_LINEAR, MiningFunction.CLASSIFICATION, ModelUtil.createMiningSchema(categoricalLabel), null, null, null)
			.setLinkFunction(GeneralRegressionModel.LinkFunction.LOGIT)
			.setOutput(ModelUtil.createProbabilityOutput(DataType.DOUBLE, categoricalLabel));

		GeneralRegressionModelUtil.encodeRegressionTable(generalRegressionModel, features, featureCoefficients, intercept, targetCategory);

		return generalRegressionModel;
	}
}