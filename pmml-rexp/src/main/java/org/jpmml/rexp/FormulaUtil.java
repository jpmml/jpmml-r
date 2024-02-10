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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.dmg.pmml.Apply;
import org.dmg.pmml.Constant;
import org.dmg.pmml.DataField;
import org.dmg.pmml.DataType;
import org.dmg.pmml.DerivedField;
import org.dmg.pmml.Discretize;
import org.dmg.pmml.DiscretizeBin;
import org.dmg.pmml.Expression;
import org.dmg.pmml.FieldRef;
import org.dmg.pmml.Interval;
import org.dmg.pmml.MapValues;
import org.dmg.pmml.OpType;
import org.dmg.pmml.PMMLFunctions;
import org.jpmml.converter.ExpressionUtil;
import org.jpmml.converter.Feature;
import org.jpmml.converter.PMMLUtil;
import org.jpmml.converter.ValueUtil;

public class FormulaUtil {

	private FormulaUtil(){
	}

	static
	public Formula createFormula(RExp terms, FormulaContext context, RExpEncoder encoder){
		Formula formula = new Formula(encoder);

		RIntegerVector factors = terms.getIntegerAttribute("factors");
		RStringVector dataClasses = terms.getStringAttribute("dataClasses", false);

		RStringVector variableRows = factors.dimnames(0);
		RStringVector termColumns = factors.dimnames(1);

		VariableMap expressionFields = new VariableMap();

		for(int i = 0; i < variableRows.size(); i++){
			String variable = variableRows.getDequotedValue(i);

			String name = variable;
			OpType opType = OpType.CONTINUOUS;
			DataType dataType;

			if(dataClasses != null){
				dataType = RExpUtil.getDataType(dataClasses.getElement(variable));
			} else

			{
				RVector<?> data = context.getData(name);
				if(data != null){
					dataType = data.getDataType();
				} else

				{
					throw new IllegalArgumentException();
				}
			}

			List<String> categories = context.getCategories(variable);
			if(categories != null && !categories.isEmpty()){
				opType = OpType.CATEGORICAL;
			}

			Expression expression = null;

			String shortName = name;

			expression:
			if((variable.indexOf('(') > -1 && variable.indexOf(')') > -1) || (variable.indexOf(' ') > -1)){

				try {
					expression = ExpressionTranslator.translateExpression(variable);
				} catch(Exception e){
					break expression;
				}

				FunctionExpression functionExpression;

				if(expression instanceof FunctionExpression){
					functionExpression = (FunctionExpression)expression;
				} else

				{
					FunctionExpression.Argument xArgument = new FunctionExpression.Argument("x", expression){

						@Override
						public String formatExpression(){
							return variable;
						}
					};

					functionExpression = new FunctionExpression("base", "I", Collections.singletonList(xArgument));
				} // End if

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
					expression = null;

					break expression;
				}

				FunctionExpression.Argument xArgument = functionExpression.getArgument("x", 0);

				String value = (xArgument.formatExpression()).trim();

				shortName = (functionExpression.hasId("base", "I") ? value : (functionExpression.getFunction() + "(" + value + ")"));
			}

			List<String> categoryNames;
			List<?> categoryValues;

			if(dataType == DataType.BOOLEAN){
				opType = OpType.CATEGORICAL;

				categoryNames = Arrays.asList("FALSE", "TRUE");
				categoryValues = Arrays.asList(Boolean.FALSE, Boolean.TRUE);
			} else

			{
				categoryNames = categories;
				categoryValues = categories;
			} // End if

			if(expression != null){
				DerivedField derivedField = encoder.createDerivedField(name, opType, dataType, expression)
					.addExtensions(PMMLUtil.createExtension("variable", (Object)variable));

				if(categoryNames != null && !categoryNames.isEmpty()){
					formula.addField(derivedField, categoryNames, categoryValues);
				} else

				{
					formula.addField(derivedField);
				} // End if

				if(!(name).equals(shortName)){
					encoder.renameField(name, shortName);
				}
			} else

			{
				if(categoryNames != null && !categoryNames.isEmpty()){
					DataField dataField = encoder.createDataField(name, OpType.CATEGORICAL, dataType, categories);

					formula.addField(dataField, categoryNames, categoryValues);
				} else

				{
					DataField dataField = encoder.createDataField(name, OpType.CONTINUOUS, dataType);

					formula.addField(dataField);
				}
			}
		}

		Collection<Map.Entry<String, List<String>>> entries = expressionFields.entrySet();
		for(Map.Entry<String, List<String>> entry : entries){
			String name = entry.getKey();
			List<String> categories = entry.getValue();

			DataField dataField = encoder.getDataField(name);
			if(dataField == null){
				OpType opType = OpType.CONTINUOUS;
				DataType dataType = DataType.DOUBLE;

				if(categories != null && !categories.isEmpty()){
					opType = OpType.CATEGORICAL;
				}

				RVector<?> data = context.getData(name);
				if(data != null){
					dataType = data.getDataType();
				}

				dataField = encoder.createDataField(name, opType, dataType, categories);
			}
		}

		return formula;
	}

	static
	public void setLabel(Formula formula, RExp terms, RExp levels, RExpEncoder encoder){
		RIntegerVector response = terms.getIntegerAttribute("response");

		int responseIndex = response.asScalar();
		if(responseIndex != 0){
			DataField dataField = (DataField)formula.getField(responseIndex - 1);

			String name = dataField.requireName();

			if(encoder.getDataField(name) == null){
				encoder.addDataField(dataField);
			} // End if

			if(levels instanceof RStringVector){
				RStringVector stringLevels = (RStringVector)levels;

				dataField = (DataField)encoder.toCategorical(name, stringLevels.getValues());
			} else

			if(levels instanceof RFactorVector){
				RFactorVector factorLevels = (RFactorVector)levels;

				dataField = (DataField)encoder.toCategorical(name, factorLevels.getLevelValues());
			} else

			if(levels != null){
				throw new IllegalArgumentException();
			}

			encoder.setLabel(dataField);
		} else

		{
			throw new IllegalArgumentException();
		}
	}

	static
	public void addFeatures(Formula formula, RStringVector names, boolean allowInteractions, RExpEncoder encoder){
		addFeatures(formula, names.getValues(), allowInteractions, encoder);
	}

	static
	public void addFeatures(Formula formula, List<String> names, boolean allowInteractions, RExpEncoder encoder){

		for(int i = 0; i < names.size(); i++){
			String name = names.get(i);

			Feature feature;

			if(allowInteractions){
				feature = formula.resolveComplexFeature(name);
			} else

			{
				feature = formula.resolveFeature(name);
			}

			encoder.addFeature(feature);
		}
	}

	static
	public List<String> removeSpecialSymbol(List<String> names, String specialName){
		int index = names.indexOf(specialName);

		if(index > -1){
			names = new ArrayList<>(names);

			names.remove(index);
		}

		return names;
	}

	static
	public List<String> removeSpecialSymbol(List<String> names, String specialName, int specialNameIndex){
		String name = names.get(specialNameIndex);

		if((name).equals(specialName)){
			names = new ArrayList<>(names);

			names.remove(specialNameIndex);
		} else

		{
			throw new IllegalArgumentException();
		}

		return names;
	}

	static
	private Expression encodeCutExpression(FunctionExpression functionExpression, List<String> categories, VariableMap expressionFields, RExpEncoder encoder){
		FunctionExpression.Argument xArgument = functionExpression.getArgument("x", 0);

		expressionFields.putAll(xArgument);

		String fieldName = prepareInputField(xArgument, OpType.CONTINUOUS, DataType.DOUBLE, encoder);

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
		Apply apply = ExpressionUtil.createApply(PMMLFunctions.IF,
			prepareExpression(testArgument, expressionFields, encoder),
			prepareExpression(yesArgument, expressionFields, encoder),
			prepareExpression(noArgument, expressionFields, encoder)
		);

		return apply;
	}

	static
	private Expression encodeMapValuesExpression(FunctionExpression functionExpression, List<String> categories, VariableMap expressionFields, RExpEncoder encoder){
		FunctionExpression.Argument xArgument = functionExpression.getArgument("x", 0);

		expressionFields.putAll(xArgument);

		String fieldName = prepareInputField(xArgument, OpType.CATEGORICAL, DataType.STRING, encoder);

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

		String fieldName = prepareInputField(xArgument, OpType.CATEGORICAL, DataType.STRING, encoder);

		FunctionExpression.Argument replaceArgument = functionExpression.getArgument("replace", 1);

		Map<String, String> mapping = parseRevalue(replaceArgument);

		expressionFields.put(fieldName, new ArrayList<>(mapping.keySet()));

		return createMapValues(fieldName, mapping, categories);
	}

	static
	private String prepareInputField(FunctionExpression.Argument argument, OpType opType, DataType dataType, RExpEncoder encoder){
		Expression expression = argument.getExpression();

		if(expression instanceof FieldRef){
			FieldRef fieldRef = (FieldRef)expression;

			return fieldRef.requireField();
		} else

		if(expression instanceof Apply){
			Apply apply = (Apply)expression;

			DerivedField derivedField = encoder.createDerivedField((argument.formatExpression()).trim(), opType, dataType, apply);

			return derivedField.requireName();
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
	private Discretize createDiscretize(String name, List<String> categories){
		Discretize discretize = new Discretize(name);

		for(String category : categories){
			Interval interval = ExpressionTranslator.translateInterval(category);

			DiscretizeBin discretizeBin = new DiscretizeBin(category, interval);

			discretize.addDiscretizeBins(discretizeBin);
		}

		return discretize;
	}

	static
	private MapValues createMapValues(String name, Map<String, String> mapping, List<String> categories){
		Set<String> inputs = new LinkedHashSet<>(mapping.keySet());
		Set<String> outputs = new LinkedHashSet<>(mapping.values());

		for(String category : categories){

			// Assume disjoint input and output value spaces
			if(outputs.contains(category)){
				continue;
			}

			mapping.put(category, category);
		}

		return ExpressionUtil.createMapValues(name, mapping);
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

			String to = (String)constant.getValue();
			if(to == null){
				throw new IllegalArgumentException();
			}

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

			String string = ValueUtil.asString(constant.getValue());

			result.add(string);
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
	private class VariableMap extends LinkedHashMap<String, List<String>> {

		public void putAll(FunctionExpression.Argument argument){
			Set<String> names = argument.getFieldNames();

			for(String name : names){

				if(!containsKey(name)){
					put(name, null);
				}
			}
		}
	}
}
