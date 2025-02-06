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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TextInputTest {

	@Test
	public void decode(){
		assertEquals("\0", TextInput.decode("\\0"));

		assertEquals("\\", TextInput.decode("\\\\"));
		assertEquals("\\", TextInput.decode("\\134"));

		assertEquals("Hello World!", TextInput.decode("Hello\\40World!"));
		assertEquals("Hello World!", TextInput.decode("Hello\\040World!"));
	}
}