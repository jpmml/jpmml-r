/*
 * Copyright (c) 2024 Villu Ruusmann
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

import java.util.Objects;

public class RSymbol extends RExp {

	private String value = null;


	public RSymbol(String value){
		super(null);

		setValue(value);
	}

	@Override
	public int hashCode(){
		return Objects.hashCode(this.getValue());
	}

	@Override
	public boolean equals(Object object){

		if(object instanceof RSymbol){
			RSymbol that = (RSymbol)object;

			return Objects.equals(this.getValue(), that.getValue());
		}

		return false;
	}

	public String getValue(){
		return this.value;
	}

	private void setValue(String value){
		this.value = Objects.requireNonNull(value);
	}

	public static final RSymbol MISSING_ARG = new RSymbol("");
}