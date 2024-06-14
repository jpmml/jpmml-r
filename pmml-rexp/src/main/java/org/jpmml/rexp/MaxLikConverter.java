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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

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
import org.jpmml.converter.InteractionFeature;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.PMMLEncoder;
import org.jpmml.converter.Schema;
import org.jpmml.converter.TypeUtil;
import org.jpmml.converter.regression.RegressionModelUtil;

public class MaxLikConverter extends ModelConverter<RGenericVector> {

	private RFunctionCall settings = null;

	private Map<String, RExp> variables = null;

	private Map<?, RFunctionCall> utilityFunctions = null;

	private RFunctionCall nlNests = null;

	private Map<?, RFunctionCall> nlStructures = null;

	private Map<String, Double> estimates = null;

	private Map<?, Feature> availabilityFeatures = null;

	private Map<?, Feature> utilityFunctionFeatures = null;


	public MaxLikConverter(RGenericVector maxLik){
		super(maxLik);
	}

	@Override
	public void encodeSchema(RExpEncoder encoder){
		parseApolloProbabilities();
		parseEstimate();

		RGenericVector maxLik = getObject();

		RStringVector modelTypeList = maxLik.getStringElement("modelTypeList");

		RFunctionCall settings = this.settings;

		Map<String, RExp> settingsMap = parseList(settings, (value) -> value);

		RFunctionCall alternatives = (RFunctionCall)settingsMap.get("alternatives");
		RFunctionCall availabilities = (RFunctionCall)settingsMap.get("avail");
		RString choiceVar = (RString)settingsMap.get("choiceVar");

		Map<String, RExp> variables = this.variables;
		Map<?, RFunctionCall> utilityFunctions = this.utilityFunctions;

		if(utilityFunctions.isEmpty()){
			throw new IllegalArgumentException();
		}

		Map<String, Double> estimates = this.estimates;

		List<?> choices = new ArrayList<>(utilityFunctions.keySet());

		DataField choiceField = encoder.createDataField(choiceVar.getValue(), OpType.CATEGORICAL, TypeUtil.getDataType(choices, DataType.STRING), choices);

		encoder.setLabel(choiceField);

		Map<Object, Feature> availabilityFeatures = null;

		if(availabilities != null){
			Function<RExp, Feature> function = new Function<RExp, Feature>(){

				@Override
				public Feature apply(RExp rexp){

					if(rexp instanceof RString){
						RString string = (RString)rexp;

						DataField availabilityField = encoder.createDataField(string.getValue(), OpType.CONTINUOUS, DataType.INTEGER, Arrays.asList(0, 1));

						return new ContinuousFeature(encoder, availabilityField);
					} else

					{
						throw new IllegalArgumentException();
					}
				}
			};

			availabilityFeatures = (Map)parseList(availabilities, function);
		}

		Map<Object, Feature> utilityFunctionFeatures = new LinkedHashMap<>();

		for(Object choice : choices){
			RFunctionCall functionCall = utilityFunctions.get(choice);

			Expression expression = toPMML(functionCall, variables, estimates, encoder);

			DerivedField derivedField = encoder.createDerivedField(FieldNameUtil.create("utility", choice), OpType.CONTINUOUS, DataType.DOUBLE, expression);

			Feature feature = new ContinuousFeature(encoder, derivedField);

			utilityFunctionFeatures.put(choice, feature);

			// XXX
			encoder.addFeature(feature);
		}

		String modelType = modelTypeList.getValue(0);
		switch(modelType){
			case MaxLikConverter.TYPE_MNL:
				break;
			case MaxLikConverter.TYPE_NL:
				{
					RFunctionCall nlNests = this.nlNests;
					Map<?, RFunctionCall> nlStructures = this.nlStructures;

					if(nlNests == null){
						throw new IllegalArgumentException();
					}

					Map<?, Number> lambdas = parseLambdas(nlNests, estimates);

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
						Number lambda = lambdas.get(choice);

						RFunctionCall functionCall = nlStructures.get(choice);

						List<?> childChoices = parseVector(functionCall);
						if(childChoices.isEmpty()){
							throw new IllegalArgumentException();
						}

						// Availability
						if(availabilities != null){
							Apply apply = ExpressionUtil.createApply(PMMLFunctions.SUM);

							for(Object childChoice : childChoices){
								Feature availabilityFeature = availabilityFeatures.get(childChoice);

								apply.addExpressions(availabilityFeature.ref());
							}

							apply = ExpressionUtil.createApply(PMMLFunctions.IF,
								ExpressionUtil.createApply(PMMLFunctions.GREATERTHAN, apply, ExpressionUtil.createConstant(0)),
								ExpressionUtil.createConstant(1), ExpressionUtil.createConstant(0)
							);

							DerivedField derivedField = encoder.createDerivedField(FieldNameUtil.create("availability", choice), OpType.CONTINUOUS, DataType.INTEGER, apply);

							Feature availabilityfeature = new ContinuousFeature(encoder, derivedField);

							availabilityFeatures.put(choice, availabilityfeature);
						}

						// Utility function
						{
							Apply apply = ExpressionUtil.createApply(PMMLFunctions.SUM);

							for(Object childChoice : childChoices){
								Feature feature = utilityFunctionFeatures.get(childChoice);

								Apply choiceApply;

								if(lambda.doubleValue() != 1d){
									choiceApply = ExpressionUtil.createApply(PMMLFunctions.EXP,
										ExpressionUtil.createApply(PMMLFunctions.DIVIDE, feature.ref(), ExpressionUtil.createConstant(lambda))
									);
								} else

								{
									choiceApply = ExpressionUtil.createApply(PMMLFunctions.EXP,
										feature.ref()
									);
								} // End if

								if(availabilities != null){
									Feature availabilityFeature = availabilityFeatures.get(childChoice);

									choiceApply = ExpressionUtil.createApply(PMMLFunctions.MULTIPLY, availabilityFeature.ref(), choiceApply);
								}

								apply.addExpressions(choiceApply);
							}

							apply = ExpressionUtil.createApply(PMMLFunctions.LN, apply);

							if(lambda.doubleValue() != 1d){
								apply = ExpressionUtil.createApply(PMMLFunctions.MULTIPLY, apply, ExpressionUtil.createConstant(lambda));
							}

							DerivedField derivedField = encoder.createDerivedField(FieldNameUtil.create("utility", choice), OpType.CONTINUOUS, DataType.DOUBLE, apply);

							Feature feature = new ContinuousFeature(encoder, derivedField);

							utilityFunctionFeatures.put(choice, feature);
						}
					}
				}
				break;
			default:
				throw new IllegalArgumentException();
		}

		this.availabilityFeatures = availabilityFeatures;
		this.utilityFunctionFeatures = utilityFunctionFeatures;
	}

	@Override
	public Model encodeModel(Schema schema){
		RGenericVector maxLik = getObject();

		RStringVector modelTypeList = maxLik.getStringElement("modelTypeList");

		PMMLEncoder encoder = schema.getEncoder();
		CategoricalLabel categoricalLabel = (CategoricalLabel)schema.getLabel();
		List<? extends Feature> features = schema.getFeatures();

		Map<?, Feature> availabilityFeatures = this.availabilityFeatures;
		Map<?, Feature> utilityFunctionFeatures = this.utilityFunctionFeatures;

		List<RegressionTable> regressionTables = new ArrayList<>();

		String modelType = modelTypeList.getValue(0);
		switch(modelType){
			case MaxLikConverter.TYPE_MNL:
				{
					for(int i = 0; i < categoricalLabel.size(); i++){
						Object choice = categoricalLabel.getValue(i);

						Feature feature = toExpFeature(utilityFunctionFeatures.get(choice), encoder);

						if(availabilityFeatures != null && !availabilityFeatures.isEmpty()){
							Feature availabilityFeature = availabilityFeatures.get(choice);

							feature = new InteractionFeature(encoder, FieldNameUtil.create("interaction", availabilityFeature, feature), DataType.DOUBLE, Arrays.asList(availabilityFeature, feature));
						}

						RegressionTable regressionTable = RegressionModelUtil.createRegressionTable(Collections.singletonList(feature), Collections.singletonList(1d), null)
							.setTargetCategory(choice);

						regressionTables.add(regressionTable);
					}
				}
				break;
			case MaxLikConverter.TYPE_NL:
				{
					RFunctionCall nlNests = this.nlNests;
					Map<?, RFunctionCall> nlStructures = this.nlStructures;

					Map<String, Double> estimates = this.estimates;

					Map<?, Number> lambdas = parseLambdas(nlNests, estimates);

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
							Number lambda = lambdas.get(nextChoice);

							Feature currentFeature = toExpFeature(utilityFunctionFeatures.get(currentChoice), encoder);
							Feature nextFeature = toExpFeature(utilityFunctionFeatures.get(nextChoice), encoder);

							DerivedField derivedField = encoder.ensureDerivedField(FieldNameUtil.create("term", currentChoice, nextChoice), OpType.CONTINUOUS, DataType.DOUBLE, () -> {
								// Can't divide by zero.
								// A division by integer zero raises an invalid result error.
								// However, a division by floating-point zero succeeds - the result is a (positive-) infinity.
								Apply choiceApply = ExpressionUtil.createApply(PMMLFunctions.IF,
									ExpressionUtil.createApply(PMMLFunctions.EQUAL, nextFeature.ref(), ExpressionUtil.createConstant(0d)),
									ExpressionUtil.createConstant(0d),
									ExpressionUtil.createApply(PMMLFunctions.DIVIDE, currentFeature.ref(), nextFeature.ref())
								);

								if(lambda.doubleValue() != 1d){
									choiceApply = ExpressionUtil.createApply(PMMLFunctions.POW, choiceApply, ExpressionUtil.createConstant(1d / lambda.doubleValue()));
								}

								return choiceApply;
							});

							apply.addExpressions(new FieldRef(derivedField));
						}

						DerivedField derivedField = encoder.createDerivedField(FieldNameUtil.create("term", choice), OpType.CONTINUOUS, DataType.DOUBLE, apply);

						Feature feature = new ContinuousFeature(encoder, derivedField);

						if(availabilityFeatures != null && !availabilityFeatures.isEmpty()){
							Feature availabilityFeature = availabilityFeatures.get(choice);

							feature = new InteractionFeature(encoder, FieldNameUtil.create("interaction", availabilityFeature, feature), DataType.DOUBLE, Arrays.asList(availabilityFeature, feature));
						}

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

		RFunctionCall settings = null;

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

						if(matchVariable(firstArgValue, "mnl_settings") || matchVariable(firstArgValue, "nl_settings")){
							settings = (RFunctionCall)secondArgValue;

							continue;
						} else

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

		if(settings == null){
			throw new IllegalArgumentException();
		} // End if

		if(!Collections.disjoint(utilityFunctions.keySet(), nlStructures.keySet())){
			throw new IllegalArgumentException();
		}

		this.settings = settings;

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
	private Map<?, Number> parseLambdas(RFunctionCall functionCall, Map<String, Double> estimates){
		Function<RExp, Number> function = new Function<RExp, Number>(){

			@Override
			public Number apply(RExp rexp){

				if(rexp instanceof RString){
					RString string = (RString)rexp;

					return estimates.get(string.getValue());
				} else

				if(rexp instanceof RNumberVector){
					RNumberVector<?> numberVector = (RNumberVector<?>)rexp;

					return numberVector.asScalar();
				} else

				{
					throw new IllegalArgumentException();
				}
			}
		};

		return parseList(functionCall, function);
	}

	static
	private <V> Map<String, V> parseList(RFunctionCall functionCall, Function<RExp, V> function){

		if(!functionCall.hasValue("list")){
			throw new IllegalArgumentException();
		}

		Map<String, V> result = new LinkedHashMap<>();

		for(Iterator<RPair> it = functionCall.arguments(); it.hasNext(); ){
			RPair arg = it.next();

			RString tag = (RString)arg.getTag();
			RExp value = arg.getValue();

			result.put(tag.getValue(), function.apply(value));
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

	private static final String TYPE_MNL = "MNL";
	private static final String TYPE_NL = "NL";
}