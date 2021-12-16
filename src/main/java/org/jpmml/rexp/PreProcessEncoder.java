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

import org.dmg.pmml.DataField;
import org.dmg.pmml.DataType;
import org.dmg.pmml.DerivedField;
import org.dmg.pmml.Expression;
import org.dmg.pmml.FieldRef;
import org.dmg.pmml.OpType;
import org.dmg.pmml.PMMLFunctions;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.Feature;
import org.jpmml.converter.FeatureUtil;
import org.jpmml.converter.FieldNameUtil;
import org.jpmml.converter.FortranMatrixUtil;
import org.jpmml.converter.PMMLUtil;
import org.jpmml.converter.ValueUtil;

public class PreProcessEncoder extends TransformerEncoder<RGenericVector> {

	private Map<String, List<Double>> ranges = Collections.emptyMap();

	private Map<String, Double> mean = Collections.emptyMap();

	private Map<String, Double> std = Collections.emptyMap();

	private Map<String, Double> median = Collections.emptyMap();


	public PreProcessEncoder(RGenericVector preProcess){
		super(preProcess);

		RGenericVector method = preProcess.getGenericElement("method");

		RStringVector methodNames = method.names();
		for(int i = 0; i < methodNames.size(); i++){
			String methodName = methodNames.getValue(i);

			switch(methodName){
				case "ignore":
					break;
				case "range":
					this.ranges = createArguments(preProcess.getDoubleElement("ranges"), 2);
					break;
				case "center":
					this.mean = createArguments(preProcess.getDoubleElement("mean"));
					break;
				case "scale":
					this.std = createArguments(preProcess.getDoubleElement("std"));
					break;
				case "medianImpute":
					this.median = createArguments(preProcess.getDoubleElement("median"));
					break;
				default:
					throw new IllegalArgumentException(methodName);
			}
		}
	}

	@Override
	public void addFeature(Feature feature){
		String name = FeatureUtil.getName(feature);

		DataField dataField = getDataField(name);
		if(dataField != null){
			Expression expression = feature.ref();
			Expression transformedExpression = encodeExpression(name, expression);

			if(!(expression).equals(transformedExpression)){
				DerivedField derivedField = createDerivedField(FieldNameUtil.create("preProcess", feature), OpType.CONTINUOUS, DataType.DOUBLE, transformedExpression);

				feature = new ContinuousFeature(PreProcessEncoder.this, derivedField);
			}
		}

		super.addFeature(feature);
	}

	private Expression encodeExpression(String name, Expression expression){
		List<Double> ranges = this.ranges.get(name);
		if(ranges != null){
			Double min = ranges.get(0);
			Double max = ranges.get(1);

			if(!ValueUtil.isZero(min)){
				expression = PMMLUtil.createApply(PMMLFunctions.SUBTRACT, expression, PMMLUtil.createConstant(min));
			} // End if

			if(!ValueUtil.isOne(max - min)){
				expression = PMMLUtil.createApply(PMMLFunctions.DIVIDE, expression, PMMLUtil.createConstant(max - min));
			}
		}

		Double mean = this.mean.get(name);
		if(mean != null && !ValueUtil.isZero(mean)){
			expression = PMMLUtil.createApply(PMMLFunctions.SUBTRACT, expression, PMMLUtil.createConstant(mean));
		}

		Double std = this.std.get(name);
		if(std != null && !ValueUtil.isOne(std)){
			expression = PMMLUtil.createApply(PMMLFunctions.DIVIDE, expression, PMMLUtil.createConstant(std));
		}

		Double median = this.median.get(name);
		if(median != null){
			expression = PMMLUtil.createApply(PMMLFunctions.IF,
				PMMLUtil.createApply(PMMLFunctions.ISNOTMISSING, new FieldRef(name)),
				expression,
				PMMLUtil.createConstant(median)
			);
		}

		return expression;
	}

	static
	private Map<String, Double> createArguments(RDoubleVector values){
		Map<String, Double> result = new LinkedHashMap<>();

		RStringVector names = values.names();
		for(int i = 0; i < names.size(); i++){
			String name = names.getValue(i);

			result.put(name, values.getValue(i));
		}

		return result;
	}

	static
	private Map<String, List<Double>> createArguments(RDoubleVector values, int rows){
		Map<String, List<Double>> result = new LinkedHashMap<>();

		RStringVector rowNames = values.dimnames(0);
		RStringVector columnNames = values.dimnames(1);

		for(int i = 0; i < columnNames.size(); i++){
			String name = columnNames.getValue(i);

			result.put(name, FortranMatrixUtil.getColumn(values.getValues(), rows, columnNames.size(), i));
		}

		return result;
	}
}
