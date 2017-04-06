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
import java.util.Objects;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;

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
import org.dmg.pmml.OpType;
import org.dmg.pmml.Row;
import org.jpmml.converter.DOMUtil;
import org.jpmml.converter.PMMLUtil;

public class FormulaUtil {

	private FormulaUtil(){
	}

	static
	public void encodeFeatures(Formula formula, FormulaContext context, RExp terms, RExpEncoder encoder){
		RIntegerVector factors = (RIntegerVector)terms.getAttributeValue("factors");
		RStringVector dataClasses = (RStringVector)terms.getAttributeValue("dataClasses");

		RStringVector variableRows = factors.dimnames(0);
		RStringVector termColumns = factors.dimnames(1);

		Set<FieldName> expressionFieldNames = new LinkedHashSet<>();

		Map<FieldName, List<String>> fieldCategories = new LinkedHashMap<>();

		fields:
		for(int i = 0; i < variableRows.size(); i++){
			String variable = variableRows.getValue(i);

			List<String> categories = context.getCategories(variable);

			FieldName name = FieldName.create(variable);
			DataType dataType = RExpUtil.getDataType(dataClasses.getValue(variable));

			FunctionExpression functionExpression = null;

			if(variable.indexOf('(') > -1 && variable.indexOf(')') > -1){

				try {
					functionExpression = (FunctionExpression)ExpressionTranslator.translateExpression(variable);
				} catch(Exception e){
					// Ignored
				}
			} // End if

			if(checkFunction(null, "I", functionExpression)){
				FunctionExpression.Argument argument = functionExpression.getArgument(0);

				expressionFieldNames.addAll(argument.getFieldNames());

				Expression expression = argument.getExpression();

				DerivedField derivedField =  encoder.createDerivedField(name, OpType.CONTINUOUS, dataType, expression)
					.addExtensions(createExtension(variable));

				formula.addField(derivedField);

				encoder.renameField(name, formatFunction("I", argument));

				continue fields;
			} else

			if(checkFunction("base", "cut", functionExpression)){
				FunctionExpression.Argument xArgument = functionExpression.getArgument(0);

				expressionFieldNames.addAll(xArgument.getFieldNames());

				FieldName fieldName = prepareInputField(xArgument, OpType.CATEGORICAL, dataType, encoder);

				Discretize discretize = createDiscretize(fieldName, categories);

				DerivedField derivedField = encoder.createDerivedField(name, OpType.CATEGORICAL, dataType, discretize)
					.addExtensions(createExtension(variable));

				formula.addField(derivedField, categories);

				encoder.renameField(name, formatFunction("cut", xArgument));
			} else

			if(checkFunction("base", "ifelse", functionExpression)){
				FunctionExpression.Argument testArgument = functionExpression.getArgument("test", 0);
				FunctionExpression.Argument yesArgument = functionExpression.getArgument("yes", 1);
				FunctionExpression.Argument noArgument = functionExpression.getArgument("no", 2);

				expressionFieldNames.addAll(testArgument.getFieldNames());
				expressionFieldNames.addAll(yesArgument.getFieldNames());
				expressionFieldNames.addAll(noArgument.getFieldNames());

				OpType opType = OpType.CONTINUOUS;

				if(categories != null && categories.size() > 0){
					opType = OpType.CATEGORICAL;
				}

				// XXX: "Missing values in test give missing values in the result"
				Apply apply = PMMLUtil.createApply("if")
					.addExpressions(prepareExpression(testArgument), prepareExpression(yesArgument), prepareExpression(noArgument));

				DerivedField derivedField = encoder.createDerivedField(name, opType, dataType, apply)
					.addExtensions(createExtension(variable));

				if(categories != null && categories.size() > 0){
					formula.addField(derivedField, categories);
				} else

				{
					formula.addField(derivedField);
				}
			} else

			if(checkFunction("plyr", "mapvalues", functionExpression)){
				FunctionExpression.Argument xArgument = functionExpression.getArgument("x", 0);

				expressionFieldNames.addAll(xArgument.getFieldNames());

				FieldName fieldName = prepareInputField(xArgument, OpType.CATEGORICAL, dataType, encoder);

				FunctionExpression.Argument fromArgument = functionExpression.getArgument("from", 1);
				FunctionExpression.Argument toArgument = functionExpression.getArgument("to", 2);

				Map<String, String> mapping = parseMapValues(fromArgument, toArgument);

				MapValues mapValues = createMapValues(fieldName, mapping, categories);

				fieldCategories.put(fieldName, new ArrayList<>(mapping.keySet()));

				DerivedField derivedField = encoder.createDerivedField(name, OpType.CATEGORICAL, dataType, mapValues)
					.addExtensions(createExtension(variable));

				formula.addField(derivedField, categories);

				encoder.renameField(name, formatFunction("mapvalues", xArgument));
			} else

			if(checkFunction("plyr", "revalue", functionExpression)){
				FunctionExpression.Argument xArgument = functionExpression.getArgument("x", 0);

				expressionFieldNames.addAll(xArgument.getFieldNames());

				FieldName fieldName = prepareInputField(xArgument, OpType.CATEGORICAL, dataType, encoder);

				FunctionExpression.Argument replaceArgument = functionExpression.getArgument("replace", 1);

				Map<String, String> mapping = parseRevalue(replaceArgument);

				MapValues mapValues = createMapValues(fieldName, mapping, categories);

				fieldCategories.put(fieldName, new ArrayList<>(mapping.keySet()));

				DerivedField derivedField = encoder.createDerivedField(name, OpType.CATEGORICAL, dataType, mapValues)
					.addExtensions(createExtension(variable));

				formula.addField(derivedField, categories);

				encoder.renameField(name, formatFunction("revalue", xArgument));
			} else

			{
				if((DataType.BOOLEAN).equals(dataType)){
					categories = Arrays.asList("false", "true");
				} // End if

				if(categories != null && categories.size() > 0){
					DataField dataField = encoder.createDataField(name, OpType.CATEGORICAL, dataType, categories);

					List<String> categoryNames;
					List<String> categoryValues;

					switch(dataType){
						case BOOLEAN:
							categoryNames = Arrays.asList("FALSE", "TRUE");
							categoryValues = Arrays.asList("false", "true");
							break;
						default:
							categoryNames = categories;
							categoryValues = categories;
							break;
					}

					formula.addField(dataField, categoryNames, categoryValues);
				} else

				{
					DataField dataField = encoder.createDataField(name, OpType.CONTINUOUS, dataType);

					formula.addField(dataField);
				}
			}
		}

		for(FieldName expressionFieldName : expressionFieldNames){
			DataField dataField = encoder.getDataField(expressionFieldName);

			if(dataField == null){
				OpType opType = OpType.CONTINUOUS;
				DataType dataType = DataType.DOUBLE;

				List<String> categories = fieldCategories.get(expressionFieldName);

				if(categories != null && categories.size() > 0){
					opType = OpType.CATEGORICAL;
				}

				RGenericVector data = context.getData();
				if(data != null && data.hasValue(expressionFieldName.getValue())){
					RVector<?> column = (RVector<?>)data.getValue(expressionFieldName.getValue());

					dataType = column.getDataType();
				}

				dataField = encoder.createDataField(expressionFieldName, opType, dataType, categories);
			}
		}
	}

	static
	private boolean checkFunction(String namespace, String function, FunctionExpression functionExpression){

		if(functionExpression != null){
			return (Objects.equals(namespace, functionExpression.getNamespace()) || Objects.equals(null, functionExpression.getNamespace())) && Objects.equals(function, functionExpression.getFunction());
		}

		return false;
	}

	static
	private FieldName formatFunction(String function, FunctionExpression.Argument argument){
		String value = (argument.formatExpression()).trim();

		return FieldName.create(function != null ? (function + "(" + value + ")") : value);
	}

	static
	private FieldName prepareInputField(FunctionExpression.Argument argument, OpType opType, DataType dataType, RExpEncoder encoder){
		Expression expression = argument.getExpression();

		if(expression instanceof FieldRef){
			FieldRef fieldRef = (FieldRef)expression;

			return fieldRef.getField();
		} else

		if(expression instanceof Apply){
			Apply apply = (Apply)expression;

			DerivedField derivedField = encoder.createDerivedField(formatFunction(null, argument), opType, dataType, apply)
				.addExtensions(createExtension((argument.formatExpression()).trim()));

			return derivedField.getName();
		} else

		{
			throw new IllegalArgumentException();
		}
	}

	static
	private Expression prepareExpression(FunctionExpression.Argument argument){
		Expression expression = argument.getExpression();

		if(expression instanceof FunctionExpression){
			FunctionExpression functionExpression = (FunctionExpression)expression;

			throw new IllegalArgumentException();
		}

		return expression;
	}

	static
	private Extension createExtension(String content){
		Extension extension = new Extension()
			.addContent(content);

		return extension;
	}

	static
	private Discretize createDiscretize(FieldName name, List<String> categories){
		Discretize discretize = new Discretize(name);

		for(String category : categories){
			Interval interval = ExpressionTranslator.translateInterval(category);

			DiscretizeBin discretizeBin = new DiscretizeBin(category, interval);

			discretize.addDiscretizeBins(discretizeBin);
		}

		return discretize;
	}

	static
	private MapValues createMapValues(FieldName name, Map<String, String> mapping, List<String> categories){
		Set<String> inputs = new LinkedHashSet<>(mapping.keySet());
		Set<String> outputs = new LinkedHashSet<>(mapping.values());

		for(String category : categories){

			// Assume disjoint input and output value spaces
			if(outputs.contains(category)){
				continue;
			}

			mapping.put(category, category);
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
			.addFieldColumnPairs(new FieldColumnPair(name, columns.get(0)))
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
}
