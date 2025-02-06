/*
 * Copyright (c) 2017 Villu Ruusmann
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

import java.util.Arrays;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FormulaTest {

	@Test
	public void split(){
		assertEquals(Arrays.asList("", ""), Formula.split(":"));
		assertEquals(Arrays.asList("::"), Formula.split("::"));

		assertEquals(Arrays.asList("A"), Formula.split("A"));
		assertEquals(Arrays.asList("", "A"), Formula.split(":A"));
		assertEquals(Arrays.asList("A", ""), Formula.split("A:"));
		assertEquals(Arrays.asList("::A"), Formula.split("::A"));
		assertEquals(Arrays.asList("A::"), Formula.split("A::"));

		assertEquals(Arrays.asList("A::B::C"), Formula.split("A::B::C"));
		assertEquals(Arrays.asList("", "A::B::C", ""), Formula.split(":A::B::C:"));
		assertEquals(Arrays.asList("A::B", "C"), Formula.split("A::B:C"));
		assertEquals(Arrays.asList("A", "B::C"), Formula.split("A:B::C"));
		assertEquals(Arrays.asList("A", "B", "C"), Formula.split("A:B:C"));
		assertEquals(Arrays.asList("", "A", "B", "C", ""), Formula.split(":A:B:C:"));
	}
}