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

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

abstract
public class BinaryOutput implements RDataOutput {

	private DataOutputStream dos = null;


	public BinaryOutput(OutputStream os) throws IOException {
		DataOutputStream dos = new DataOutputStream(os);

		dos.writeByte('X');
		dos.writeByte('\n');

		this.dos = dos;
	}

	@Override
	public void close() throws IOException {
		this.dos.close();
	}

	@Override
	public String escape(String string){
		return string;
	}

	@Override
	public void writeInt(int value) throws IOException {
		this.dos.writeInt(value);
	}

	@Override
	public void writeDouble(double value) throws IOException {
		this.dos.writeLong(Double.doubleToLongBits(value));
	}

	@Override
	public void writeByteArray(byte[] bytes) throws IOException {
		this.dos.write(bytes);
	}
}