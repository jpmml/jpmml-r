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

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.Map;

public class RExpWriter implements Closeable {

	private RDataOutput output = null;

	private Map<Object, Integer> referenceTable = new LinkedHashMap<>();


	public RExpWriter(OutputStream os) throws IOException {
		this(os, false);
	}

	public RExpWriter(OutputStream os, boolean ascii) throws IOException {

		if(ascii){
			this.output = new TextOutput(os){

				@Override
				public RExpWriter getWriter(){
					return RExpWriter.this;
				}
			};
		} else

		{
			this.output = new BinaryOutput(os){

				@Override
				public RExpWriter getWriter(){
					return RExpWriter.this;
				}
			};
		}
	}

	@Override
	public void close() throws IOException {
		this.output.close();
	}

	public void write(RExp rexp) throws IOException {
		this.output.writeInt(2);

		// XXX
		this.output.writeInt(0);
		this.output.writeInt(0);

		rexp.write(this.output);
	}

	public Map<Object, Integer> getReferenceTable(){
		return this.referenceTable;
	}
}