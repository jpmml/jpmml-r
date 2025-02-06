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
package org.jpmml.rexp.testing;

import org.jpmml.rexp.evaluator.RExpFunctionRegistry;
import org.jpmml.rexp.evaluator.RExpFunctions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class HurdleConverterTest extends RExpEncoderBatchTest {

	@BeforeAll
	static
	public void setup(){
		RExpFunctionRegistry.publish(RExpFunctions.STATS_PPOIS);
	}

	@Test
	public void evaluateNMES1988() throws Exception {
		evaluate("Hurdle", "NMES1988");
	}
}