/*
 * Copyright (c) 2016 Villu Ruusmann
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.dmg.pmml.Apply;
import org.dmg.pmml.DataField;
import org.dmg.pmml.DataType;
import org.dmg.pmml.DerivedField;
import org.dmg.pmml.Discretize;
import org.dmg.pmml.DiscretizeBin;
import org.dmg.pmml.Expression;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.FieldRef;
import org.dmg.pmml.Interval;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.Model;
import org.dmg.pmml.OpType;
import org.dmg.pmml.TypeDefinitionField;
import org.dmg.pmml.Value;
import org.dmg.pmml.regression.RegressionModel;
import org.dmg.pmml.regression.RegressionTable;
import org.jpmml.converter.BinaryFeature;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.ConvertibleBinaryFeature;
import org.jpmml.converter.Feature;
import org.jpmml.converter.InteractionFeature;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.PMMLUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.WildcardFeature;
import org.jpmml.converter.regression.RegressionModelUtil;
import org.jpmml.model.visitors.FieldReferenceFinder;

public class LMConverter extends ModelConverter<RGenericVector> {

	private BiMap<FieldName, BinaryFeature> binaryFeatures = HashBiMap.create();


	public LMConverter(RGenericVector lm){
		super(lm);
	}

	@Override
	public void encodeFeatures(FeatureMapper featureMapper){
		RGenericVector lm = getObject();

		RDoubleVector coefficients = (RDoubleVector)lm.getValue("coefficients");
		RGenericVector xlevels = (RGenericVector)lm.getValue("xlevels", true);
		RGenericVector model = (RGenericVector)lm.getValue("model");
		RGenericVector data = (RGenericVector)lm.getValue("data", true);

		RExp terms = model.getAttributeValue("terms");

		RIntegerVector factors = (RIntegerVector)terms.getAttributeValue("factors");
		RIntegerVector response = (RIntegerVector)terms.getAttributeValue("response");
		RStringVector dataClasses = (RStringVector)terms.getAttributeValue("dataClasses");

		RGenericVector dimnames = (RGenericVector)factors.getAttributeValue("dimnames");

		RStringVector variableRows = (RStringVector)dimnames.getValue(0);
		RStringVector termColumns = (RStringVector)dimnames.getValue(1);

		List<TypeDefinitionField> fields = new ArrayList<>();

		Set<FieldName> expressionFieldNames = new LinkedHashSet<>();

		fields:
		for(int i = 0; i < variableRows.size(); i++){
			String variable = variableRows.getValue(i);

			FieldName name = FieldName.create(variable);
			DataType dataType = RExpUtil.getDataType(dataClasses.getValue(variable));

			if(variable.startsWith("I(")){
				FunctionExpression functionExpression = (FunctionExpression)ExpressionTranslator.translateExpression(variable);

				List<FunctionExpression.Argument> arguments = functionExpression.getArguments();
				if(arguments.size() != 1){
					throw new IllegalArgumentException();
				}

				Expression expression = functionExpression.getExpression(0);

				FieldReferenceFinder fieldReferenceFinder = new FieldReferenceFinder();
				fieldReferenceFinder.applyTo(expression);

				expressionFieldNames.addAll(fieldReferenceFinder.getFieldNames());

				DerivedField derivedField =  featureMapper.createDerivedField(name, OpType.CONTINUOUS, dataType, expression);

				fields.add(derivedField);

				continue fields;
			} // End if

			if(xlevels != null && xlevels.hasValue(variable)){
				RStringVector levels = (RStringVector)xlevels.getValue(variable);

				if(variable.startsWith("cut(")){
					FunctionExpression functionExpression = (FunctionExpression)ExpressionTranslator.translateExpression(variable);

					Expression expression = functionExpression.getExpression(0);

					FieldReferenceFinder fieldReferenceFinder = new FieldReferenceFinder();
					fieldReferenceFinder.applyTo(expression);

					expressionFieldNames.addAll(fieldReferenceFinder.getFieldNames());

					FieldName fieldName;

					if(expression instanceof FieldRef){
						FieldRef fieldRef = (FieldRef)expression;

						fieldName = fieldRef.getField();
					} else

					if(expression instanceof Apply){
						Apply apply = (Apply)expression;

						int begin = "cut(".length();
						int end = variable.indexOf(", breaks = ", begin); // XXX
						if(end < 0){
							throw new IllegalArgumentException(variable);
						}

						String function = variable.substring(begin, end).trim();

						DerivedField derivedField = featureMapper.createDerivedField(FieldName.create(function), OpType.CONTINUOUS, DataType.DOUBLE, apply);

						fieldName = derivedField.getName();
					} else

					{
						throw new IllegalArgumentException();
					}

					Discretize discretize = new Discretize(fieldName);

					List<String> values = levels.getValues();
					for(String value : values){
						Interval interval = ExpressionTranslator.translateInterval(value);

						DiscretizeBin discretizeBin = new DiscretizeBin(value, interval);

						discretize.addDiscretizeBins(discretizeBin);
					}

					DerivedField derivedField = featureMapper.createDerivedField(name, OpType.CATEGORICAL, dataType, discretize);

					registerBinaryFields(derivedField, values, featureMapper);

					fields.add(derivedField);
				} else

				{
					DataField dataField = featureMapper.createDataField(name, OpType.CATEGORICAL, dataType);

					registerBinaryFields(dataField, levels.getValues(), featureMapper);

					fields.add(dataField);
				}
			} else

			if((DataType.BOOLEAN).equals(dataType)){
				DataField dataField = featureMapper.createDataField(name, OpType.CATEGORICAL, dataType);

				registerBinaryFields(dataField, Arrays.asList("TRUE", "FALSE"), Arrays.asList("true", "false"), featureMapper);

				fields.add(dataField);
			} else

			{
				DataField dataField = featureMapper.createDataField(name, OpType.CONTINUOUS, dataType);

				fields.add(dataField);
			}
		}

		for(FieldName expressionFieldName : expressionFieldNames){
			DataField dataField = featureMapper.getDataField(expressionFieldName);

			if(dataField == null){
				DataType dataType = DataType.DOUBLE;

				if(data != null){
					RVector<?> column = (RVector<?>)data.getValue(expressionFieldName.getValue());

					dataType = column.getDataType();
				}

				dataField = featureMapper.createDataField(expressionFieldName, OpType.CONTINUOUS, dataType);
			}
		}

		// Dependent variable
		int responseIndex = response.asScalar();
		if(responseIndex != 0){
			DataField dataField = (DataField)fields.get(responseIndex - 1);

			Feature feature = new WildcardFeature(dataField);

			featureMapper.append(feature);
		} else

		{
			throw new IllegalArgumentException();
		}

		// Independent variables
		RStringVector coefficientNames = coefficients.names();
		for(int i = 0; i < coefficientNames.size(); i++){
			String coefficientName = coefficientNames.getValue(i);

			if((LMConverter.INTERCEPT).equals(coefficientName)){
				continue;
			}

			FieldName name = FieldName.create(coefficientName);

			Feature feature;

			String[] variables = coefficientName.split(":");
			if(variables.length == 1){
				feature = resolveFeature(name, featureMapper);
			} else

			{
				List<Feature> variableFeatures = new ArrayList<>();

				for(String variable : variables){
					Feature variableFeature = resolveFeature(FieldName.create(variable), featureMapper);

					variableFeatures.add(variableFeature);
				}

				feature = new InteractionFeature(name, DataType.DOUBLE, variableFeatures);
			}

			featureMapper.append(name, feature);
		}
	}

	@Override
	public Model encodeModel(Schema schema){
		RGenericVector lm = getObject();

		RDoubleVector coefficients = (RDoubleVector)lm.getValue("coefficients");

		Double intercept = coefficients.getValue(LMConverter.INTERCEPT, true);

		List<Feature> features = schema.getFeatures();

		if(coefficients.size() != (features.size() + (intercept != null ? 1 : 0))){
			throw new IllegalArgumentException();
		}

		List<Double> featureCoefficients = prepareFeatureCoefficients(features, coefficients);

		RegressionTable regressionTable = RegressionModelUtil.createRegressionTable(features, intercept, featureCoefficients);

		RegressionModel regressionModel = new RegressionModel(MiningFunction.REGRESSION, ModelUtil.createMiningSchema(schema), null)
			.addRegressionTables(regressionTable);

		return regressionModel;
	}

	public List<Double> prepareFeatureCoefficients(List<Feature> features, RDoubleVector coefficients){
		List<Double> result = new ArrayList<>();

		BiMap<BinaryFeature, FieldName> inverseBinaryFeatures = this.binaryFeatures.inverse();

		for(Feature feature : features){
			FieldName name = feature.getName();

			if(feature instanceof BinaryFeature){
				BinaryFeature binaryFeature = (BinaryFeature)feature;

				name = inverseBinaryFeatures.get(binaryFeature);
			}

			double coefficient = coefficients.getValue(name.getValue());

			result.add(coefficient);
		}

		return result;
	}

	private Feature resolveFeature(FieldName name, FeatureMapper featureMapper){
		Feature feature = this.binaryFeatures.get(name);

		if(feature == null){
			TypeDefinitionField field = featureMapper.getField(name);

			feature = new ContinuousFeature(field);
		}

		return feature;
	}

	private void registerBinaryFields(TypeDefinitionField field, List<String> categories, FeatureMapper featureMapper){
		registerBinaryFields(field, categories, categories, featureMapper);
	}

	private void registerBinaryFields(TypeDefinitionField field, List<String> categoryNames, List<String> categoryValues, FeatureMapper featureMapper){
		FieldName name = field.getName();

		if(categoryNames.size() != categoryValues.size()){
			throw new IllegalArgumentException();
		}

		for(int i = 0; i < categoryNames.size(); i++){
			String categoryName = categoryNames.get(i);
			String categoryValue = categoryValues.get(i);

			BinaryFeature binaryFeature = new ConvertibleBinaryFeature(featureMapper, field, categoryValue);

			this.binaryFeatures.put(FieldName.create(name.getValue() + categoryName), binaryFeature);
		}

		if(categoryValues.size() > 0 && (field instanceof DataField)){
			List<Value> values = field.getValues();

			values.addAll(PMMLUtil.createValues(categoryValues));
		}
	}

	public static final String INTERCEPT = "(Intercept)";
}