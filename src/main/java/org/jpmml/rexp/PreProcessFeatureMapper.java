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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import org.dmg.pmml.DataType;
import org.dmg.pmml.DerivedField;
import org.dmg.pmml.Expression;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.FieldRef;
import org.dmg.pmml.OpType;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.Feature;
import org.jpmml.converter.PMMLUtil;
import org.jpmml.converter.Schema;

public class PreProcessFeatureMapper extends FeatureMapper {

	private Map<FieldName, List<Double>> ranges = Collections.emptyMap();

	private Map<FieldName, Double> mean = Collections.emptyMap();

	private Map<FieldName, Double> std = Collections.emptyMap();

	private Map<FieldName, Double> median = Collections.emptyMap();


	public PreProcessFeatureMapper(RGenericVector preProcess){
		RGenericVector method = (RGenericVector)preProcess.getValue("method");

		RStringVector methodNames = method.names();
		for(int i = 0; i < methodNames.size(); i++){
			String methodName = methodNames.getValue(i);

			switch(methodName){
				case "ignore":
					break;
				case "range":
					this.ranges = createArguments((RDoubleVector)preProcess.getValue("ranges"), 2);
					break;
				case "center":
					this.mean = createArguments((RDoubleVector)preProcess.getValue("mean"));
					break;
				case "scale":
					this.std = createArguments((RDoubleVector)preProcess.getValue("std"));
					break;
				case "medianImpute":
					this.median = createArguments((RDoubleVector)preProcess.getValue("median"));
					break;
				default:
					throw new IllegalArgumentException(methodName);
			}
		}
	}

	@Override
	public Schema createSchema(FieldName targetField, List<FieldName> activeFields){
		Schema schema = super.createSchema(targetField, activeFields);

		schema = filter(schema);

		return schema;
	}

	private Schema filter(Schema schema){
		FieldName targetField = schema.getTargetField();
		List<String> targetCategories = schema.getTargetCategories();
		List<FieldName> activeFields = new ArrayList<>(schema.getActiveFields());
		List<Feature> features = new ArrayList<>(schema.getFeatures());

		if(activeFields.size() != features.size()){
			throw new IllegalArgumentException();
		}

		ListIterator<FieldName> activeFieldIt = activeFields.listIterator();
		ListIterator<Feature> featureIt = features.listIterator();

		while(activeFieldIt.hasNext()){
			FieldName activeField = activeFieldIt.next();
			Feature feature = featureIt.next();

			Expression expression = encodeExpression(activeField);
			if(expression == null){
				continue;
			}

			activeFieldIt.remove();

			DerivedField derivedField = createDerivedField(FieldName.create("preProcess(" + activeField.getValue() + ")"), OpType.CONTINUOUS, DataType.DOUBLE, expression);

			feature = new ContinuousFeature(derivedField);

			featureIt.set(feature);
		}

		schema = new Schema(targetField, targetCategories, activeFields, features);

		return schema;
	}

	private Expression encodeExpression(FieldName name){
		Expression expression = new FieldRef(name);

		List<Double> ranges = this.ranges.get(name);
		if(ranges != null){
			Double min = ranges.get(0);
			Double max = ranges.get(1);

			expression = PMMLUtil.createApply("/", PMMLUtil.createApply("-", expression, PMMLUtil.createConstant(min)), PMMLUtil.createConstant(max - min));
		}

		Double mean = this.mean.get(name);
		if(mean != null){
			expression = PMMLUtil.createApply("-", expression, PMMLUtil.createConstant(mean));
		}

		Double std = this.std.get(name);
		if(std != null){
			expression = PMMLUtil.createApply("/", expression, PMMLUtil.createConstant(std));
		}

		Double median = this.median.get(name);
		if(median != null){
			expression = PMMLUtil.createApply("if", PMMLUtil.createApply("isNotMissing", new FieldRef(name)), expression, PMMLUtil.createConstant(median));
		} // End if

		if(expression instanceof FieldRef){
			return null;
		}

		return expression;
	}

	static
	private Map<FieldName, Double> createArguments(RDoubleVector values){
		Map<FieldName, Double> result = new LinkedHashMap<>();

		RStringVector names = values.names();
		for(int i = 0; i < names.size(); i++){
			String name = names.getValue(i);

			result.put(FieldName.create(name), values.getValue(i));
		}

		return result;
	}

	static
	private Map<FieldName, List<Double>> createArguments(RDoubleVector values, int rows){
		Map<FieldName, List<Double>> result = new LinkedHashMap<>();

		RGenericVector dimnames = (RGenericVector)values.getAttributeValue("dimnames");

		RStringVector rowNames = (RStringVector)dimnames.getValue(0);
		RStringVector columnNames = (RStringVector)dimnames.getValue(1);

		for(int i = 0; i < columnNames.size(); i++){
			String name = columnNames.getValue(i);

			result.put(FieldName.create(name), RExpUtil.getColumn(values.getValues(), rows, columnNames.size(), i));
		}

		return result;
	}
}