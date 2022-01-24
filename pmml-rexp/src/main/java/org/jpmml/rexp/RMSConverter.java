/*
 * Copyright (c) 2017 Villu Ruusmann
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
import java.util.List;

import org.jpmml.converter.Feature;

abstract
public class RMSConverter extends LMConverter {

	public RMSConverter(RGenericVector rms){
		super(rms);
	}

	@Override
	public void encodeSchema(RExpEncoder encoder){
		RGenericVector rms = getObject();

		RExp terms = rms.getElement("terms");
		RGenericVector design = rms.getGenericElement("Design");

		RGenericVector parms = design.getGenericElement("parms", false);

		List<String> coefficientNames = getCoefficientNames();

		FormulaContext context = new FormulaContext(){

			@Override
			public List<String> getCategories(String variable){

				if(parms != null && parms.hasElement(variable)){
					RStringVector levels = parms.getStringElement(variable);

					return levels.getValues();
				}

				return null;
			}

			@Override
			public RVector<?> getData(String variable){

				if(parms != null && parms.hasElement(variable)){
					return RStringVector.EMPTY;
				} // End if

				if(!coefficientNames.contains(variable) && coefficientNames.contains(variable + "TRUE")){
					return RBooleanVector.EMPTY;
				}

				return RDoubleVector.EMPTY;
			}
		};

		encodeSchema(terms, context, encoder);
	}

	@Override
	public String getInterceptName(){
		return RMSConverter.INTERCEPT;
	}

	@Override
	public List<String> getCoefficientNames(){
		RGenericVector rms = getObject();

		RGenericVector design = rms.getGenericElement("Design");

		RStringVector mmcolnames = design.getStringElement("mmcolnames");

		List<String> result = new ArrayList<>();
		result.add(RMSConverter.INTERCEPT);
		result.addAll(mmcolnames.getDequotedValues());

		return result;
	}

	@Override
	public List<Double> getFeatureCoefficients(List<? extends Feature> features, RDoubleVector coefficients){
		List<Double> values = coefficients.getValues();

		return values.subList(1, values.size());
	}

	public static final String INTERCEPT = "Intercept";
}