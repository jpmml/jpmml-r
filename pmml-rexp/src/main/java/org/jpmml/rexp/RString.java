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

import java.io.IOException;

public class RString extends RExp {

	private String value = null;


	public RString(String value){
		super(null);

		setValue(value);
	}

	@Override
	public void write(RDataOutput output) throws IOException {
		String value = getValue();

		output.writeInt(SExpTypes.CHARSXP);

		if(value == null){
			output.writeInt(-1);
		} else

		{
			value = output.escape(value);

			byte[] bytes = value.getBytes();

			output.writeInt(bytes.length);
			output.writeByteArray(bytes);
		}
	}

	public String getValue(){
		return this.value;
	}

	private void setValue(String value){
		this.value = value;
	}

	public static final RString NA = new RString("NA");
}