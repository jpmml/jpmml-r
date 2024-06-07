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
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.dmg.pmml.DataField;
import org.dmg.pmml.DataType;
import org.dmg.pmml.DerivedField;
import org.dmg.pmml.Expression;
import org.dmg.pmml.FieldRef;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.OpType;
import org.dmg.pmml.regression.RegressionModel;
import org.dmg.pmml.regression.RegressionTable;
import org.jpmml.converter.CategoricalLabel;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.ExpressionUtil;
import org.jpmml.converter.Feature;
import org.jpmml.converter.FieldNameUtil;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.TypeUtil;
import org.jpmml.converter.regression.RegressionModelUtil;

public class MaxLikConverter extends ModelConverter<RGenericVector> {

	public MaxLikConverter(RGenericVector maxLik){
		super(maxLik);
	}

	@Override
	public void encodeSchema(RExpEncoder encoder){
		RGenericVector maxLik = getObject();

		RDoubleVector estimate = maxLik.getDoubleElement("estimate");
		RClosure apolloProbabilities = (RClosure)maxLik.getElement("apollo_probabilities");

		RStringVector estimateNames = estimate.names();

		Map<String, Double> betas = new LinkedHashMap<>();

		for(int i = 0; i < estimate.size(); i++){
			betas.put(estimateNames.getDequotedValue(i), estimate.getValue(i));
		}

		RFunctionCall body = (RFunctionCall)apolloProbabilities.getBody();

		if(!body.hasValue("{")){
			throw new IllegalArgumentException();
		}

		Map<Object, RExp> utilityFunctions = new LinkedHashMap<>();

		for(Iterator<RExp> it = body.argumentValues(); it.hasNext(); ){
			RExp argValue = it.next();

			if(argValue instanceof RFunctionCall){
				RFunctionCall functionCall = (RFunctionCall)argValue;

				if(functionCall.hasValue("=")){
					Iterator<RExp> it2 = functionCall.argumentValues();

					RExp firstArgValue = it2.next();
					RExp secondArgValue = it2.next();

					Object choice = matchUtilityFunction(firstArgValue);
					if(choice != null){
						utilityFunctions.put(choice, secondArgValue);
					}
				}
			} else

			{
				throw new IllegalArgumentException();
			}
		}

		if(utilityFunctions.isEmpty()){
			throw new IllegalArgumentException();
		}

		List<?> choices = new ArrayList<>(utilityFunctions.keySet());

		DataField choiceField = encoder.createDataField("choice", OpType.CATEGORICAL, TypeUtil.getDataType(choices, DataType.STRING), choices);

		encoder.setLabel(choiceField);

		for(Object choice : choices){
			RFunctionCall functionCall = (RFunctionCall)utilityFunctions.get(choice);

			Expression expression = toPMML(functionCall, betas, encoder);

			DerivedField derivedField = encoder.createDerivedField(FieldNameUtil.create("utility", choice), OpType.CONTINUOUS, DataType.DOUBLE, expression);

			Feature feature = new ContinuousFeature(encoder, derivedField);

			encoder.addFeature(feature);
		}
	}

	@Override
	public RegressionModel encodeModel(Schema schema){
		RGenericVector maxLik = getObject();

		CategoricalLabel categoricalLabel = (CategoricalLabel)schema.getLabel();
		List<? extends Feature> features = schema.getFeatures();

		List<RegressionTable> regressionTables = new ArrayList<>();

		for(int i = 0; i < categoricalLabel.size(); i++){
			Feature feature = features.get(i);

			RegressionTable regressionTable = RegressionModelUtil.createRegressionTable(Collections.singletonList(feature), Collections.singletonList(1d), null)
				.setTargetCategory(categoricalLabel.getValue(i));

			regressionTables.add(regressionTable);
		}

		RegressionModel regressionModel = new RegressionModel(MiningFunction.CLASSIFICATION, ModelUtil.createMiningSchema(categoricalLabel), regressionTables)
			.setNormalizationMethod(RegressionModel.NormalizationMethod.SOFTMAX)
			.setOutput(ModelUtil.createProbabilityOutput(DataType.DOUBLE, categoricalLabel));

		return regressionModel;
	}

	static
	private Object matchUtilityFunction(RExp argValue){

		if(argValue instanceof RFunctionCall){
			RFunctionCall functionCall = (RFunctionCall)argValue;

			if(functionCall.hasValue("[[")){
				Iterator<RExp> it = functionCall.argumentValues();

				RExp firstArgValue = it.next();

				if(firstArgValue instanceof RString){
					RString string = (RString)firstArgValue;

					if(Objects.equals("V", string.getValue())){
						RExp secondArgValue = it.next();

						if(secondArgValue instanceof RVector){
							RVector<?> vector = (RVector<?>)secondArgValue;

							return vector.asScalar();
						}
					}
				}
			}
		}

		return null;
	}

	static
	private Expression toPMML(RExp argumentValue, Map<String, Double> betas, RExpEncoder encoder){

		if(argumentValue instanceof RString){
			RString string = (RString)argumentValue;

			String stringValue = string.getValue();
			if(betas.containsKey(stringValue)){
				return ExpressionUtil.createConstant(betas.get(stringValue));
			}

			DataField dataField = encoder.getDataField(stringValue);
			if(dataField == null){
				dataField = encoder.createDataField(stringValue, OpType.CONTINUOUS, DataType.DOUBLE);
			}

			return new FieldRef(stringValue);
		} else

		if(argumentValue instanceof RNumberVector){
			RNumberVector<?> numberVector = (RNumberVector<?>)argumentValue;

			return ExpressionUtil.createConstant(numberVector.asScalar());
		} else

		if(argumentValue instanceof RFunctionCall){
			RFunctionCall functionCall = (RFunctionCall)argumentValue;

			RString value = (RString)functionCall.getValue();
			Iterator<RExp> it = functionCall.argumentValues();

			try {
				switch(value.getValue()){
					case "(":
						return toPMML(it.next(), betas, encoder);
					case "+":
					case "-":
					case "*":
					case "/":
						// XXX
						return ExpressionUtil.createApply(value.getValue(),
							toPMML(it.next(), betas, encoder),
							toPMML(it.next(), betas, encoder)
						);
					default:
						throw new IllegalArgumentException(value.getValue());
				}
			} finally {

				if(it.hasNext()){
					throw new IllegalStateException();
				}
			}
		} else

		{
			throw new IllegalArgumentException();
		}
	}
}