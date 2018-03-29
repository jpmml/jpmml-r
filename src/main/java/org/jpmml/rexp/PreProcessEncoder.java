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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.dmg.pmml.DataType;
import org.dmg.pmml.DerivedField;
import org.dmg.pmml.Expression;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.FieldRef;
import org.dmg.pmml.OpType;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.Feature;
import org.jpmml.converter.FeatureUtil;
import org.jpmml.converter.FortranMatrixUtil;
import org.jpmml.converter.PMMLUtil;
import org.jpmml.converter.Schema;

public class PreProcessEncoder extends RExpEncoder {

	private Map<FieldName, List<Double>> ranges = Collections.emptyMap();

	private Map<FieldName, Double> mean = Collections.emptyMap();

	private Map<FieldName, Double> std = Collections.emptyMap();

	private Map<FieldName, Double> median = Collections.emptyMap();


	public PreProcessEncoder(RGenericVector preProcess){
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
	public Schema createSchema(){
		Schema schema = super.createSchema();

		schema = filter(schema);

		return schema;
	}

	private Schema filter(Schema schema){
		Function<Feature, Feature> function = new Function<Feature, Feature>(){

			@Override
			public Feature apply(Feature feature){
				Expression expression = encodeExpression(feature);

				if(expression == null){
					return feature;
				}

				DerivedField derivedField = createDerivedField(FeatureUtil.createName("preProcess", feature), OpType.CONTINUOUS, DataType.DOUBLE, expression);

				return new ContinuousFeature(PreProcessEncoder.this, derivedField);
			}
		};

		return schema.toTransformedSchema(function);
	}

	private Expression encodeExpression(Feature feature){
		FieldName name = feature.getName();

		Expression expression = feature.ref();

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

		RStringVector rowNames = values.dimnames(0);
		RStringVector columnNames = values.dimnames(1);

		for(int i = 0; i < columnNames.size(); i++){
			String name = columnNames.getValue(i);

			result.put(FieldName.create(name), FortranMatrixUtil.getColumn(values.getValues(), rows, columnNames.size(), i));
		}

		return result;
	}
}
