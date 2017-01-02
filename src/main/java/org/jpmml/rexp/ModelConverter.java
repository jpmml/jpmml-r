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

import org.dmg.pmml.Model;
import org.dmg.pmml.PMML;
import org.jpmml.converter.Schema;

abstract
public class ModelConverter<R extends RExp> extends Converter<R> {

	public ModelConverter(R object){
		super(object);
	}

	abstract
	public void encodeFeatures(RExpEncoder encoder);

	abstract
	public Model encodeModel(Schema schema);

	public boolean isSupervised(){
		return true;
	}

	@Override
	public PMML encodePMML(){
		RExp object = getObject();

		RGenericVector preProcess;

		if(object instanceof S4Object){
			S4Object model = (S4Object)object;

			preProcess = (RGenericVector)model.getAttributeValue("preProcess", true);
		} else

		if(object instanceof RGenericVector){
			RGenericVector model = (RGenericVector)object;

			preProcess = (RGenericVector)model.getValue("preProcess", true);
		} else

		{
			preProcess = null;
		}

		RExpEncoder encoder;

		if(preProcess != null){
			encoder = new PreProcessEncoder(preProcess);
		} else

		{
			encoder = new RExpEncoder();
		}

		return encodePMML(encoder);
	}

	public PMML encodePMML(RExpEncoder encoder){
		encodeFeatures(encoder);

		Schema schema;

		if(isSupervised()){
			schema = encoder.createSupervisedSchema();
		} else

		{
			schema = encoder.createUnsupervisedSchema();
		}

		Model model = encodeModel(schema);

		PMML pmml = encoder.encodePMML(model);

		return pmml;
	}
}