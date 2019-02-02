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

	private Double lambdaS = null;


	public GLMNetConverter(RGenericVector glmnet){
		super(glmnet);
	}

	abstract
	public Model encodeModel(RDoubleVector a0, RExp beta, int column, Schema schema);

	@Override
	public void encodeSchema(RExpEncoder encoder){
		RGenericVector glmnet = getObject();

		RExp beta = glmnet.getValue("beta");
		RStringVector classnames = glmnet.getStringValue("classnames", true);

		if((classnames != null && classnames.size() > 1) && (beta instanceof RGenericVector)){
			RGenericVector classBetas = (RGenericVector)beta;

			beta = (S4Object)classBetas.getValue(0);
		} // End if

		RGenericVector dimnames = beta.getGenericAttributeValue("Dimnames");

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

		RDoubleVector a0 = glmnet.getDoubleValue("a0");
		RExp beta = glmnet.getValue("beta");
		RDoubleVector lambda = glmnet.getDoubleValue("lambda");

		Double lambdaS = getLambdaS();
		if(lambdaS == null){
			lambdaS = loadLambdaS();
		}

		int column = (lambda.getValues()).indexOf(lambdaS);
		if(column < 0){
			throw new IllegalArgumentException();
		}

		return encodeModel(a0, beta, column, schema);
	}

	private Double loadLambdaS(){
		RGenericVector glmnet = getObject();

		RNumberVector<?> lambdaS = DecorationUtil.getNumericValue(glmnet, "lambda.s");

		return (lambdaS.asScalar()).doubleValue();
	}

	public Double getLambdaS(){
		return this.lambdaS;
	}

	void setLambdaS(Double lambdaS){
		this.lambdaS = lambdaS;
	}

	static
	public List<Double> getCoefficients(S4Object beta, int column){
		RIntegerVector i = beta.getIntegerAttributeValue("i");
		RIntegerVector p = beta.getIntegerAttributeValue("p");
		RIntegerVector dim = beta.getIntegerAttributeValue("Dim");
		RDoubleVector x = beta.getDoubleAttributeValue("x");

		double[] result = new double[dim.getValue(0)];

		int begin = p.getValue(column);
		int end = p.getValue(column + 1);

		for(int index = begin; index < end; index++){
			result[i.getValue(index)] = x.getValue(index);
		}

		return Doubles.asList(result);
	}
}