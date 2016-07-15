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
import org.jpmml.converter.PMMLUtil;
import org.jpmml.converter.Schema;

abstract
public class ModelConverter<R extends RExp> extends Converter<R> {

	public ModelConverter(R object){
		super(object);
	}

	abstract
	public void encodeFeatures(FeatureMapper featureMapper);

	abstract
	public Model encodeModel(Schema schema);

	public boolean isSupervised(){
		return true;
	}

	@Override
	public PMML encodePMML(){
		RExp object = getObject();

		RGenericVector preProcess = null;

		try {
			if(object instanceof S4Object){
				S4Object model = (S4Object)object;

				preProcess = (RGenericVector)model.getAttributeValue("preProcess");
			} else

			if(object instanceof RGenericVector){
				RGenericVector model = (RGenericVector)object;

				preProcess = (RGenericVector)model.getValue("preProcess");
			}
		} catch(IllegalArgumentException iae){
			// Ignored
		}

		FeatureMapper featureMapper;

		if(preProcess != null){
			featureMapper = new PreProcessFeatureMapper(preProcess);
		} else

		{
			featureMapper = new FeatureMapper();
		}

		return encodePMML(featureMapper);
	}

	public PMML encodePMML(FeatureMapper featureMapper){
		encodeFeatures(featureMapper);

		Schema schema;

		if(isSupervised()){
			schema = featureMapper.createSupervisedSchema();
		} else

		{
			schema = featureMapper.createUnsupervisedSchema();
		}

		Model model = encodeModel(schema);

		PMML pmml = featureMapper.encodePMML(model)
			.setHeader(PMMLUtil.createHeader("JPMML-R", "1.1-SNAPSHOT"));

		return pmml;
	}
}