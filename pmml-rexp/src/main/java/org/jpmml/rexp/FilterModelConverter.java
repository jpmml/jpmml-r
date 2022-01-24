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

import org.dmg.pmml.Model;
import org.dmg.pmml.PMML;
import org.jpmml.converter.Schema;

abstract
public class FilterModelConverter<R extends RExp, M extends RExp> extends ModelConverter<R> {

	private ModelConverter<M> converter = null;


	public FilterModelConverter(R object){
		super(object);
	}

	abstract
	public ModelConverter<M> createConverter();

	@Override
	public void encodeSchema(RExpEncoder encoder){
		this.converter.encodeSchema(encoder);
	}

	@Override
	public Model encodeModel(Schema schema){
		return this.converter.encodeModel(schema);
	}

	@Override
	public PMML encodePMML(RExpEncoder encoder){
		this.converter = createConverter();

		return super.encodePMML(encoder);
	}
}