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
import java.util.Collections;
import java.util.List;

import org.dmg.pmml.DataType;
import org.dmg.pmml.DerivedField;
import org.dmg.pmml.Expression;
import org.dmg.pmml.FieldRef;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.Model;
import org.dmg.pmml.OpType;
import org.dmg.pmml.Output;
import org.dmg.pmml.OutputField;
import org.dmg.pmml.PMML;
import org.dmg.pmml.PMMLFunctions;
import org.dmg.pmml.ResultFeature;
import org.dmg.pmml.True;
import org.dmg.pmml.mining.MiningModel;
import org.dmg.pmml.mining.Segment;
import org.dmg.pmml.mining.Segmentation;
import org.dmg.pmml.regression.RegressionModel;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.ContinuousLabel;
import org.jpmml.converter.ExpressionUtil;
import org.jpmml.converter.Feature;
import org.jpmml.converter.FieldNameUtil;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.mining.MiningModelUtil;
import org.jpmml.converter.regression.RegressionModelUtil;
import org.jpmml.rexp.evaluator.RExpFunctions;

public class HurdleConverter extends MixtureModelConverter {

	public HurdleConverter(RGenericVector hurdle){
		super(hurdle);
	}

	@Override
	public PMML encodePMML(RExpEncoder encoder){
		Model zeroModel = encodeComponent(MixtureModelConverter.NAME_ZERO, encoder);

		OutputField zeroPredictField = ModelUtil.createPredictedField(FieldNameUtil.create("predict", "zero"), OpType.CONTINUOUS, DataType.DOUBLE);

		encoder.createDerivedField(zeroModel, zeroPredictField, true);

		Segment zeroSegment = new Segment(True.INSTANCE, zeroModel)
			.setId(MixtureModelConverter.NAME_ZERO);

		Model countModel = encodeComponent(MixtureModelConverter.NAME_COUNT, encoder);

		OutputField countPredictField = ModelUtil.createPredictedField(FieldNameUtil.create("predict", "count"), OpType.CONTINUOUS, DataType.DOUBLE);

		encoder.createDerivedField(countModel, countPredictField, true);

		Segment countSegment = new Segment(True.INSTANCE, countModel)
			.setId(MixtureModelConverter.NAME_COUNT);

		Expression adjExpression = ExpressionUtil.createApply(PMMLFunctions.EXP,
			ExpressionUtil.createApply(PMMLFunctions.SUBTRACT,
				ExpressionUtil.createApply(PMMLFunctions.LN, new FieldRef(zeroPredictField)),
				ExpressionUtil.createApply(RExpFunctions.STATS_PPOIS, ExpressionUtil.createConstant(0), new FieldRef(countPredictField))
			)
		);

		DerivedField adjZeroPredictField = encoder.createDerivedField(FieldNameUtil.create("adjusted", zeroPredictField.requireName()), OpType.CONTINUOUS, DataType.DOUBLE, adjExpression);

		Expression targetExpression = ExpressionUtil.createApply(PMMLFunctions.MULTIPLY, new FieldRef(countPredictField), new FieldRef(adjZeroPredictField));

		DerivedField targetField = encoder.createDerivedField(FieldNameUtil.create("adjusted", countPredictField.requireName()), OpType.CONTINUOUS, DataType.DOUBLE, targetExpression);

		Feature feature = new ContinuousFeature(encoder, targetField);

		ContinuousLabel label = getLabel();

		Schema schema = new Schema(encoder, label, Collections.emptyList());

		RegressionModel fullModel = RegressionModelUtil.createRegression(Collections.singletonList(feature), Collections.singletonList(1d), null, RegressionModel.NormalizationMethod.NONE, schema);

		OutputField truncatedTargetField = new OutputField(FieldNameUtil.create("truncated", label.getName()), OpType.CONTINUOUS, DataType.DOUBLE)
			.setResultFeature(ResultFeature.TRANSFORMED_VALUE)
			.setExpression(new FieldRef(countPredictField));

		Output output = new Output()
			.addOutputFields(truncatedTargetField);

		fullModel.setOutput(output);

		Segment fullSegment = new Segment(True.INSTANCE, fullModel)
			.setId(MixtureModelConverter.NAME_FULL);

		List<Model> models = Arrays.asList(zeroModel, countModel, fullModel);

		Segmentation segmentation = new Segmentation(Segmentation.MultipleModelMethod.MODEL_CHAIN, null)
			.addSegments(zeroSegment, countSegment, fullSegment);

		MiningModel miningModel = new MiningModel(MiningFunction.REGRESSION, MiningModelUtil.createMiningSchema(models))
			.setSegmentation(segmentation);

		return encoder.encodePMML(miningModel);
	}
}