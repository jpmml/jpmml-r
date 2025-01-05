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
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.collect.Iterables;
import org.dmg.pmml.Apply;
import org.dmg.pmml.DataField;
import org.dmg.pmml.DataType;
import org.dmg.pmml.DerivedField;
import org.dmg.pmml.Expression;
import org.dmg.pmml.Extension;
import org.dmg.pmml.Field;
import org.dmg.pmml.FieldRef;
import org.dmg.pmml.InlineTable;
import org.dmg.pmml.MathContext;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.MissingValueTreatmentMethod;
import org.dmg.pmml.Model;
import org.dmg.pmml.OpType;
import org.dmg.pmml.Output;
import org.dmg.pmml.OutputField;
import org.dmg.pmml.PMMLFunctions;
import org.dmg.pmml.ResultFeature;
import org.dmg.pmml.regression.RegressionModel;
import org.dmg.pmml.regression.RegressionTable;
import org.jpmml.converter.CategoricalLabel;
import org.jpmml.converter.ConstantFeature;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.ExpressionUtil;
import org.jpmml.converter.Feature;
import org.jpmml.converter.FieldNameUtil;
import org.jpmml.converter.InteractionFeature;
import org.jpmml.converter.MissingValueDecorator;
import org.jpmml.converter.ModelEncoder;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.PMMLUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.TypeUtil;
import org.jpmml.converter.ValueUtil;
import org.jpmml.converter.regression.RegressionModelUtil;
import org.jpmml.converter.transformations.ExpTransformation;

public class MaxLikConverter extends ModelConverter<RGenericVector> {

	private RFunctionCall settings = null;

	private Map<String, RExp> variables = null;

	private Map<?, RFunctionCall> utilityFunctions = null;

	private RFunctionCall nlNests = null;

	private Map<?, RFunctionCall> nlStructures = null;

	private Map<?, Number> lambdas = null;

	private Map<?, ?> nlTree = null;

	private Map<?, Feature> availabilityFeatures = null;

	private Map<?, Feature> utilityFunctionFeatures = null;

	private Map<?, Feature> expUtilityFunctionFeatures = null;


	public MaxLikConverter(RGenericVector maxLik){
		super(maxLik);
	}

	@Override
	public void encodeSchema(RExpEncoder encoder){
		parseApolloProbabilities();

		RGenericVector maxLik = getObject();

		RDoubleVector estimate = maxLik.getDoubleElement("estimate");
		RStringVector modelTypeList = maxLik.getStringElement("modelTypeList");

		RStringVector estimateNames = estimate.names();

		Map<String, Double> estimates = new LinkedHashMap<>();

		for(int i = 0; i < estimate.size(); i++){
			estimates.put(estimateNames.getDequotedValue(i), estimate.getValue(i));
		}

		String modelType = modelTypeList.getValue(0);

		RFunctionCall settings = this.settings;
		if(settings == null){
			throw new IllegalArgumentException("Invalid \'apollo_probabilities\' element. Missing model settings (variable \'" + modelType.toLowerCase() + "_settings\')");
		}

		Map<String, RExp> settingsMap = parseList(settings, (value) -> value);

		RFunctionCall alternatives = (RFunctionCall)settingsMap.get("alternatives");
		RFunctionCall availabilities = (RFunctionCall)settingsMap.get("avail");
		RString choiceVar = (RString)settingsMap.get("choiceVar");

		List<?> choices = (parseVector(alternatives).entrySet()).stream()
			.sorted((left, right) -> {
				return ((Comparable)left.getValue()).compareTo((Comparable)right.getValue());
			})
			.map(Map.Entry::getKey)
			.collect(Collectors.toList());

		Map<String, RExp> variables = this.variables;
		Map<?, RFunctionCall> utilityFunctions = this.utilityFunctions;

		if(utilityFunctions.isEmpty()){
			throw new IllegalArgumentException("Invalid \'apollo_probabilities\' element. Missing utility function set (variable \'V\')");
		} else

		if(!(new LinkedHashSet<>(choices)).equals(utilityFunctions.keySet())){
			throw new IllegalArgumentException("Invalid \'apollo_probabilities\' element. Invalid utility function set");
		}

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

						encoder.addDecorator(availabilityField, new MissingValueDecorator(MissingValueTreatmentMethod.AS_VALUE, 1));

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
		Map<Object, Feature> expUtilityFunctionFeatures = new LinkedHashMap<>();

		for(Object choice : choices){
			RFunctionCall functionCall = utilityFunctions.get(choice);

			Model model = encodeUtilityFunction(choice, functionCall, variables, estimates, encoder);

			encoder.addTransformer(model);

			Output output = model.getOutput();

			List<OutputField> outputFields = output.getOutputFields();
			for(OutputField outputField : outputFields){
				DerivedField derivedField = encoder.createDerivedField(model, outputField, true);

				Feature feature = new ContinuousFeature(encoder, derivedField);

				ResultFeature resultFeature = outputField.getResultFeature();
				switch(resultFeature){
					case PREDICTED_VALUE:
						{
							utilityFunctionFeatures.put(choice, feature);
						}
						break;
					case TRANSFORMED_VALUE:
						{
							expUtilityFunctionFeatures.put(choice, feature);
						}
						break;
					default:
						throw new IllegalArgumentException();
				}
			}

			outputFields.clear();
		}

		switch(modelType){
			case MaxLikConverter.TYPE_MNL:
				break;
			case MaxLikConverter.TYPE_NL:
				{
					RFunctionCall nlNests = this.nlNests;
					Map<?, RFunctionCall> nlStructures = this.nlStructures;

					if(nlNests == null){
						throw new IllegalArgumentException("Invalid \'apollo_probabilities\' element. Missing nest lambda parameters (variable \'nlNests\')");
					}

					Map<?, Number> lambdas = parseLambdas(nlNests, estimates);

					if(nlStructures.isEmpty()){
						throw new IllegalArgumentException("Invalid \'apollo_probabilities\' element. Missing nest structure (variable \'nlStructure\')");
					} // End if

					List<?> nestChoices = new ArrayList<>(nlStructures.keySet());

					// Handle lower levels first, higher levels last
					Collections.reverse(nestChoices);

					Map<Object, Object> nlTree = new LinkedHashMap<>();

					for(Object nestChoice : nestChoices){
						Number lambda = lambdas.get(nestChoice);

						RFunctionCall functionCall = nlStructures.get(nestChoice);

						Collection<?> childChoices = parseVector(functionCall).values();
						for(Object childChoice : childChoices){
							nlTree.put(childChoice, nestChoice);
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

							DerivedField derivedField = encoder.createDerivedField(FieldNameUtil.create("availability", nestChoice), OpType.CONTINUOUS, DataType.INTEGER, apply);

							Feature availabilityfeature = new ContinuousFeature(encoder, derivedField);

							availabilityFeatures.put(nestChoice, availabilityfeature);
						}

						// Utility function
						{
							Apply apply = ExpressionUtil.createApply(PMMLFunctions.SUM);

							for(Object childChoice : childChoices){
								Expression expression;

								if(lambda.doubleValue() != 1d){
									Feature feature = utilityFunctionFeatures.get(childChoice);

									expression = ExpressionUtil.createApply(PMMLFunctions.EXP,
										ExpressionUtil.createApply(PMMLFunctions.DIVIDE, feature.ref(), ExpressionUtil.createConstant(lambda))
									);
								} else

								{
									Feature expFeature = expUtilityFunctionFeatures.get(childChoice);

									expression = expFeature.ref();
								} // End if

								if(availabilities != null){
									Feature availabilityFeature = availabilityFeatures.get(childChoice);

									expression = ExpressionUtil.createApply(PMMLFunctions.MULTIPLY, availabilityFeature.ref(), expression);
								}

								apply.addExpressions(expression);
							}

							apply = ExpressionUtil.createApply(PMMLFunctions.LN, apply);

							if(lambda.doubleValue() != 1d){
								apply = ExpressionUtil.createApply(PMMLFunctions.MULTIPLY, apply, ExpressionUtil.createConstant(lambda));
							}

							DerivedField derivedField = encoder.createDerivedField(FieldNameUtil.create("utility", nestChoice), OpType.CONTINUOUS, DataType.DOUBLE, apply);

							Feature feature = new ContinuousFeature(encoder, derivedField);

							utilityFunctionFeatures.put(nestChoice, feature);

							Apply expApply = ExpressionUtil.createApply(PMMLFunctions.EXP, feature.ref());

							DerivedField expDerivedField = encoder.createDerivedField(FieldNameUtil.create("exp", feature), OpType.CONTINUOUS, DataType.DOUBLE, expApply);

							Feature expFeature = new ContinuousFeature(encoder, expDerivedField);

							expUtilityFunctionFeatures.put(nestChoice, expFeature);
						}
					}

					this.lambdas = lambdas;

					Extension extension = encodeNlStructures(nlTree);

					choiceField.addExtensions(extension);

					this.nlTree = nlTree;
				}
				break;
			default:
				throw new IllegalArgumentException(modelType);
		}

		this.availabilityFeatures = availabilityFeatures;

		this.utilityFunctionFeatures = utilityFunctionFeatures;
		this.expUtilityFunctionFeatures = expUtilityFunctionFeatures;
	}

	@Override
	public Model encodeModel(Schema schema){
		RGenericVector maxLik = getObject();

		RStringVector modelTypeList = maxLik.getStringElement("modelTypeList");

		String modelType = modelTypeList.getValue(0);

		ModelEncoder encoder = schema.getEncoder();
		CategoricalLabel categoricalLabel = (CategoricalLabel)schema.getLabel();
		List<? extends Feature> features = schema.getFeatures();

		Map<?, Feature> availabilityFeatures = this.availabilityFeatures;

		Map<?, Feature> utilityFunctionFeatures = this.utilityFunctionFeatures;
		Map<?, Feature> expUtilityFunctionFeatures = this.expUtilityFunctionFeatures;

		List<RegressionTable> regressionTables = new ArrayList<>();

		switch(modelType){
			case MaxLikConverter.TYPE_MNL:
				{
					for(int i = 0; i < categoricalLabel.size(); i++){
						Object choice = categoricalLabel.getValue(i);

						Feature feature = expUtilityFunctionFeatures.get(choice);

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
					Map<?, Number> lambdas = this.lambdas;

					Map<?, ?> nlTree = this.nlTree;

					for(int i = 0; i < categoricalLabel.size(); i++){
						Object choice = categoricalLabel.getValue(i);

						List<Expression> expressions = new ArrayList<>();

						for(Object currentChoice = choice, nextChoice = nlTree.get(currentChoice); nextChoice != null; currentChoice = nextChoice, nextChoice = nlTree.get(currentChoice)){
							Number lambda = lambdas.get(nextChoice);

							Feature currentFeature = expUtilityFunctionFeatures.get(currentChoice);
							Feature nextFeature = expUtilityFunctionFeatures.get(nextChoice);

							DerivedField derivedField = encoder.ensureDerivedField(FieldNameUtil.create("decisionFunction", currentChoice, nextChoice), OpType.CONTINUOUS, DataType.DOUBLE, () -> {
								// Can't divide by zero.
								// A division by integer zero raises an invalid result error.
								// However, a division by floating-point zero succeeds - the result is a (positive-) infinity.
								Expression expression = ExpressionUtil.createApply(PMMLFunctions.IF,
									ExpressionUtil.createApply(PMMLFunctions.EQUAL, nextFeature.ref(), ExpressionUtil.createConstant(0d)),
									ExpressionUtil.createConstant(0d),
									ExpressionUtil.createApply(PMMLFunctions.DIVIDE, currentFeature.ref(), nextFeature.ref())
								);

								if(lambda.doubleValue() != 1d){
									expression = ExpressionUtil.createApply(PMMLFunctions.POW, expression, ExpressionUtil.createConstant(1d / lambda.doubleValue()));
								}

								return expression;
							});

							expressions.add(new FieldRef(derivedField));
						}

						Expression expression;

						if(expressions.size() == 1){
							expression = Iterables.getOnlyElement(expressions);
						} else

						{
							Apply apply = ExpressionUtil.createApply(PMMLFunctions.PRODUCT);

							(apply.getExpressions()).addAll(expressions);

							expression = apply;
						}

						DerivedField derivedField = encoder.createDerivedField(FieldNameUtil.create("decisionFunction", choice), OpType.CONTINUOUS, DataType.DOUBLE, expression);

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

	private RegressionModel encodeUtilityFunction(Object choice, RFunctionCall functionCall, Map<String, RExp> variables, Map<String, Double> estimates, RExpEncoder encoder){
		List<Feature> features = new ArrayList<>();
		List<Number> coefficients = new ArrayList<>();

		encodeTerm(choice, functionCall, MaxLikConverter.SIGN_PLUS, variables, estimates, features, coefficients, encoder);

		RegressionModel regressionModel = new RegressionModel(MiningFunction.REGRESSION, ModelUtil.createMiningSchema(null), null)
			.setNormalizationMethod(RegressionModel.NormalizationMethod.NONE)
			.addRegressionTables(RegressionModelUtil.createRegressionTable(features, coefficients, null))
			.setOutput(ModelUtil.createPredictedOutput(FieldNameUtil.create("utility", choice), OpType.CONTINUOUS, DataType.DOUBLE, new ExpTransformation()));

		return regressionModel;
	}

	private void encodeTerm(Object choice, RExp rexp, int sign, Map<String, RExp> variables, Map<String, Double> estimates, List<Feature> features, List<Number> coefficients, RExpEncoder encoder){

		if(rexp instanceof RFunctionCall){
			RFunctionCall functionCall = (RFunctionCall)rexp;

			if(functionCall.hasValue("+")){
				Iterator<RExp> it = functionCall.argumentValues();

				encodeTerm(choice, it.next(), MaxLikConverter.SIGN_PLUS, variables, estimates, features, coefficients, encoder);
				encodeTerm(choice, it.next(), MaxLikConverter.SIGN_PLUS, variables, estimates, features, coefficients, encoder);

				return;
			} else

			if(functionCall.hasValue("-")){
				Iterator<RExp> it = functionCall.argumentValues();

				encodeTerm(choice, it.next(), MaxLikConverter.SIGN_PLUS, variables, estimates, features, coefficients, encoder);
				encodeTerm(choice, it.next(), MaxLikConverter.SIGN_MINUS, variables, estimates, features, coefficients, encoder);

				return;
			}
		} else

		if(rexp instanceof RString){
			RString string = (RString)rexp;

			if(estimates.containsKey(string.getValue())){
				Number beta = estimates.get(string.getValue());

				features.add(new ConstantFeature(encoder, beta));
				coefficients.add(sign);

				return;
			}
		}

		Feature feature;
		Number coefficient = null;

		if(rexp instanceof RFunctionCall){
			RFunctionCall functionCall = (RFunctionCall)rexp;

			if(functionCall.hasValue("*")){
				Iterator<RExp> it = functionCall.argumentValues();

				RExp firstArgValue = it.next();
				RExp secondArgValue = it.next();

				if(firstArgValue instanceof RString){
					RString string = (RString)firstArgValue;

					if(estimates.containsKey(string.getValue())){
						coefficient = estimates.get(string.getValue());

						rexp = secondArgValue;
					}
				}
			}
		}

		Expression expression = toPMML(rexp, variables, estimates, encoder);

		if(expression instanceof FieldRef){
			FieldRef fieldRef = (FieldRef)expression;

			Field<?> field = encoder.getField(fieldRef.requireField());

			feature = new ContinuousFeature(encoder, field);
		} else

		{
			DerivedField derivedField = encoder.createDerivedField(FieldNameUtil.create("term", choice, features.size()), OpType.CONTINUOUS, DataType.DOUBLE, expression);

			feature = new ContinuousFeature(encoder, derivedField);
		} // End if

		if(coefficient != null){
			coefficient = ValueUtil.multiply(MathContext.DOUBLE, sign, coefficient);
		} else

		{
			coefficient = sign;
		}

		features.add(feature);
		coefficients.add(coefficient);
	}

	private Extension encodeNlStructures(Map<?, ?> nlTree){
		Map<String, List<Object>> data = new LinkedHashMap<>();

		List<Object> parents = new ArrayList<>();
		List<Object> children = new ArrayList<>();

		(nlTree.entrySet()).stream()
			.forEach(entry -> {
				parents.add(entry.getValue());
				children.add(entry.getKey());
			});

		if(!parents.isEmpty() && Objects.equals(parents.get(parents.size() - 1), "root")){
			Collections.reverse(parents);
			Collections.reverse(children);
		}

		data.put("parent", parents);
		data.put("child", children);

		InlineTable inlineTable = PMMLUtil.createInlineTable(data);

		return PMMLUtil.createExtension("nlStructures", inlineTable);
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

				if(functionCall.hasValue("=") || functionCall.hasValue("<-")){
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

		this.settings = settings;

		this.variables = variables;
		this.utilityFunctions = utilityFunctions;

		this.nlNests = nlNests;
		this.nlStructures = nlStructures;
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
					return toComparisonExpression(PMMLFunctions.EQUAL, it, variables, estimates, encoder);
				case "!=":
					return toComparisonExpression(PMMLFunctions.NOTEQUAL, it, variables, estimates, encoder);
				case "<":
					return toComparisonExpression(PMMLFunctions.LESSTHAN, it, variables, estimates, encoder);
				case "<=":
					return toComparisonExpression(PMMLFunctions.LESSOREQUAL, it, variables, estimates, encoder);
				case ">":
					return toComparisonExpression(PMMLFunctions.GREATERTHAN, it, variables, estimates, encoder);
				case ">=":
					return toComparisonExpression(PMMLFunctions.GREATEROREQUAL, it, variables, estimates, encoder);
				case "ifelse":
					return ExpressionUtil.createApply(PMMLFunctions.IF,
						toPMML(it.next(), variables, estimates, encoder),
						toPMML(it.next(), variables, estimates, encoder),
						toPMML(it.next(), variables, estimates, encoder)
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
	private Apply toComparisonExpression(String function, Iterator<RExp> it, Map<String, RExp> variables, Map<String, Double> estimates, RExpEncoder encoder){
		return ExpressionUtil.createApply(PMMLFunctions.IF,
			toBinaryExpression(function, it, variables, estimates, encoder),
			ExpressionUtil.createConstant(1d), ExpressionUtil.createConstant(0d)
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
	private Map<?, ?> parseVector(RFunctionCall functionCall){

		if(!functionCall.hasValue("c")){
			throw new IllegalArgumentException();
		}

		Map<Object, Object> result = new LinkedHashMap<>();

		for(Iterator<RPair> it = functionCall.arguments(); it.hasNext(); ){
			RPair arg = it.next();

			RString tag = (RString)arg.getTag();
			RExp value = arg.getValue();

			if(value instanceof RVector){
				RVector<?> vector = (RVector<?>)value;

				if(tag != null){
					result.put(tag.getValue(), vector.asScalar());
				} else

				{
					result.put((result.size() + 1), vector.asScalar());
				}
			} else

			{
				throw new IllegalArgumentException();
			}
		}

		return result;
	}

	private static final String TYPE_MNL = "MNL";
	private static final String TYPE_NL = "NL";

	private static final int SIGN_MINUS = -1;
	private static final int SIGN_PLUS = 1;
}