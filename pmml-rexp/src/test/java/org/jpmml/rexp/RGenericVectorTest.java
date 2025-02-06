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
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RGenericVectorTest {

	@Test
	public void addAttributes(){
		List<RExp> values = Arrays.asList(
			new RIntegerVector(Collections.emptyList(), null),
			new RDoubleVector(Collections.emptyList(), null),
			new RStringVector(Collections.emptyList(), null)
		);

		RGenericVector genericVector = new RGenericVector(values, null);

		assertFalse(genericVector.hasAttribute("class"));
		assertFalse(genericVector.hasAttribute("names"));

		genericVector.addAttribute("class", new RStringVector(Arrays.asList("data.frame"), null));

		assertTrue(genericVector.hasAttribute("class"));
		assertFalse(genericVector.hasAttribute("names"));

		genericVector.addAttribute("names", new RStringVector(Arrays.asList("A", "B", "C"), null));

		assertTrue(genericVector.hasAttribute("class"));
		assertTrue(genericVector.hasAttribute("names"));
	}
}