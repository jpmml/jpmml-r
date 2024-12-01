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

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

abstract
public class TextOutput implements RDataOutput {

	private Writer writer = null;


	public TextOutput(OutputStream os) throws IOException {
		Writer writer = new OutputStreamWriter(os);

		writer.write('A');
		writer.write('\n');

		this.writer = writer;
	}

	@Override
	public void close() throws IOException {
		this.writer.close();
	}

	@Override
	public String escape(String string){
		return encode(string);
	}

	@Override
	public void writeInt(int value) throws IOException {
		this.writer.write(String.valueOf(value));
		this.writer.write('\n');
	}

	@Override
	public void writeDouble(double value) throws IOException {
		this.writer.write(String.valueOf(value));
		this.writer.write('\n');
	}

	@Override
	public void writeByteArray(byte[] bytes) throws IOException {
		this.writer.write(new String(bytes));
		this.writer.write('\n');
	}

	static
	public String encode(String string){
		StringBuilder sb = new StringBuilder(2 * string.length());

		for(int i = 0; i < string.length(); i++){
			char c = string.charAt(i);

			if(c == '\\'){
				sb.append('\\').append('\\');
			} else

			if(c <= 31 || c == ' ' || c >= 127){
				sb.append('\\').append(Integer.toOctalString(c));
			} else

			{
				sb.append(c);
			}
		}

		return sb.toString();
	}
}