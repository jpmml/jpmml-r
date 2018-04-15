/*
 * Copyright (c) 2018 Villu Ruusmann
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

import java.util.Arrays;
import java.util.List;

import com.google.common.primitives.Doubles;
import org.dmg.pmml.DataField;
import org.dmg.pmml.DataType;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.Model;
import org.dmg.pmml.OpType;
import org.jpmml.converter.Schema;

abstract
public class GLMNetConverter extends ModelConverter<RGenericVector> {

	public GLMNetConverter(RGenericVector glmnet){
		super(glmnet);
	}

	abstract
	public Model encodeModel(RDoubleVector a0, RExp beta, int column, Schema schema);

	@Override
	public void encodeSchema(RExpEncoder encoder){
		RGenericVector glmnet = getObject();

		RExp beta = glmnet.getValue("beta");
		RStringVector classnames = (RStringVector)glmnet.getValue("classnames", true);

		if((classnames != null && classnames.size() > 1) && (beta instanceof RGenericVector)){
			RGenericVector classBetas = (RGenericVector)beta;

			beta = (S4Object)classBetas.getValue(0);
		} // End if

		RGenericVector dimnames = (RGenericVector)beta.getAttributeValue("Dimnames");

		if(classnames != null){
			DataField dataField = encoder.createDataField(FieldName.create("_target"), OpType.CATEGORICAL, DataType.STRING, classnames.getValues());

			encoder.setLabel(dataField);
		} else

		{
			DataField dataField = encoder.createDataField(FieldName.create("_target"), OpType.CONTINUOUS, DataType.DOUBLE);

			encoder.setLabel(dataField);
		}

		RStringVector rowNames = (RStringVector)dimnames.getValue(0);
		for(int i = 0; i < rowNames.size(); i++){
			String rowName = rowNames.getValue(i);

			DataField dataField = encoder.createDataField(FieldName.create(rowName), OpType.CONTINUOUS, DataType.DOUBLE);

			encoder.addFeature(dataField);
		}
	}

	@Override
	public Model encodeModel(Schema schema){
		RGenericVector glmnet = getObject();

		RDoubleVector a0 = (RDoubleVector)glmnet.getValue("a0");
		RExp beta = glmnet.getValue("beta");
		RDoubleVector lambda = (RDoubleVector)glmnet.getValue("lambda");

		RNumberVector<?> lambdaS;

		try {
			lambdaS = (RNumberVector<?>)glmnet.getValue("lambda.s");
		} catch(IllegalArgumentException iae){
			throw new IllegalArgumentException("No lambda value information. Please initialize the \'lambda.s\' element", iae);
		}

		int column = (lambda.getValues()).indexOf((lambdaS.asScalar()).doubleValue());
		if(column < 0){
			throw new IllegalArgumentException();
		}

		return encodeModel(a0, beta, column, schema);
	}

	static
	public List<Double> getCoefficients(S4Object beta, int column){
		RIntegerVector i = (RIntegerVector)beta.getAttributeValue("i");
		RIntegerVector p = (RIntegerVector)beta.getAttributeValue("p");
		RIntegerVector dim = (RIntegerVector)beta.getAttributeValue("Dim");
		RDoubleVector x = (RDoubleVector)beta.getAttributeValue("x");

		double[] result = new double[dim.getValue(0)];

		Arrays.fill(result, Double.NaN);

		int begin = p.getValue(column);
		int end = p.getValue(column + 1);

		for(int index = begin; index < end; index++){
			result[i.getValue(index)] = x.getValue(index);
		}

		return Doubles.asList(result);
	}
}