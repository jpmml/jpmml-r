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

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

abstract
public class BinaryInput implements RDataInput {

	private DataInputStream dis = null;


	public BinaryInput(InputStream is) throws IOException {
		DataInputStream dis = new DataInputStream(is);

		byte first = dis.readByte();
		if(first != 'X'){
			throw new IllegalArgumentException();
		}

		byte second = dis.readByte();
		if(second != '\n'){
			byte[] xdrMagic = new byte[5];
			xdrMagic[0] = first;
			xdrMagic[1] = second;

			dis.readFully(xdrMagic, 2, xdrMagic.length - 2);

			if(!Arrays.equals(XDR2_MAGIC, xdrMagic)){
				throw new IllegalArgumentException();
			}
		}

		this.dis = dis;
	}

	@Override
	public void close() throws IOException {
		this.dis.close();
	}

	@Override
	public int readInt() throws IOException {
		int value = this.dis.readInt();

		return value;
	}

	@Override
	public double readDouble() throws IOException {
		long value = this.dis.readLong();

		return Double.longBitsToDouble(value);
	}

	@Override
	public byte[] readByteArray(int length) throws IOException {
		byte[] buffer = new byte[length];

		this.dis.readFully(buffer);

		return buffer;
	}

	private static final byte[] XDR2_MAGIC = {'X', 'D', 'R', '2', '\n'};
}