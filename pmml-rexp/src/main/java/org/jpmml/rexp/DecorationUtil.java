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

import org.jpmml.converter.ExceptionUtil;

public class DecorationUtil {

	private DecorationUtil(){
	}

	static
	public RGenericVector getGenericElement(RGenericVector model, String name){

		try {
			return model.getGenericElement(name, true);
		} catch(RExpException re){
			throw toDecorationException(model, name, re);
		}
	}

	static
	public RBooleanVector getBooleanElement(RGenericVector model, String name){

		try {
			return model.getBooleanElement(name, true);
		} catch(RExpException re){
			throw toDecorationException(model, name, re);
		}
	}

	static
	public RNumberVector<?> getNumericElement(RGenericVector model, String name){

		try {
			return model.getNumericElement(name, true);
		} catch(RExpException re){
			throw toDecorationException(model, name, re);
		}
	}

	static
	public RVector<?> getVectorElement(RGenericVector model, String name){

		try {
			return model.getVectorElement(name, true);
		} catch(RExpException re){
			throw toDecorationException(model, name, re);
		}
	}

	static
	private RExpException toDecorationException(RGenericVector model, String name, Exception e){
		RStringVector classNames = model._class();

		String className = classNames.getValue(0);

		return (RExpException)new RExpException("Missing " + ExceptionUtil.formatName(className + "$" + name) + " element")
			.setSolution("Decorate the model object using the " + ExceptionUtil.formatName("r2pmml::decorate." + className) + " function before saving it into the RDS file");
	}
}