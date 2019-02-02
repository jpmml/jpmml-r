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

public class DecorationUtil {

	private DecorationUtil(){
	}

	static
	public RGenericVector getGenericValue(RGenericVector model, String name){

		try {
			return model.getGenericValue(name, false);
		} catch(IllegalArgumentException iae){
			throw toDecorationException(model, name, iae);
		}
	}

	static
	public RNumberVector<?> getNumericValue(RGenericVector model, String name){

		try {
			return model.getNumericValue(name, false);
		} catch(IllegalArgumentException iae){
			throw toDecorationException(model, name, iae);
		}
	}

	static
	public RVector<?> getVectorValue(RGenericVector model, String name){

		try {
			return model.getVectorValue(name, false);
		} catch(IllegalArgumentException iae){
			throw toDecorationException(model, name, iae);
		}
	}

	static
	private RuntimeException toDecorationException(RGenericVector model, String name, Exception e){
		RStringVector classNames = RExpUtil.getClassNames(model);

		String className = classNames.getValue(0);

		return new IllegalArgumentException("Missing \'" + className + "$" + name + "\' element. Please decorate the model object using the \'r2pmml::decorate." + className + "\' function before saving it into the RDS file");
	}
}