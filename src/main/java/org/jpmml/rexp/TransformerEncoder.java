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

import org.jpmml.converter.Schema;

abstract
public class TransformerEncoder<R extends RExp> extends RExpEncoder {

	private R object = null;


	public TransformerEncoder(R object){
		setObject(object);
	}

	abstract
	public Schema transformSchema(Schema schema);

	@Override
	public Schema createSchema(){
		Schema schema = super.createSchema();

		return transformSchema(schema);
	}

	public R getObject(){
		return this.object;
	}

	private void setObject(R object){
		this.object = object;
	}
}