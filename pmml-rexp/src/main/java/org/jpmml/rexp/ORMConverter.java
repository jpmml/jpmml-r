/*
 * Copyright (c) 2024 Villu Ruusmann
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.dmg.pmml.DataField;
import org.dmg.pmml.DataType;
import org.dmg.pmml.DerivedField;
import org.dmg.pmml.Model;
import org.dmg.pmml.OpType;
import org.dmg.pmml.OutputField;
import org.dmg.pmml.mining.Segmentation;
import org.dmg.pmml.regression.RegressionModel;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.ContinuousLabel;
import org.jpmml.converter.ExceptionUtil;
import org.jpmml.converter.Feature;
import org.jpmml.converter.ModelEncoder;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.OrdinalLabel;
import org.jpmml.converter.Schema;
import org.jpmml.converter.mining.MiningModelUtil;
import org.jpmml.converter.regression.RegressionModelUtil;

public class ORMConverter extends RMSConverter {

	public ORMConverter(RGenericVector orm){
		super(orm);
	}

	@Override
	public void encodeSchema(RExpEncoder encoder){
		RGenericVector orm = getObject();

		RStringVector yUnique = orm.getStringElement("yunique");

		super.encodeSchema(encoder);

		ContinuousLabel continuousLabel = (ContinuousLabel)encoder.getLabel();

		DataField dataField = (DataField)encoder.toOrdinal(continuousLabel.getName(), yUnique.getValues());

		// XXX
		dataField.setDataType(DataType.STRING);

		encoder.setLabel(dataField);
	}

	@Override
	public Model encodeModel(Schema schema){
		RGenericVector orm = getObject();

		RStringVector family = orm.getStringElement("family");
		RDoubleVector coefficients = orm.getDoubleElement("coefficients");

		ModelEncoder encoder = schema.getEncoder();
		OrdinalLabel ordinalLabel = schema.requireOrdinalLabel();
		List<? extends Feature> features = schema.getFeatures();

		List<Double> thresholdCoefficients = new ArrayList<>();
		List<Double> featureCoefficients = new ArrayList<>();

		for(int i = 0, max = (ordinalLabel.size() - 1); i < max; i++){
			Double coefficient = coefficients.getValue(i);

			coefficient = -1 * coefficient.doubleValue();

			thresholdCoefficients.add(coefficient);
		} // End for

		for(Feature feature : features){
			Double coefficient = getFeatureCoefficient(feature, coefficients);

			featureCoefficients.add(coefficient);
		}

		Schema segmentSchema = schema.toAnonymousRegressorSchema(DataType.DOUBLE);

		RegressionModel firstRegressionModel = RegressionModelUtil.createRegression(features, featureCoefficients, null, null, segmentSchema)
			.setTargets(ModelUtil.createRescaleTargets(-1d, null, segmentSchema.requireContinuousLabel()));

		OutputField linpredOutputField = ModelUtil.createPredictedField("linpred", OpType.CONTINUOUS, DataType.DOUBLE);

		DerivedField linpredField = encoder.createDerivedField(firstRegressionModel, linpredOutputField, true);

		Feature feature = new ContinuousFeature(encoder, linpredField);

		RegressionModel secondRegressionModel = RegressionModelUtil.createOrdinalClassification(feature, thresholdCoefficients, parseNormalizationMethod(family.asScalar()), true, schema);

		return MiningModelUtil.createModelChain(Arrays.asList(firstRegressionModel, secondRegressionModel), Segmentation.MissingPredictionTreatment.RETURN_MISSING);
	}

	static
	private RegressionModel.NormalizationMethod parseNormalizationMethod(String family){

		switch(family){
			case "logistic":
				return RegressionModel.NormalizationMethod.LOGIT;
			case "probit":
				return RegressionModel.NormalizationMethod.PROBIT;
			case "cauchit":
				return RegressionModel.NormalizationMethod.CAUCHIT;
			default:
				throw new RExpException("Family " + ExceptionUtil.formatParameter(family) + " is not supported");
		}
	}
}