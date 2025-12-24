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

import java.util.Arrays;

import org.dmg.pmml.DataField;
import org.dmg.pmml.DataType;
import org.dmg.pmml.DerivedField;
import org.dmg.pmml.Model;
import org.dmg.pmml.OpType;
import org.dmg.pmml.OutputField;
import org.dmg.pmml.mining.Segmentation;
import org.dmg.pmml.regression.RegressionModel;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.Feature;
import org.jpmml.converter.ModelEncoder;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.ScalarLabel;
import org.jpmml.converter.Schema;
import org.jpmml.converter.mining.MiningModelUtil;
import org.jpmml.converter.regression.RegressionModelUtil;

public class POLRConverter extends LMConverter {

	public POLRConverter(RGenericVector polr){
		super(polr);
	}

	@Override
	public void encodeSchema(RExpEncoder encoder){
		RGenericVector polr = getObject();

		RStringVector lev = polr.getStringElement("lev");

		super.encodeSchema(encoder);

		ScalarLabel scalarLabel = (ScalarLabel)encoder.getLabel();

		DataField dataField = (DataField)encoder.toOrdinal(scalarLabel.getName(), lev.getValues());

		encoder.setLabel(dataField);
	}

	@Override
	public Model encodeModel(Schema schema){
		RGenericVector polr = getObject();

		RStringVector method = polr.getStringElement("method");
		RNumberVector<?> zeta = polr.getNumericElement("zeta");

		ModelEncoder encoder = schema.getEncoder();

		Schema segmentSchema = schema.toAnonymousRegressorSchema(DataType.DOUBLE);

		RegressionModel firstRegressionModel = (RegressionModel)super.encodeModel(segmentSchema)
			.setTargets(ModelUtil.createRescaleTargets(-1d, null, segmentSchema.requireContinuousLabel()));

		OutputField linpredOutputField = ModelUtil.createPredictedField("linpred", OpType.CONTINUOUS, DataType.DOUBLE);

		DerivedField linpredField = encoder.createDerivedField(firstRegressionModel, linpredOutputField, true);

		Feature feature = new ContinuousFeature(encoder, linpredField);

		RegressionModel secondRegressionModel = RegressionModelUtil.createOrdinalClassification(feature, zeta.getValues(), parseNormalizationMethod(method.asScalar()), true, schema);

		return MiningModelUtil.createModelChain(Arrays.asList(firstRegressionModel, secondRegressionModel), Segmentation.MissingPredictionTreatment.RETURN_MISSING);
	}

	static
	private RegressionModel.NormalizationMethod parseNormalizationMethod(String method){

		switch(method){
			case "logistic":
				return RegressionModel.NormalizationMethod.LOGIT;
			case "probit":
				return RegressionModel.NormalizationMethod.PROBIT;
			case "loglog":
				return RegressionModel.NormalizationMethod.LOGLOG;
			case "cloglog":
				return RegressionModel.NormalizationMethod.CLOGLOG;
			case "cauchit":
				return RegressionModel.NormalizationMethod.CAUCHIT;
			default:
				throw new IllegalArgumentException(method);
		}
	}
}