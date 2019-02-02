/*
 * Copyright (c) 2014 Villu Ruusmann
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

import org.dmg.pmml.PMML;

abstract
public class Converter<R extends RExp> {

	private R object = null;


	public Converter(R object){
		setObject(object);
	}

	abstract
	public PMML encodePMML(RExpEncoder encoder);

	public PMML encodePMML(){
		RExpEncoder encoder = createEncoder();

		return encodePMML(encoder);
	}

	public RExpEncoder createEncoder(){
		RExp object = getObject();

		RGenericVector preProcess;
		RGenericVector recipe;

		if(object instanceof S4Object){
			S4Object model = (S4Object)object;

			preProcess = (RGenericVector)model.getAttributeValue("preProcess", true);
			recipe = (RGenericVector)model.getAttributeValue("recipe", true);
		} else

		if(object instanceof RGenericVector){
			RGenericVector model = (RGenericVector)object;

			preProcess = model.getGenericValue("preProcess", true);
			recipe = model.getGenericValue("recipe", true);
		} else

		{
			preProcess = null;
			recipe = null;
		} // End if

		if(preProcess != null && recipe != null){
			throw new IllegalArgumentException();
		}

		RExpEncoder encoder;

		if(preProcess != null){
			encoder = new PreProcessEncoder(preProcess);
		} else

		if(recipe != null){
			encoder = new RecipeEncoder(recipe);
		} else

		{
			encoder = new RExpEncoder();
		}

		return encoder;
	}

	public Boolean getOption(String name, Boolean defaultValue){
		RGenericVector options = getOptions();

		if(options != null){
			RBooleanVector option = options.getBooleanValue(name, true);

			if(option != null){
				return option.asScalar();
			}
		}

		return defaultValue;
	}

	public RGenericVector getOptions(){
		R object = getObject();

		if(object instanceof RGenericVector){
			RGenericVector model = (RGenericVector)object;

			return model.getGenericValue("pmml_options", true);
		}

		return null;
	}

	public R getObject(){
		return this.object;
	}

	private void setObject(R object){
		this.object = object;
	}

	static
	public <R extends RExp> Converter<R> newConverter(R rexp){
		ConverterFactory converterFactory = ConverterFactory.newInstance();

		return converterFactory.newConverter(rexp);
	}
}