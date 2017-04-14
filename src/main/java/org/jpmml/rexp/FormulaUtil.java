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
	public Formula createFormula(RExp terms, FormulaContext context, RExpEncoder encoder){
		Formula formula = new Formula(encoder);

		RIntegerVector factors = (RIntegerVector)terms.getAttributeValue("factors");
		RStringVector dataClasses = (RStringVector)terms.getAttributeValue("dataClasses");

		RStringVector variableRows = factors.dimnames(0);
		RStringVector termColumns = factors.dimnames(1);

		VariableMap expressionFields = new VariableMap();

		for(int i = 0; i < variableRows.size(); i++){
			String variable = variableRows.getDequotedValue(i);

			FieldName name = FieldName.create(variable);
			OpType opType = OpType.CONTINUOUS;
			DataType dataType = RExpUtil.getDataType(dataClasses.getValue(variable));

			List<String> categories = context.getCategories(variable);
			if(categories != null && categories.size() > 0){
				opType = OpType.CATEGORICAL;
			}

			Expression expression = null;

			FieldName shortName = name;

			expression:
			if(variable.indexOf('(') > -1 && variable.indexOf(')') > -1){
				FunctionExpression functionExpression;

				try {
					functionExpression = (FunctionExpression)ExpressionTranslator.translateExpression(variable);
				} catch(Exception e){
					break expression;
				}

				if(functionExpression.hasId("base", "cut")){
					expression = encodeCutExpression(functionExpression, categories, expressionFields, encoder);
				} else

				if(functionExpression.hasId("base", "I")){
					expression = encodeIdentityExpression(functionExpression, expressionFields, encoder);
				} else

				if(functionExpression.hasId("base", "ifelse")){
					expression = encodeIfElseExpression(functionExpression, expressionFields, encoder);
				} else

				if(functionExpression.hasId("plyr", "mapvalues")){
					expression = encodeMapValuesExpression(functionExpression, categories, expressionFields, encoder);
				} else

				if(functionExpression.hasId("plyr", "revalue")){
					expression = encodeReValueExpression(functionExpression, categories, expressionFields, encoder);
				} else

				{
					break expression;
				}

				FunctionExpression.Argument xArgument = functionExpression.getArgument("x", 0);

				String value = (xArgument.formatExpression()).trim();

				shortName = FieldName.create(functionExpression.hasId("base", "I") ? value : (functionExpression.getFunction() + "(" + value + ")"));
			} // End if

			if(expression != null){
				DerivedField derivedField = encoder.createDerivedField(name, opType, dataType, expression)
					.addExtensions(createExtension(variable));

				if(categories != null && categories.size() > 0){
					formula.addField(derivedField, categories);
				} else

				{
					formula.addField(derivedField);
				} // End if

				if(!(name).equals(shortName)){
					encoder.renameField(name, shortName);
				}
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

		Collection<Map.Entry<FieldName, List<String>>> entries = expressionFields.entrySet();
		for(Map.Entry<FieldName, List<String>> entry : entries){
			FieldName name = entry.getKey();
			List<String> categories = entry.getValue();

			DataField dataField = encoder.getDataField(name);
			if(dataField == null){
				OpType opType = OpType.CONTINUOUS;
				DataType dataType = DataType.DOUBLE;

				if(categories != null && categories.size() > 0){
					opType = OpType.CATEGORICAL;
				}

				RGenericVector data = context.getData();
				if(data != null && data.hasValue(name.getValue())){
					RVector<?> column = (RVector<?>)data.getValue(name.getValue());

					dataType = column.getDataType();
				}

				dataField = encoder.createDataField(name, opType, dataType, categories);
			}
		}

		return formula;
	}

	static
	private Expression encodeCutExpression(FunctionExpression functionExpression, List<String> categories, VariableMap expressionFields, RExpEncoder encoder){
		FunctionExpression.Argument xArgument = functionExpression.getArgument("x", 0);

		expressionFields.putAll(xArgument);

		FieldName fieldName = prepareInputField(xArgument, OpType.CONTINUOUS, DataType.DOUBLE, encoder);

		return createDiscretize(fieldName, categories);
	}

	static
	private Expression encodeIdentityExpression(FunctionExpression functionExpression, VariableMap expressionFields, RExpEncoder encoder){
		FunctionExpression.Argument xArgument = functionExpression.getArgument("x", 0);

		expressionFields.putAll(xArgument);

		return prepareExpression(xArgument, expressionFields, encoder);
	}

	static
	private Expression encodeIfElseExpression(FunctionExpression functionExpression, VariableMap expressionFields, RExpEncoder encoder){
		FunctionExpression.Argument testArgument = functionExpression.getArgument("test", 0);

		expressionFields.putAll(testArgument);

		FunctionExpression.Argument yesArgument = functionExpression.getArgument("yes", 1);
		FunctionExpression.Argument noArgument = functionExpression.getArgument("no", 2);

		expressionFields.putAll(yesArgument);
		expressionFields.putAll(noArgument);

		// XXX: "Missing values in test give missing values in the result"
		Apply apply = PMMLUtil.createApply("if")
			.addExpressions(prepareExpression(testArgument, expressionFields, encoder))
			.addExpressions(prepareExpression(yesArgument, expressionFields, encoder), prepareExpression(noArgument, expressionFields, encoder));

		return apply;
	}

	static
	private Expression encodeMapValuesExpression(FunctionExpression functionExpression, List<String> categories, VariableMap expressionFields, RExpEncoder encoder){
		FunctionExpression.Argument xArgument = functionExpression.getArgument("x", 0);

		expressionFields.putAll(xArgument);

		FieldName fieldName = prepareInputField(xArgument, OpType.CATEGORICAL, DataType.STRING, encoder);

		FunctionExpression.Argument fromArgument = functionExpression.getArgument("from", 1);
		FunctionExpression.Argument toArgument = functionExpression.getArgument("to", 2);

		Map<String, String> mapping = parseMapValues(fromArgument, toArgument);

		expressionFields.put(fieldName, new ArrayList<>(mapping.keySet()));

		return createMapValues(fieldName, mapping, categories);
	}

	static
	private Expression encodeReValueExpression(FunctionExpression functionExpression, List<String> categories, VariableMap expressionFields, RExpEncoder encoder){
		FunctionExpression.Argument xArgument = functionExpression.getArgument("x", 0);

		expressionFields.putAll(xArgument);

		FieldName fieldName = prepareInputField(xArgument, OpType.CATEGORICAL, DataType.STRING, encoder);

		FunctionExpression.Argument replaceArgument = functionExpression.getArgument("replace", 1);

		Map<String, String> mapping = parseRevalue(replaceArgument);

		expressionFields.put(fieldName, new ArrayList<>(mapping.keySet()));

		return createMapValues(fieldName, mapping, categories);
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

			DerivedField derivedField = encoder.createDerivedField(FieldName.create((argument.formatExpression()).trim()), opType, dataType, apply);

			return derivedField.getName();
		} else

		{
			throw new IllegalArgumentException();
		}
	}

	static
	private Expression prepareExpression(FunctionExpression.Argument argument, VariableMap expressionFields, RExpEncoder encoder){
		Expression expression = argument.getExpression();

		if(expression instanceof FunctionExpression){
			FunctionExpression functionExpression = (FunctionExpression)expression;

			if(functionExpression.hasId("base", "ifelse")){
				return encodeIfElseExpression(functionExpression, expressionFields, encoder);
			}

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

		if(!functionExpression.hasId("base", "c")){
			throw new IllegalArgumentException();
		}

		return functionExpression;
	}

	static
	private class VariableMap extends LinkedHashMap<FieldName, List<String>> {

		public void putAll(FunctionExpression.Argument argument){
			Set<FieldName> names = argument.getFieldNames();

			for(FieldName name : names){

				if(!containsKey(name)){
					put(name, null);
				}
			}
		}
	}
}
