/*
 * Copyright (c) 2025 Villu Ruusmann
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
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.dmg.pmml.DataType;
import org.dmg.pmml.DerivedField;
import org.dmg.pmml.Expression;
import org.dmg.pmml.FieldRef;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.Model;
import org.dmg.pmml.OpType;
import org.dmg.pmml.OutputField;
import org.dmg.pmml.PMML;
import org.dmg.pmml.PMMLFunctions;
import org.dmg.pmml.ResultFeature;
import org.dmg.pmml.True;
import org.dmg.pmml.mining.MiningModel;
import org.dmg.pmml.mining.Segment;
import org.dmg.pmml.mining.Segmentation;
import org.dmg.pmml.regression.RegressionModel;
import org.jpmml.converter.ContinuousLabel;
import org.jpmml.converter.ExpressionUtil;
import org.jpmml.converter.Feature;
import org.jpmml.converter.FieldNameUtil;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.mining.MiningModelUtil;
import org.jpmml.converter.regression.RegressionModelUtil;

public class ZeroInflConverter extends MixtureModelConverter {

	public ZeroInflConverter(RGenericVector zeroInfl){
		super(zeroInfl);
	}

	@Override
	public PMML encodePMML(RExpEncoder encoder){
		Model zeroModel = encodeComponent(MixtureModelConverter.NAME_ZERO, encoder);

		OutputField zeroPredictField = ModelUtil.createPredictedField(FieldNameUtil.create("predict", MixtureModelConverter.NAME_ZERO), OpType.CONTINUOUS, DataType.DOUBLE);

		encoder.createDerivedField(zeroModel, zeroPredictField, true);

		Expression probaExpression = ExpressionUtil.createApply(PMMLFunctions.DIVIDE,
			ExpressionUtil.createApply(PMMLFunctions.EXP, new FieldRef(zeroPredictField)),
			ExpressionUtil.createApply(PMMLFunctions.ADD,
				ExpressionUtil.createConstant(1),
				ExpressionUtil.createApply(PMMLFunctions.EXP, new FieldRef(zeroPredictField))
			)
		);

		OutputField zeroProbabilityField = new OutputField(FieldNameUtil.create("probability", MixtureModelConverter.NAME_ZERO), OpType.CONTINUOUS, DataType.DOUBLE)
			.setResultFeature(ResultFeature.TRANSFORMED_VALUE)
			.setExpression(probaExpression);

		encoder.createDerivedField(zeroModel, zeroProbabilityField, true);

		Segment zeroSegment = new Segment(True.INSTANCE, zeroModel)
			.setId(MixtureModelConverter.NAME_ZERO);

		Model countModel = encodeComponent(MixtureModelConverter.NAME_COUNT, encoder);

		OutputField countPredictField = ModelUtil.createPredictedField(FieldNameUtil.create("predict", MixtureModelConverter.NAME_COUNT), OpType.CONTINUOUS, DataType.DOUBLE);

		encoder.createDerivedField(countModel, countPredictField, true);

		Segment countSegment = new Segment(True.INSTANCE, countModel)
			.setId(MixtureModelConverter.NAME_COUNT);

		Expression targetExpression = ExpressionUtil.createApply(PMMLFunctions.MULTIPLY,
			ExpressionUtil.createApply(PMMLFunctions.SUBTRACT,
				ExpressionUtil.createConstant(1),
				new FieldRef(zeroProbabilityField)
			),
			new FieldRef(countPredictField)
		);

		DerivedField targetField = encoder.createDerivedField(FieldNameUtil.create("adjusted", countPredictField.requireName()), OpType.CONTINUOUS, DataType.DOUBLE, targetExpression);

		ContinuousLabel label = getLabel();

		Map<String, OutputField> outputFields = Collections.singletonMap(FieldNameUtil.create("inflated", label.getName()), countPredictField);

		Model fullModel = encodeTarget(targetField, outputFields, encoder);

		Segment fullSegment = new Segment(True.INSTANCE, fullModel)
			.setId(MixtureModelConverter.NAME_FULL);

		List<Model> models = Arrays.asList(zeroModel, countModel, fullModel);

		Segmentation segmentation = new Segmentation(Segmentation.MultipleModelMethod.MODEL_CHAIN, null)
			.addSegments(zeroSegment, countSegment, fullSegment);

		MiningModel miningModel = new MiningModel(MiningFunction.REGRESSION, MiningModelUtil.createMiningSchema(models))
			.setSegmentation(segmentation);

		return encoder.encodePMML(miningModel);
	}

	@Override
	protected Model encodeZeroComponent(List<Feature> features, List<Double> coefficients, Double intercept, Schema schema){
		RGenericVector zeroInfl = getObject();

		RStringVector link = zeroInfl.getStringElement("link");

		String linkName = link.asScalar();
		switch(linkName){
			case "logit":
				return RegressionModelUtil.createRegression(features, coefficients, intercept, RegressionModel.NormalizationMethod.NONE, schema);
			default:
				throw new IllegalArgumentException(linkName);
		}
	}

	@Override
	protected Model encodeCountComponent(List<Feature> features, List<Double> coefficients, Double intercept, Schema schema){
		RGenericVector zeroInfl = getObject();

		RStringVector dist = zeroInfl.getStringElement("dist");

		String distName = dist.asScalar();
		switch(distName){
			case "negbin":
			case "poisson":
				return RegressionModelUtil.createRegression(features, coefficients, intercept, RegressionModel.NormalizationMethod.EXP, schema);
			default:
				throw new IllegalArgumentException(distName);
		}
	}
}