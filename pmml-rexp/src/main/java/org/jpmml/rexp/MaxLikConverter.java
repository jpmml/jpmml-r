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

import org.dmg.pmml.Apply;
import org.dmg.pmml.DataField;
import org.dmg.pmml.DataType;
import org.dmg.pmml.DerivedField;
import org.dmg.pmml.Expression;
import org.dmg.pmml.Field;
import org.dmg.pmml.FieldRef;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.Model;
import org.dmg.pmml.OpType;
import org.dmg.pmml.PMMLFunctions;
import org.dmg.pmml.regression.RegressionModel;
import org.dmg.pmml.regression.RegressionTable;
import org.jpmml.converter.CategoricalLabel;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.ExpressionUtil;
import org.jpmml.converter.Feature;
import org.jpmml.converter.FieldNameUtil;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.PMMLEncoder;
import org.jpmml.converter.Schema;
import org.jpmml.converter.TypeUtil;
import org.jpmml.converter.regression.RegressionModelUtil;

public class MaxLikConverter extends ModelConverter<RGenericVector> {

	private Map<String, RExp> variables = null;

	private Map<?, RFunctionCall> utilityFunctions = null;

	private RFunctionCall nlNests = null;

	private Map<?, RFunctionCall> nlStructures = null;

	private Map<String, Double> estimates = null;


	public MaxLikConverter(RGenericVector maxLik){
		super(maxLik);
	}

	@Override
	public void encodeSchema(RExpEncoder encoder){
		parseApolloProbabilities();
		parseEstimate();

		RGenericVector maxLik = getObject();

		RStringVector modelTypeList = maxLik.getStringElement("modelTypeList");

		Map<String, RExp> variables = this.variables;

		Map<?, RFunctionCall> utilityFunctions = this.utilityFunctions;

		if(utilityFunctions.isEmpty()){
			throw new IllegalArgumentException();
		}

		Map<String, Double> estimates = this.estimates;

		List<?> choices = new ArrayList<>(utilityFunctions.keySet());

		DataField choiceField = encoder.createDataField("choice", OpType.CATEGORICAL, TypeUtil.getDataType(choices, DataType.STRING), choices);

		encoder.setLabel(choiceField);

		Map<Object, Feature> choiceFeatures = new LinkedHashMap<>();

		for(Object choice : choices){
			RFunctionCall functionCall = utilityFunctions.get(choice);

			Expression expression = toPMML(functionCall, variables, estimates, encoder);

			DerivedField derivedField = encoder.createDerivedField(FieldNameUtil.create("utility", choice), OpType.CONTINUOUS, DataType.DOUBLE, expression);

			Feature feature = new ContinuousFeature(encoder, derivedField);

			choiceFeatures.put(choice, feature);

			// XXX
			encoder.addFeature(feature);
		}

		String modelType = modelTypeList.getValue(0);
		switch(modelType){
			case "MNL":
				break;
			case "NL":
				{
					RFunctionCall nlNests = this.nlNests;
					Map<?, RFunctionCall> nlStructures = this.nlStructures;

					if(nlNests == null){
						throw new IllegalArgumentException();
					}

					Map<?, ?> lambdas = parseList(nlNests, estimates);

					if(nlStructures.isEmpty()){
						throw new IllegalArgumentException();
					} // End if

					if(!Objects.equals(lambdas.keySet(), nlStructures.keySet())){
						throw new IllegalArgumentException();
					}

					choices = new ArrayList<>(nlStructures.keySet());

					// XXX
					Collections.reverse(choices);

					for(Object choice : choices){
						Number lambda = (Number)lambdas.get(choice);

						RFunctionCall functionCall = nlStructures.get(choice);

						List<?> childChoices = parseVector(functionCall);
						if(childChoices.isEmpty()){
							throw new IllegalArgumentException();
						}

						Apply apply = ExpressionUtil.createApply(PMMLFunctions.SUM);

						for(Object childChoice : childChoices){
							Feature choiceFeature = choiceFeatures.get(childChoice);

							if(choiceFeature == null){
								throw new IllegalArgumentException();
							}

							Apply choiceApply;

							if(lambda.doubleValue() != 1d){
								choiceApply = ExpressionUtil.createApply(PMMLFunctions.EXP,
									ExpressionUtil.createApply(PMMLFunctions.DIVIDE, choiceFeature.ref(), ExpressionUtil.createConstant(lambda))
								);
							} else

							{
								choiceApply = ExpressionUtil.createApply(PMMLFunctions.EXP,
									choiceFeature.ref()
								);
							}

							apply.addExpressions(choiceApply);
						}

						apply = ExpressionUtil.createApply(PMMLFunctions.LN, apply);

						if(lambda.doubleValue() != 1d){
							apply = ExpressionUtil.createApply(PMMLFunctions.MULTIPLY, apply, ExpressionUtil.createConstant(lambda));
						}

						DerivedField derivedField = encoder.createDerivedField(FieldNameUtil.create("utility", choice), OpType.CONTINUOUS, DataType.DOUBLE, apply);

						Feature feature = new ContinuousFeature(encoder, derivedField);

						choiceFeatures.put(choice, feature);
					}
				}
				break;
			default:
				throw new IllegalArgumentException();
		}
	}

	@Override
	public Model encodeModel(Schema schema){
		RGenericVector maxLik = getObject();

		RStringVector modelTypeList = maxLik.getStringElement("modelTypeList");

		PMMLEncoder encoder = schema.getEncoder();
		CategoricalLabel categoricalLabel = (CategoricalLabel)schema.getLabel();
		List<? extends Feature> features = schema.getFeatures();

		List<RegressionTable> regressionTables = new ArrayList<>();

		String modelType = modelTypeList.getValue(0);
		switch(modelType){
			case "MNL":
				{
					for(int i = 0; i < categoricalLabel.size(); i++){
						Object choice = categoricalLabel.getValue(i);

						Feature feature = toExpFeature(getUtilityFeature(choice, encoder), encoder);

						RegressionTable regressionTable = RegressionModelUtil.createRegressionTable(Collections.singletonList(feature), Collections.singletonList(1d), null)
							.setTargetCategory(choice);

						regressionTables.add(regressionTable);
					}
				}
				break;
			case "NL":
				{
					RFunctionCall nlNests = this.nlNests;
					Map<?, RFunctionCall> nlStructures = this.nlStructures;

					Map<String, Double> estimates = this.estimates;

					Map<?, ?> lambdas = parseList(nlNests, estimates);

					Map<Object, Object> nlTree = new LinkedHashMap<>();

					List<?> choices = new ArrayList<>(nlStructures.keySet());

					for(Object choice : choices){
						RFunctionCall functionCall = nlStructures.get(choice);

						List<?> childChoices = parseVector(functionCall);
						for(Object childChoice : childChoices){
							nlTree.put(childChoice, choice);
						}
					}

					for(int i = 0; i < categoricalLabel.size(); i++){
						Object choice = categoricalLabel.getValue(i);

						Apply apply = ExpressionUtil.createApply(PMMLFunctions.PRODUCT);

						for(Object currentChoice = choice, nextChoice = nlTree.get(currentChoice); nextChoice != null; currentChoice = nextChoice, nextChoice = nlTree.get(currentChoice)){
							Number lambda = (Number)lambdas.get(nextChoice);

							Feature currentUtilityFeature = toExpFeature(getUtilityFeature(currentChoice, encoder), encoder);
							Feature nextUtilityFeature = toExpFeature(getUtilityFeature(nextChoice, encoder), encoder);

							DerivedField derivedField = encoder.ensureDerivedField(FieldNameUtil.create("term", currentChoice, nextChoice), OpType.CONTINUOUS, DataType.DOUBLE, () -> {
								Apply choiceApply = ExpressionUtil.createApply(PMMLFunctions.DIVIDE, currentUtilityFeature.ref(), nextUtilityFeature.ref());

								if(lambda.doubleValue() != 1d){
									choiceApply = ExpressionUtil.createApply(PMMLFunctions.POW, choiceApply, ExpressionUtil.createConstant(1d / lambda.doubleValue()));
								}

								return choiceApply;
							});

							apply.addExpressions(new FieldRef(derivedField));
						}

						DerivedField derivedField = encoder.createDerivedField(FieldNameUtil.create("term", choice), OpType.CONTINUOUS, DataType.DOUBLE, apply);

						Feature feature = new ContinuousFeature(encoder, derivedField);

						RegressionTable regressionTable = RegressionModelUtil.createRegressionTable(Collections.singletonList(feature), Collections.singletonList(1d), null)
							.setTargetCategory(choice);

						regressionTables.add(regressionTable);
					}
				}
				break;
			default:
				throw new IllegalArgumentException(modelType);
		}

		RegressionModel regressionModel = new RegressionModel(MiningFunction.CLASSIFICATION, ModelUtil.createMiningSchema(categoricalLabel), regressionTables)
			.setNormalizationMethod(RegressionModel.NormalizationMethod.SIMPLEMAX)
			.setOutput(ModelUtil.createProbabilityOutput(DataType.DOUBLE, categoricalLabel));

		return regressionModel;
	}

	private void parseApolloProbabilities(){
		RGenericVector maxLik = getObject();

		RClosure apolloProbabilities = (RClosure)maxLik.getElement("apollo_probabilities");

		RFunctionCall body = (RFunctionCall)apolloProbabilities.getBody();

		if(!body.hasValue("{")){
			throw new IllegalArgumentException();
		}

		Map<String, RExp> variables = new LinkedHashMap<>();

		Map<Object, RFunctionCall> utilityFunctions = new LinkedHashMap<>();

		RFunctionCall nlNests = null;
		Map<Object, RFunctionCall> nlStructures = new LinkedHashMap<>();

		for(Iterator<RExp> it = body.argumentValues(); it.hasNext(); ){
			RExp argValue = it.next();

			if(argValue instanceof RFunctionCall){
				RFunctionCall functionCall = (RFunctionCall)argValue;

				if(functionCall.hasValue("=")){
					Iterator<RExp> it2 = functionCall.argumentValues();

					RExp firstArgValue = it2.next();
					RExp secondArgValue = it2.next();

					if(firstArgValue instanceof RString){
						RString string = (RString)firstArgValue;

						if(matchVariable(firstArgValue, "nlNests")){
							nlNests = (RFunctionCall)secondArgValue;

							continue;
						}

						variables.put(string.getValue(), secondArgValue);
					} else

					if(firstArgValue instanceof RFunctionCall){
						Object choice;

						choice = matchUtilityFunction(firstArgValue);
						if(choice != null){
							utilityFunctions.put(choice, (RFunctionCall)secondArgValue);

							continue;
						}

						choice = matchNLStructure(firstArgValue);
						if(choice != null){
							nlStructures.put(choice, (RFunctionCall)secondArgValue);

							continue;
						}
					}
				}
			} else

			{
				throw new IllegalArgumentException();
			}
		}

		if(!Collections.disjoint(utilityFunctions.keySet(), nlStructures.keySet())){
			throw new IllegalArgumentException();
		}

		this.variables = variables;

		this.utilityFunctions = utilityFunctions;

		this.nlNests = nlNests;
		this.nlStructures = nlStructures;
	}

	private void parseEstimate(){
		RGenericVector maxLik = getObject();

		RDoubleVector estimate = maxLik.getDoubleElement("estimate");

		RStringVector estimateNames = estimate.names();

		Map<String, Double> estimates = new LinkedHashMap<>();

		for(int i = 0; i < estimate.size(); i++){
			estimates.put(estimateNames.getDequotedValue(i), estimate.getValue(i));
		}

		this.estimates = estimates;
	}

	static
	private boolean matchVariable(RExp argValue, String variableName){

		if(argValue instanceof RString){
			RString string = (RString)argValue;

			return Objects.equals(variableName, string.getValue());
		}

		return false;
	}

	static
	private Object matchUtilityFunction(RExp argValue){
		return matchListAssignment(argValue, "V");
	}

	static
	private Object matchNLStructure(RExp argValue){
		return matchListAssignment(argValue, "nlStructure");
	}

	static
	private Object matchListAssignment(RExp argValue, String variableName){

		if(argValue instanceof RFunctionCall){
			RFunctionCall functionCall = (RFunctionCall)argValue;

			if(functionCall.hasValue("[[")){
				Iterator<RExp> it = functionCall.argumentValues();

				RExp firstArgValue = it.next();

				if(firstArgValue instanceof RString){
					RString string = (RString)firstArgValue;

					if(Objects.equals(variableName, string.getValue())){
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
	private ContinuousFeature getUtilityFeature(Object choice, PMMLEncoder encoder){
		Field<?> field = encoder.getField(FieldNameUtil.create("utility", choice));

		return new ContinuousFeature(encoder, field);
	}

	static
	private ContinuousFeature toExpFeature(Feature feature, PMMLEncoder encoder){
		DerivedField derivedField = encoder.ensureDerivedField(FieldNameUtil.create(PMMLFunctions.EXP, feature), OpType.CONTINUOUS, DataType.DOUBLE, () -> {
			return ExpressionUtil.createApply(PMMLFunctions.EXP, feature.ref());
		});

		return new ContinuousFeature(encoder, derivedField);
	}

	static
	private Expression toPMML(RExp argumentValue, Map<String, RExp> variables, Map<String, Double> estimates, RExpEncoder encoder){

		if(argumentValue instanceof RString){
			RString string = (RString)argumentValue;

			String stringValue = string.getValue();

			if(estimates.containsKey(stringValue)){
				return ExpressionUtil.createConstant(estimates.get(stringValue));
			}

			Field<?> field;

			try {
				field = encoder.getField(stringValue);
			} catch(IllegalArgumentException iae){

				if(variables.containsKey(stringValue)){
					Expression expression = toPMML(variables.get(stringValue), variables, estimates, encoder);

					field = encoder.createDerivedField(stringValue, OpType.CONTINUOUS, DataType.DOUBLE, expression);
				} else

				{
					field = encoder.createDataField(stringValue, OpType.CONTINUOUS, DataType.DOUBLE);
				}
			}

			return new FieldRef(field);
		} else

		if(argumentValue instanceof RNumberVector){
			RNumberVector<?> numberVector = (RNumberVector<?>)argumentValue;

			return ExpressionUtil.createConstant(numberVector.asScalar());
		} else

		if(argumentValue instanceof RFunctionCall){
			RFunctionCall functionCall = (RFunctionCall)argumentValue;

			RString value = (RString)functionCall.getValue();
			Iterator<RExp> it = functionCall.argumentValues();

			switch(value.getValue()){
				case "(":
					return toPMML(it.next(), variables, estimates, encoder);
				case "+":
				case "-":
				case "*":
				case "/":
					return toBinaryExpression(value.getValue(), it, variables, estimates, encoder);
				case "^":
					return toBinaryExpression(PMMLFunctions.POW, it, variables, estimates, encoder);
				case "==":
					return ExpressionUtil.createApply(PMMLFunctions.IF,
						toBinaryExpression(PMMLFunctions.EQUAL, it, variables, estimates, encoder),
						ExpressionUtil.createConstant(1d), ExpressionUtil.createConstant(0d)
					);
				case "!=":
					return ExpressionUtil.createApply(PMMLFunctions.IF,
						toBinaryExpression(PMMLFunctions.NOTEQUAL, it, variables, estimates, encoder),
						ExpressionUtil.createConstant(1d), ExpressionUtil.createConstant(0d)
					);
				default:
					throw new IllegalArgumentException(value.getValue());
			}
		} else

		{
			throw new IllegalArgumentException();
		}
	}

	static
	private Apply toBinaryExpression(String function, Iterator<RExp> it, Map<String, RExp> variables, Map<String, Double> estimates, RExpEncoder encoder){
		return ExpressionUtil.createApply(function,
			toPMML(it.next(), variables, estimates, encoder),
			toPMML(it.next(), variables, estimates, encoder)
		);
	}

	static
	private Map<?, ?> parseList(RFunctionCall functionCall, Map<String, Double> estimates){

		if(!functionCall.hasValue("list")){
			throw new IllegalArgumentException();
		}

		Map<Object, Object> result = new LinkedHashMap<>();

		for(Iterator<RPair> it = functionCall.arguments(); it.hasNext(); ){
			RPair arg = it.next();

			RString tag = (RString)arg.getTag();
			RExp value = arg.getValue();

			if(value instanceof RString){
				RString string = (RString)value;

				result.put(tag.getValue(), estimates.get(string.getValue()));
			} else

			if(value instanceof RVector){
				RVector<?> vector = (RVector<?>)value;

				result.put(tag.getValue(), vector.asScalar());
			} else

			{
				throw new IllegalArgumentException();
			}
		}

		return result;
	}

	static
	private List<?> parseVector(RFunctionCall functionCall){

		if(!functionCall.hasValue("c")){
			throw new IllegalArgumentException();
		}

		List<Object> result = new ArrayList<>();

		for(Iterator<RExp> it = functionCall.argumentValues(); it.hasNext(); ){
			RVector<?> argValue = (RVector<?>)it.next();

			result.add(argValue.asScalar());
		}

		return result;
	}
}