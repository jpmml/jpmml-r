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
import java.util.Collections;
import java.util.List;

import org.dmg.pmml.DataField;
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
import org.jpmml.converter.SchemaUtil;
import org.jpmml.converter.mining.MiningModelUtil;
import org.jpmml.converter.regression.RegressionModelUtil;
import org.jpmml.rexp.evaluator.RExpFunctions;

public class HurdleConverter extends Converter<RGenericVector> {

	private ContinuousLabel label = null;


	public HurdleConverter(RGenericVector hurdle){
		super(hurdle);
	}

	@Override
	public PMML encodePMML(RExpEncoder encoder){
		Model zeroModel = encodeModel(HurdleConverter.NAME_ZERO, encoder);

		OutputField zeroPredictField = ModelUtil.createPredictedField(FieldNameUtil.create("predict", "zero"), OpType.CONTINUOUS, DataType.DOUBLE);

		encoder.createDerivedField(zeroModel, zeroPredictField, true);

		Segment zeroSegment = new Segment(True.INSTANCE, zeroModel)
			.setId("zero");

		Model countModel = encodeModel(HurdleConverter.NAME_COUNT, encoder);

		OutputField countPredictField = ModelUtil.createPredictedField(FieldNameUtil.create("predict", "count"), OpType.CONTINUOUS, DataType.DOUBLE);

		encoder.createDerivedField(countModel, countPredictField, true);

		Segment countSegment = new Segment(True.INSTANCE, countModel)
			.setId("count");

		Expression adjExpression = ExpressionUtil.createApply(PMMLFunctions.EXP,
			ExpressionUtil.createApply(PMMLFunctions.SUBTRACT,
				ExpressionUtil.createApply(PMMLFunctions.LN, new FieldRef(zeroPredictField)),
				ExpressionUtil.createApply(RExpFunctions.PPOIS, ExpressionUtil.createConstant(0), new FieldRef(countPredictField))
			)
		);

		DerivedField adjZeroPredictField = encoder.createDerivedField(FieldNameUtil.create("adjusted", zeroPredictField.requireName()), OpType.CONTINUOUS, DataType.DOUBLE, adjExpression);

		Expression targetExpression = ExpressionUtil.createApply(PMMLFunctions.MULTIPLY, new FieldRef(countPredictField), new FieldRef(adjZeroPredictField));

		DerivedField targetField = encoder.createDerivedField(FieldNameUtil.create("adjusted", countPredictField.requireName()), OpType.CONTINUOUS, DataType.DOUBLE, targetExpression);

		Feature feature = new ContinuousFeature(encoder, targetField);

		Schema schema = new Schema(encoder, this.label, Collections.emptyList());

		RegressionModel fullModel = RegressionModelUtil.createRegression(Collections.singletonList(feature), Collections.singletonList(1d), null, RegressionModel.NormalizationMethod.NONE, schema);

		OutputField truncatedTargetField = new OutputField(FieldNameUtil.create("truncated", this.label.getName()), OpType.CONTINUOUS, DataType.DOUBLE)
			.setResultFeature(ResultFeature.TRANSFORMED_VALUE)
			.setExpression(new FieldRef(countPredictField));

		Output output = new Output()
			.addOutputFields(truncatedTargetField);

		fullModel.setOutput(output);

		Segment fullSegment = new Segment(True.INSTANCE, fullModel)
			.setId("full");

		List<Model> models = Arrays.asList(zeroModel, countModel, fullModel);

		Segmentation segmentation = new Segmentation(Segmentation.MultipleModelMethod.MODEL_CHAIN, null)
			.addSegments(zeroSegment, countSegment, fullSegment);

		MiningModel miningModel = new MiningModel(MiningFunction.REGRESSION, MiningModelUtil.createMiningSchema(models))
			.setSegmentation(segmentation);

		return encoder.encodePMML(miningModel);
	}

	private Model encodeModel(String name, RExpEncoder encoder){
		RGenericVector hurdle = getObject();

		RDoubleVector coefficients = hurdle.getGenericElement("coefficients").getDoubleElement(name);
		RStringVector dist = hurdle.getGenericElement("dist").getStringElement(name);
		RExp terms = hurdle.getGenericElement("terms").getElement(name);
		RGenericVector model = hurdle.getGenericElement("model");

		RStringVector coefficientNames = coefficients.names();

		FormulaContext context = new ModelFrameFormulaContext(model);

		Formula formula = FormulaUtil.createFormula(terms, context, encoder);

		switch(name){
			case HurdleConverter.NAME_COUNT:
				FormulaUtil.setLabel(formula, terms, null, encoder);

				ContinuousLabel continuousLabel = (ContinuousLabel)encoder.getLabel();

				// XXX
				DataField dataField = (DataField)encoder.getField(continuousLabel.getName());
				dataField.setDataType(DataType.DOUBLE);

				this.label = new ContinuousLabel(dataField);

				break;
			case HurdleConverter.NAME_ZERO:
				break;
			default:
				throw new IllegalArgumentException();
		}

		encoder.setLabel(new ContinuousLabel(DataType.DOUBLE));

		List<Feature> features = encoder.getFeatures();
		if(!features.isEmpty()){
			features.clear();
		}

		List<String> names = FormulaUtil.removeSpecialSymbol(coefficientNames.getDequotedValues(), "(Intercept)");

		FormulaUtil.addFeatures(formula, names, true, encoder);

		features = encoder.getFeatures();

		Schema schema = encoder.createSchema();

		Double intercept = coefficients.getElement("(Intercept)", false);

		SchemaUtil.checkSize(coefficients.size() - (intercept != null ? 1 : 0), features);

		List<Double> featureCoefficients = new ArrayList<>();

		for(Feature feature : features){
			Double coefficient = formula.getCoefficient(feature, coefficients);

			featureCoefficients.add(coefficient);
		}

		RegressionModel regressionModel;

		switch(name){
			case HurdleConverter.NAME_ZERO:

				switch(dist.asScalar()){
					case "binomial":
						regressionModel = RegressionModelUtil.createRegression(features, featureCoefficients, intercept, RegressionModel.NormalizationMethod.LOGIT, schema);
						break;
					default:
						throw new IllegalArgumentException();
				}
				break;
			case HurdleConverter.NAME_COUNT:

				switch(dist.asScalar()){
					case "poisson":
						regressionModel = RegressionModelUtil.createRegression(features, featureCoefficients, intercept, RegressionModel.NormalizationMethod.EXP, schema);
						break;
					default:
						throw new IllegalArgumentException();
				}
				break;
			default:
				throw new IllegalArgumentException(name);
		}

		return regressionModel;
	}

	private static final String NAME_COUNT = "count";
	private static final String NAME_ZERO = "zero";
}