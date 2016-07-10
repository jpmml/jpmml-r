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

	abstract
	public void encodeFeatures(R rexp, FeatureMapper featureMapper);

	abstract
	public Schema createSchema(FeatureMapper featureMapper);

	abstract
	public Model encodeModel(R rexp, Schema schema);

	@Override
	public PMML convert(R rexp){
		FeatureMapper featureMapper = new FeatureMapper();

		encodeFeatures(rexp, featureMapper);

		Schema schema = createSchema(featureMapper);

		Model model = encodeModel(rexp, schema);

		PMML pmml = featureMapper.encodePMML(model)
			.setHeader(PMMLUtil.createHeader("JPMML-R", "1.1-SNAPSHOT"));

		return pmml;
	}
}