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

import java.util.Arrays;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class RStringVectorTest {

	@Test
	public void toFactorVector(){
		RStringVector stringVector = new RStringVector(Arrays.asList("c", "a", "c", "b", "a"), null);

		assertEquals(5, stringVector.size());

		RFactorVector factorVector = stringVector.toFactorVector();

		assertEquals(5, factorVector.size());

		assertEquals(Arrays.asList("a", "b", "c"), factorVector.getLevelValues());
		assertEquals(Arrays.asList(3, 1, 3, 2, 1), factorVector.getValues());

		factorVector = stringVector.toFactorVector(Arrays.asList("c", "b", "a"));

		assertEquals(5, factorVector.size());

		assertEquals(Arrays.asList("c", "b", "a"), factorVector.getLevelValues());
		assertEquals(Arrays.asList(1, 3, 1, 2, 3), factorVector.getValues());
	}
}