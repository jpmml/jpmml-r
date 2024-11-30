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

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

abstract
public class TextInput implements RDataInput {

	private BufferedReader reader = null;


	public TextInput(InputStream is) throws IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(is));

		int first = reader.read();
		if(first != 'A'){
			throw new IllegalArgumentException();
		}

		int second = reader.read();
		if(second != '\n'){
			throw new IllegalArgumentException();
		}

		this.reader = reader;
	}

	@Override
	public void close() throws IOException {
		this.reader.close();
	}

	@Override
	public int readInt() throws IOException {
		String line = this.reader.readLine();

		if(line == null){
			throw new EOFException();
		} else

		if(("NA").equals(line)){
			return Integer.MIN_VALUE;
		}

		return Integer.parseInt(line);
	}

	@Override
	public double readDouble() throws IOException {
		String line = this.reader.readLine();

		if(line == null){
			throw new EOFException();
		}

		return Double.parseDouble(line);
	}

	@Override
	public byte[] readByteArray(int length) throws IOException {
		String line = this.reader.readLine();

		if(line == null){
			throw new EOFException();
		} // End if

		if(line.indexOf('\\') > -1){
			line = decode(line);
		}

		byte[] bytes = line.getBytes();
		if(bytes.length != length){
			throw new IOException();
		}

		return bytes;
	}

	static
	public String decode(String string){
		StringBuilder sb = new StringBuilder();

		int i = 0;

		while(i < string.length()){
			char currChar = string.charAt(i);

			if(currChar == '\\'){
				int nextChar = string.charAt(i + 1);

				if(nextChar >= '0' && nextChar <= '8'){
					int octalValue = (nextChar - '0');

					int j = 0;

					while((j < 2) && (i + 2 + j) < string.length()){
						nextChar = string.charAt(i + 2 + j);

						if(nextChar >= '0' && nextChar <= '8'){
							octalValue = (octalValue << 3) + (nextChar - '0');

							j++;
						} else

						{
							break;
						}
					}

					sb.append((char)octalValue);

					i += (2 + j);
				} else

				if(nextChar == '\\'){
					sb.append('\\');

					i += 2;
				} else

				{
					throw new IllegalArgumentException();
				}
			} else

			{
				sb.append(currChar);

				i++;
			}
		}

		return sb.toString();
	}
}