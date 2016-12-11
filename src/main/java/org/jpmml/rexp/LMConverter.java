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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import org.dmg.pmml.Apply;
import org.dmg.pmml.Constant;
import org.dmg.pmml.DataField;
import org.dmg.pmml.DataType;
import org.dmg.pmml.DerivedField;
import org.dmg.pmml.Discretize;
import org.dmg.pmml.DiscretizeBin;
import org.dmg.pmml.Expression;
import org.dmg.pmml.Extension;
import org.dmg.pmml.FieldColumnPair;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.FieldRef;
import org.dmg.pmml.InlineTable;
import org.dmg.pmml.Interval;
import org.dmg.pmml.MapValues;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.Model;
import org.dmg.pmml.OpType;
import org.dmg.pmml.Row;
import org.dmg.pmml.TypeDefinitionField;
import org.dmg.pmml.Value;
import org.dmg.pmml.regression.RegressionModel;
import org.dmg.pmml.regression.RegressionTable;
import org.jpmml.converter.BinaryFeature;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.ConvertibleBinaryFeature;
import org.jpmml.converter.DOMUtil;
import org.jpmml.converter.Feature;
import org.jpmml.converter.InteractionFeature;
import org.jpmml.converter.ModelUtil;
import org.jpmml.converter.PMMLUtil;
import org.jpmml.converter.Schema;
import org.jpmml.converter.WildcardFeature;
import org.jpmml.converter.regression.RegressionModelUtil;

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

		Map<FieldName, List<String>> fieldCategories = new LinkedHashMap<>();

		fields:
		for(int i = 0; i < variableRows.size(); i++){
			String variable = variableRows.getValue(i);

			FieldName name = FieldName.create(variable);
			DataType dataType = RExpUtil.getDataType(dataClasses.getValue(variable));

			if(variable.startsWith("I(")){
				FunctionExpression functionExpression = (FunctionExpression)ExpressionTranslator.translateExpression(variable);

				FunctionExpression.Argument argument = functionExpression.getArgument(0);

				expressionFieldNames.addAll(argument.getFieldNames());

				Expression expression = argument.getExpression();

				DerivedField derivedField =  featureMapper.createDerivedField(name, OpType.CONTINUOUS, dataType, expression)
					.addExtensions(createExtension(variable));

				fields.add(derivedField);

				featureMapper.renameField(name, formatFunction("I", argument));

				continue fields;
			} // End if

			if(xlevels != null && xlevels.hasValue(variable)){
				RStringVector levels = (RStringVector)xlevels.getValue(variable);

				List<String> values = levels.getValues();

				if(variable.startsWith("cut(")){
					FunctionExpression cutExpression = (FunctionExpression)ExpressionTranslator.translateExpression(variable);

					FunctionExpression.Argument xArgument = cutExpression.getArgument(0);

					expressionFieldNames.addAll(xArgument.getFieldNames());

					FieldName fieldName = prepareInputField(xArgument, OpType.CATEGORICAL, dataType, featureMapper);

					Discretize discretize = new Discretize(fieldName);

					for(String value : values){
						Interval interval = ExpressionTranslator.translateInterval(value);

						DiscretizeBin discretizeBin = new DiscretizeBin(value, interval);

						discretize.addDiscretizeBins(discretizeBin);
					}

					DerivedField derivedField = featureMapper.createDerivedField(name, OpType.CATEGORICAL, dataType, discretize)
						.addExtensions(createExtension(variable));

					registerBinaryFields(derivedField, values, featureMapper);

					featureMapper.renameField(name, formatFunction("cut", xArgument));

					fields.add(derivedField);
				} else

				if(variable.startsWith("mapvalues(")){
					FunctionExpression mapValuesExpression = (FunctionExpression)ExpressionTranslator.translateExpression(variable);

					FunctionExpression.Argument xArgument = mapValuesExpression.getArgument("x", 0);

					expressionFieldNames.addAll(xArgument.getFieldNames());

					FieldName fieldName = prepareInputField(xArgument, OpType.CATEGORICAL, dataType, featureMapper);

					FunctionExpression.Argument fromArgument = mapValuesExpression.getArgument("from", 1);
					FunctionExpression.Argument toArgument = mapValuesExpression.getArgument("to", 2);

					Map<String, String> mapping = parseMapValues(fromArgument, toArgument);

					MapValues mapValues = createMapValues(fieldName, mapping, values);

					List<String> categories = new ArrayList<>(mapping.keySet());

					fieldCategories.put(fieldName, categories);

					DerivedField derivedField = featureMapper.createDerivedField(name, OpType.CATEGORICAL, dataType, mapValues)
						.addExtensions(createExtension(variable));

					registerBinaryFields(derivedField, values, featureMapper);

					featureMapper.renameField(name, formatFunction("mapvalues", xArgument));

					fields.add(derivedField);
				} else

				if(variable.startsWith("revalue(")){
					FunctionExpression revalueExpression = (FunctionExpression)ExpressionTranslator.translateExpression(variable);

					FunctionExpression.Argument xArgument = revalueExpression.getArgument("x", 0);

					expressionFieldNames.addAll(xArgument.getFieldNames());

					FieldName fieldName = prepareInputField(xArgument, OpType.CATEGORICAL, dataType, featureMapper);

					FunctionExpression.Argument replaceArgument = revalueExpression.getArgument("replace", 1);

					Map<String, String> mapping = parseRevalue(replaceArgument);

					MapValues mapValues = createMapValues(fieldName, mapping, values);

					List<String> categories = new ArrayList<>(mapping.keySet());

					fieldCategories.put(fieldName, categories);

					DerivedField derivedField = featureMapper.createDerivedField(name, OpType.CATEGORICAL, dataType, mapValues)
						.addExtensions(createExtension(variable));

					registerBinaryFields(derivedField, values, featureMapper);

					featureMapper.renameField(name, formatFunction("revalue", xArgument));

					fields.add(derivedField);
				} else

				{
					DataField dataField = featureMapper.createDataField(name, OpType.CATEGORICAL, dataType);

					registerBinaryFields(dataField, values, featureMapper);

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
				OpType opType = OpType.CONTINUOUS;
				DataType dataType = DataType.DOUBLE;

				List<String> categories = fieldCategories.get(expressionFieldName);

				if(categories != null && categories.size() > 0){
					opType = OpType.CATEGORICAL;
				} // End if

				if(data != null){
					RVector<?> column = (RVector<?>)data.getValue(expressionFieldName.getValue());

					dataType = column.getDataType();
				}

				dataField = featureMapper.createDataField(expressionFieldName, opType, dataType);

				if(categories != null && categories.size() > 0){
					List<Value> values = dataField.getValues();

					values.addAll(PMMLUtil.createValues(categories));
				}
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

		List<Double> featureCoefficients = getFeatureCoefficients(features, coefficients);

		RegressionTable regressionTable = RegressionModelUtil.createRegressionTable(features, intercept, featureCoefficients);

		RegressionModel regressionModel = new RegressionModel(MiningFunction.REGRESSION, ModelUtil.createMiningSchema(schema), null)
			.addRegressionTables(regressionTable);

		return regressionModel;
	}

	public List<Double> getFeatureCoefficients(List<Feature> features, RDoubleVector coefficients){
		List<Double> result = new ArrayList<>();

		for(Feature feature : features){
			Double coefficient = getFeatureCoefficient(feature, coefficients);

			result.add(coefficient);
		}

		return result;
	}

	public Double getFeatureCoefficient(Feature feature, RDoubleVector coefficients){
		FieldName name = feature.getName();

		if(feature instanceof BinaryFeature){
			BinaryFeature binaryFeature = (BinaryFeature)feature;

			BiMap<BinaryFeature, FieldName> inverseBinaryFeatures = this.binaryFeatures.inverse();

			name = inverseBinaryFeatures.get(binaryFeature);
		}

		return coefficients.getValue(name.getValue());
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

		if(categoryValues.size() > 0){

			if(field instanceof DataField){
				List<Value> values = field.getValues();

				values.addAll(PMMLUtil.createValues(categoryValues));
			}
		}
	}

	static
	private FieldName formatFunction(String function, FunctionExpression.Argument argument){
		String value = (argument.formatExpression()).trim();

		return FieldName.create(function != null ? (function + "(" + value + ")") : value);
	}

	static
	private FieldName prepareInputField(FunctionExpression.Argument argument, OpType opType, DataType dataType, FeatureMapper featureMapper){
		Expression expression = argument.getExpression();

		if(expression instanceof FieldRef){
			FieldRef fieldRef = (FieldRef)expression;

			return fieldRef.getField();
		} else

		if(expression instanceof Apply){
			Apply apply = (Apply)expression;

			DerivedField derivedField = featureMapper.createDerivedField(formatFunction(null, argument), opType, dataType, apply)
				.addExtensions(createExtension((argument.formatExpression()).trim()));

			return derivedField.getName();
		} else

		{
			throw new IllegalArgumentException();
		}
	}

	static
	private Extension createExtension(String content){
		Extension extension = new Extension()
			.addContent(content);

		return extension;
	}

	static
	private MapValues createMapValues(FieldName fieldName, Map<String, String> mapping, List<String> values){
		Set<String> inputs = new LinkedHashSet<>(mapping.keySet());
		Set<String> outputs = new LinkedHashSet<>(mapping.values());

		for(String value : values){

			// Assume disjoint input and output value spaces
			if(outputs.contains(value)){
				continue;
			}

			mapping.put(value, value);
		}

		List<String> columns = Arrays.asList("from", "to");

		InlineTable inlineTable = new InlineTable();

		DocumentBuilder documentBuilder = DOMUtil.createDocumentBuilder();

		Collection<Map.Entry<String, String>> entries = mapping.entrySet();
		for(Map.Entry<String, String> entry : entries){
			Row row = DOMUtil.createRow(documentBuilder, columns, Arrays.asList(entry.getKey(), entry.getValue()));

			inlineTable.addRows(row);
		}

		MapValues mapValues = new MapValues()
			.addFieldColumnPairs(new FieldColumnPair(fieldName, columns.get(0)))
			.setOutputColumn(columns.get(1))
			.setInlineTable(inlineTable);

		return mapValues;
	}

	static
	private Map<String, String> parseMapValues(FunctionExpression.Argument fromArgument, FunctionExpression.Argument toArgument){
		Map<String, String> result = new LinkedHashMap<>();

		List<String> fromValues = parseVector(fromArgument);
		List<String> toValues = parseVector(toArgument);

		if(fromValues.size() != toValues.size()){
			throw new IllegalArgumentException();
		}

		for(int i = 0; i < fromValues.size(); i++){
			String from = fromValues.get(i);
			String to = toValues.get(i);

			if(from == null || to == null){
				throw new IllegalArgumentException();
			}

			result.put(from, to);
		}

		return result;
	}

	static
	private Map<String, String> parseRevalue(FunctionExpression.Argument replaceArgument){
		Map<String, String> result = new LinkedHashMap<>();

		FunctionExpression vectorExpression = toVectorExpression(replaceArgument);

		List<FunctionExpression.Argument> objectArguments = vectorExpression.getArguments();
		for(FunctionExpression.Argument objectArgument : objectArguments){
			String from = objectArgument.getTag();
			if(from == null){
				throw new IllegalArgumentException();
			}

			Constant constant = (Constant)objectArgument.getExpression();
			if(constant.getDataType() != null && (DataType.STRING).equals(constant.getDataType())){
				throw new IllegalArgumentException();
			}

			String to = constant.getValue();

			result.put(from, to);
		}

		return result;
	}

	static
	private List<String> parseVector(FunctionExpression.Argument argument){
		List<String> result = new ArrayList<>();

		FunctionExpression vectorExpression = toVectorExpression(argument);

		List<FunctionExpression.Argument> objectArguments = vectorExpression.getArguments();
		for(FunctionExpression.Argument objectArgument : objectArguments){
			Constant constant = (Constant)objectArgument.getExpression();

			result.add(constant.getValue());
		}

		return result;
	}

	static
	private FunctionExpression toVectorExpression(FunctionExpression.Argument argument){
		FunctionExpression functionExpression = (FunctionExpression)ExpressionTranslator.translateExpression((argument.formatExpression()).trim());

		if(!("c").equals(functionExpression.getFunction())){
			throw new IllegalArgumentException();
		}

		return functionExpression;
	}

	public static final String INTERCEPT = "(Intercept)";
}